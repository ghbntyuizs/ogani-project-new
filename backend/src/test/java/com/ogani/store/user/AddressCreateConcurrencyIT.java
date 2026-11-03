package com.ogani.store.user;

import com.ogani.store.common.AbstractIntegrationTest;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@Transactional(propagation=Propagation.NOT_SUPPORTED)
class AddressCreateConcurrencyIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactions;
    Long buyer;
    UserPrincipal principal;
    @BeforeEach void fixture() {
        assertTrue(jdbc.queryForObject("select current_database()",String.class).endsWith("_test"));
        buyer=jdbc.queryForObject("insert into users(role_id,email,password_hash,first_name,last_name) select 3,?,password_hash,'Address','Buyer' from users where email=? returning id",Long.class,"address-"+UUID.randomUUID()+"@example.test",CUSTOMER);
        principal=UserPrincipal.from(users.findById(buyer).orElseThrow());
    }
    @AfterEach void cleanup() { if(buyer!=null) jdbc.update("delete from users where id=?",buyer); }
    @Test void simultaneousFirstAddressesProduceExactlyOneDefault() throws Exception {
        assertEquals(List.of(201,201),contend(false));assertEquals(2,count());assertEquals(1,defaults());
    }
    @Test void simultaneousTenthAddressesCannotExceedLimit() throws Exception {
        for(int i=0;i<9;i++) assertEquals(201,create(false));
        assertEquals(List.of(201,409),contend(true).stream().sorted().toList());assertEquals(10,count());assertEquals(1,defaults());
    }
    @Test void simultaneousDefaultReplacementsNeverLeaveMultipleDefaults() throws Exception {
        assertEquals(201,create(false));
        assertEquals(List.of(201,201),contend(true));assertEquals(3,count());assertEquals(1,defaults());
    }
    @Test void failedInsertRestoresOldDefaultAndAllowsRetry() throws Exception {
        assertEquals(201,create(false));
        long original=jdbc.queryForObject("select id from customer_addresses where user_id=? and is_default",Long.class,buyer);
        String function="address_fail_"+UUID.randomUUID().toString().replace("-","");
        try {
            jdbc.execute("create function "+function+"() returns trigger language plpgsql as $$ begin if new.user_id="+buyer+" then raise exception 'Intentional address failure'; end if; return new; end $$");
            jdbc.execute("create trigger "+function+" before insert on customer_addresses for each row execute function "+function+"()");
            assertEquals(500,create(true));assertEquals(1,count());assertEquals(1,defaults());
            assertEquals(original,jdbc.queryForObject("select id from customer_addresses where user_id=? and is_default",Long.class,buyer));
        } finally {
            jdbc.execute("drop trigger if exists "+function+" on customer_addresses");jdbc.execute("drop function if exists "+function+"()");
        }
        assertEquals(201,create(true));assertEquals(2,count());assertEquals(1,defaults());
    }
    int create(boolean makeDefault) throws Exception {
        String body=AddressCreateIT.BODY.replace("\"city\"","\"defaultAddress\":"+makeDefault+",\"city\"");
        return mvc.perform(MockMvcRequestBuilders.post("/api/addresses").with(user(principal)).contentType("application/json").content(body)).andReturn().getResponse().getStatus();
    }
    @Test void simultaneousUpdateDefaultsKeepOnlyOneAndDoNotCreateAddresses() throws Exception {
        assertEquals(201,create(false));assertEquals(201,create(false));
        var ids=jdbc.queryForList("select id from customer_addresses where user_id=? order by id",Long.class,buyer);
        assertEquals(List.of(200,200),contend(() -> update(ids.get(0),"A"),() -> update(ids.get(1),"B")));
        assertEquals(2,count());assertEquals(1,defaults());
    }
    @Test void createAndUpdateDefaultAreSerialized() throws Exception {
        assertEquals(201,create(false));
        long id=jdbc.queryForObject("select id from customer_addresses where user_id=?",Long.class,buyer);
        assertEquals(List.of(201,200),contend(() -> create(true),() -> update(id,"Edited")));
        assertEquals(2,count());assertEquals(1,defaults());
    }
    @Test void concurrentFullUpdatesNeverMixFieldsFromDifferentRequests() throws Exception {
        assertEquals(201,create(false));
        long id=jdbc.queryForObject("select id from customer_addresses where user_id=?",Long.class,buyer);
        assertEquals(List.of(200,200),contend(() -> update(id,"A"),() -> update(id,"B")));
        var address=jdbc.queryForMap("select city,address_line from customer_addresses where id=?",id);
        assertTrue(Set.of("A","B").contains(address.get("city")));
        assertEquals(address.get("city"),address.get("address_line"));assertEquals(1,count());assertEquals(1,defaults());
    }
    @Test void failedUpdateRollsBackBothFieldsAndDefaultSwitch() throws Exception {
        assertEquals(201,create(false));assertEquals(201,create(false));
        var ids=jdbc.queryForList("select id from customer_addresses where user_id=? order by id",Long.class,buyer);
        long target=ids.get(1);
        var before=jdbc.queryForList("select * from customer_addresses where user_id=? order by id",buyer);
        String function="address_update_fail_"+UUID.randomUUID().toString().replace("-","");
        try {
            jdbc.execute("create function "+function+"() returns trigger language plpgsql as $$ begin if new.id="+target+" then raise exception 'Intentional address update failure'; end if; return new; end $$");
            jdbc.execute("create trigger "+function+" before update on customer_addresses for each row execute function "+function+"()");
            assertEquals(500,update(target,"Changed"));
            assertEquals(before,jdbc.queryForList("select * from customer_addresses where user_id=? order by id",buyer));
        } finally {
            jdbc.execute("drop trigger if exists "+function+" on customer_addresses");jdbc.execute("drop function if exists "+function+"()");
        }
        assertEquals(200,update(target,"Changed"));assertEquals(1,defaults());
    }
    int update(long id,String marker) throws Exception {
        String body=AddressCreateIT.BODY.replace("\"city\":\"HCM\"","\"defaultAddress\":true,\"city\":\""+marker+"\"")
                .replace("12 Nguyễn Huệ",marker);
        return mvc.perform(MockMvcRequestBuilders.put("/api/addresses/{id}",id).with(user(principal)).contentType("application/json").content(body)).andReturn().getResponse().getStatus();
    }
    @Test void concurrentDefaultSelectionsKeepExactlyOneDefault() throws Exception {
        var ids=twoAddresses();
        assertEquals(List.of(200,200),contend(() -> selectDefault(ids.get(0)),() -> selectDefault(ids.get(1))));
        assertEquals(2,count());assertEquals(1,defaults());
        assertEquals(List.of(200,200),contend(() -> selectDefault(ids.get(0)),() -> selectDefault(ids.get(0))));
        assertEquals(ids.get(0),jdbc.queryForObject("select id from customer_addresses where user_id=? and is_default",Long.class,buyer));
    }
    @Test void deletingDefaultAndSelectingAnotherSerializeSafely() throws Exception {
        var ids=twoAddresses();
        assertEquals(List.of(200,200),contend(() -> remove(ids.get(0)),() -> selectDefault(ids.get(1))));
        assertEquals(1,count());assertEquals(1,defaults());
        assertEquals(ids.get(1),jdbc.queryForObject("select id from customer_addresses where user_id=? and is_default",Long.class,buyer));
    }
    @Test void deletingAddressWhileSelectingItCannotResurrectIt() throws Exception {
        var ids=twoAddresses();
        var result=contend(() -> remove(ids.get(1)),() -> selectDefault(ids.get(1)));
        assertEquals(200,result.get(0));assertTrue(Set.of(200,404).contains(result.get(1)));
        assertEquals(1,count());assertEquals(1,defaults());
        assertEquals(0,jdbc.queryForObject("select count(*) from customer_addresses where id=?",Integer.class,ids.get(1)));
    }
    @Test void concurrentDeletingLastAddressesLeavesEmptyAndNextCreateDefaults() throws Exception {
        var ids=twoAddresses();
        assertEquals(List.of(200,200),contend(() -> remove(ids.get(0)),() -> remove(ids.get(1))));
        assertEquals(0,count());assertEquals(0,defaults());
        assertEquals(201,create(false));assertEquals(1,count());assertEquals(1,defaults());
    }
    @Test void concurrentRepeatedDeleteHasOneSuccessAndOneNotFound() throws Exception {
        var ids=twoAddresses();
        assertEquals(List.of(200,404),contend(() -> remove(ids.get(0)),() -> remove(ids.get(0))).stream().sorted().toList());
        assertEquals(1,count());assertEquals(1,defaults());
    }
    @Test void deleteAndCreateAtCapCannotBreakLimitOrDefault() throws Exception {
        for(int i=0;i<10;i++) assertEquals(201,create(false));
        long id=jdbc.queryForObject("select id from customer_addresses where user_id=? and is_default",Long.class,buyer);
        var result=contend(() -> remove(id),() -> create(true));
        assertEquals(200,result.get(0));assertTrue(Set.of(201,409).contains(result.get(1)));
        assertEquals(result.get(1)==201?10:9,count());assertEquals(1,defaults());
    }
    @Test void failedDefaultSelectionRollsBackPreviousDefault() throws Exception { failsAndRetries(false); }
    @Test void failedReplacementAfterDeleteRestoresDeletedAddressAndDefault() throws Exception { failsAndRetries(true); }
    void failsAndRetries(boolean remove) throws Exception {
        var ids=twoAddresses();
        var before=jdbc.queryForList("select * from customer_addresses where user_id=? order by id",buyer);
        String function="address_fail_"+UUID.randomUUID().toString().replace("-","");
        try {
            jdbc.execute("create function "+function+"() returns trigger language plpgsql as $$ begin if new.id="+ids.get(1)+" and new.is_default then raise exception 'Intentional default write failure'; end if; return new; end $$");
            jdbc.execute("create trigger "+function+" before update on customer_addresses for each row execute function "+function+"()");
            assertEquals(500,remove?remove(ids.get(0)):selectDefault(ids.get(1)));
            assertEquals(before,jdbc.queryForList("select * from customer_addresses where user_id=? order by id",buyer));
        } finally {
            jdbc.execute("drop trigger if exists "+function+" on customer_addresses");jdbc.execute("drop function if exists "+function+"()");
        }
        assertEquals(200,remove?remove(ids.get(0)):selectDefault(ids.get(1)));assertEquals(1,defaults());
    }
    List<Long> twoAddresses() throws Exception {
        assertEquals(201,create(false));assertEquals(201,create(false));
        return jdbc.queryForList("select id from customer_addresses where user_id=? order by id",Long.class,buyer);
    }
    int selectDefault(long id) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.patch("/api/addresses/{id}/default",id).with(user(principal))).andReturn().getResponse().getStatus();
    }
    int remove(long id) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.delete("/api/addresses/{id}",id).with(user(principal))).andReturn().getResponse().getStatus();
    }
    int count() { return jdbc.queryForObject("select count(*) from customer_addresses where user_id=?",Integer.class,buyer); }
    int defaults() { return jdbc.queryForObject("select count(*) from customer_addresses where user_id=? and is_default",Integer.class,buyer); }
    List<Integer> contend(boolean makeDefault) throws Exception {
        return contend(() -> create(makeDefault),() -> create(makeDefault));
    }
    List<Integer> contend(Callable<Integer> first,Callable<Integer> second) throws Exception {
        var pool=Executors.newFixedThreadPool(2);var requests=new ArrayList<Future<Integer>>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.queryForObject("select id from users where id=? for update",Long.class,buyer);
                requests.add(pool.submit(first));requests.add(pool.submit(second));
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
                while(true) {
                    jdbc.execute("select pg_stat_clear_snapshot()");
                    if(jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like '%users%'",Integer.class)>=2) break;
                    if(System.nanoTime()>deadline) fail("Expected both address requests to wait on buyer lock");
                    try { Thread.sleep(10); } catch(InterruptedException ex) { Thread.currentThread().interrupt();throw new AssertionError(ex); }
                }
            });
            return List.of(requests.get(0).get(15,TimeUnit.SECONDS),requests.get(1).get(15,TimeUnit.SECONDS));
        } finally { pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS)); }
    }
}
