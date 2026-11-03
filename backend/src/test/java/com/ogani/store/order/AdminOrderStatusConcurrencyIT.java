package com.ogani.store.order;

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
class AdminOrderStatusConcurrencyIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactions;
    private final List<Long> buyers=new ArrayList<>(),products=new ArrayList<>(),orders=new ArrayList<>();
    private Integer category;
    private UserPrincipal admin,staff,customer;

    @BeforeEach void fixture() {
        assertTrue(jdbc.queryForObject("select current_database()",String.class).endsWith("_test"));
        String suffix=UUID.randomUUID().toString();
        for(int n=0;n<2;n++) buyers.add(jdbc.queryForObject("insert into users(role_id,email,password_hash,first_name,last_name) select 3,?,password_hash,'Status','Buyer' from users where email=? returning id",
                Long.class,"status-"+n+suffix+"@example.test",CUSTOMER));
        admin=UserPrincipal.from(users.findByEmail(ADMIN).orElseThrow());staff=UserPrincipal.from(users.findByEmail(STAFF).orElseThrow());
        customer=UserPrincipal.from(users.findById(buyers.get(0)).orElseThrow());
        category=jdbc.queryForObject("insert into categories(name,slug) values('Status fixture',?) returning id",Integer.class,suffix);
        for(int n=0;n<2;n++) products.add(jdbc.queryForObject("insert into products(category_id,name,slug,sku,price,stock_quantity,sold_count,status) values(?,'Snapshot product',?,?,20,0,7,'OUT_OF_STOCK') returning id",
                Long.class,category,n+suffix,n+suffix));
        for(Long buyer:buyers) orders.add(jdbc.queryForObject("insert into orders(order_code,user_id,first_name,last_name,email,phone,ship_country,ship_address,ship_city,ship_recipient_name,subtotal,total) "
                +"values(?,?,'Status','Buyer','buyer@example.test','0901234567','Vietnam','12 Test','HCM','Status Buyer',40,40) returning id",
                Long.class,"AST-"+UUID.randomUUID().toString().replace("-","").substring(0,16),buyer));
        line(orders.get(0),products.get(0));line(orders.get(0),products.get(1));
        line(orders.get(1),products.get(1));line(orders.get(1),products.get(0));
    }

    @AfterEach void cleanup() {
        for(Long order:orders) jdbc.update("delete from orders where id=?",order);
        for(Long buyer:buyers) jdbc.update("delete from users where id=?",buyer);
        for(Long product:products) jdbc.update("delete from products where id=?",product);
        if(category!=null) jdbc.update("delete from categories where id=?",category);
    }

    @Test void identicalStaffCommandsAppendHistoryOnce() throws Exception {
        assertEquals(List.of(200,200),contend("users",buyers.get(0),() -> patch(admin,orders.get(0),"CONFIRMED",0),() -> patch(staff,orders.get(0),"CONFIRMED",0)));
        assertEquals(1,history(orders.get(0)));assertEquals(1L,version(orders.get(0)));assertEquals(0,stock());
    }

    @Test void differentStaffTargetsCannotBothApplyFromSameVersion() throws Exception {
        var result=contend("users",buyers.get(0),() -> patch(admin,orders.get(0),"CONFIRMED",0),() -> patch(staff,orders.get(0),"CANCELLED",0));
        assertEquals(List.of(200,409),result.stream().sorted().toList());assertEquals(1,history(orders.get(0)));
        assertEquals(result.get(1)==200?2:0,stock());
    }

    @Test void adminAndCustomerCancellationRestockOnlyOnce() throws Exception {
        assertEquals(List.of(200,200),contend("users",buyers.get(0),() -> patch(admin,orders.get(0),"CANCELLED",0),() -> cancelCustomer()));
        assertEquals(2,stock());assertEquals(1,history(orders.get(0)));assertEquals(1L,version(orders.get(0)));
    }

    @Test void adminConfirmationAndCustomerCancellationHaveOneWinner() throws Exception {
        var result=contend("users",buyers.get(0),() -> patch(admin,orders.get(0),"CONFIRMED",0),() -> cancelCustomer());
        assertEquals(List.of(200,409),result.stream().sorted().toList());assertEquals(1,history(orders.get(0)));
        assertEquals(result.get(1)==200?2:0,stock());
    }

    @Test void concurrentCompletionCountsEachUnitOnlyOnce() throws Exception {
        shipping(orders.get(0));
        assertEquals(List.of(200,200),contend("users",buyers.get(0),() -> patch(admin,orders.get(0),"COMPLETED",0),() -> patch(staff,orders.get(0),"COMPLETED",0)));
        assertEquals(9,sold());assertEquals(0,stock());assertEquals(1,history(orders.get(0)));
        assertEquals(1L,jdbc.queryForObject("select version from products where id=?",Long.class,products.get(0)));
    }

    @Test void inverseProductInsertionOrdersDoNotDeadlockOrLoseSales() throws Exception {
        shipping(orders.get(0));shipping(orders.get(1));
        assertEquals(List.of(200,200),contend("products",products.get(0),() -> patch(admin,orders.get(0),"COMPLETED",0),() -> patch(staff,orders.get(1),"COMPLETED",0)));
        assertEquals(11,sold());assertEquals(11,jdbc.queryForObject("select sold_count from products where id=?",Integer.class,products.get(1)));
    }

    @Test void staffProcessingEachOthersOrdersDoNotDeadlockOnActorReferences() throws Exception {
        jdbc.update("update orders set user_id=? where id=?",staff.getId(),orders.get(0));
        jdbc.update("update orders set user_id=? where id=?",admin.getId(),orders.get(1));
        shipping(orders.get(0));shipping(orders.get(1));
        assertEquals(List.of(200,200),contend("products",products.get(0),() -> patch(admin,orders.get(0),"COMPLETED",0),() -> patch(staff,orders.get(1),"COMPLETED",0)));
        assertEquals(11,sold());assertEquals(1,history(orders.get(0)));assertEquals(1,history(orders.get(1)));
    }

    @Test void cancellationAndCompletionOnSharedProductsKeepBothCountersCorrect() throws Exception {
        shipping(orders.get(1));
        assertEquals(List.of(200,200),contend("products",products.get(0),() -> patch(admin,orders.get(0),"CANCELLED",0),() -> patch(staff,orders.get(1),"COMPLETED",0)));
        assertEquals(2,stock());assertEquals(9,sold());
    }

    @Test void changedOwnershipWhileWaitingReturnsConflictInsteadOfTakingLocksOutOfOrder() throws Exception {
        var pool=Executors.newSingleThreadExecutor();var requests=new ArrayList<Future<Integer>>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.queryForObject("select id from users where id=? for update",Long.class,buyers.get(0));
                requests.add(pool.submit(() -> patch(admin,orders.get(0),"CONFIRMED",0)));awaitBlocked("users",1);
                jdbc.update("update orders set user_id=?,version=version+1 where id=?",buyers.get(1),orders.get(0));
            });
            assertEquals(409,requests.get(0).get(15,TimeUnit.SECONDS));assertEquals(0,history(orders.get(0)));assertEquals(0,stock());
        } finally { pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS)); }
    }

    @Test void lateHistoryOrOrderFailureRollsBackCompletionAndAllowsRetry() throws Exception {
        shipping(orders.get(0));String function="status_fail_"+UUID.randomUUID().toString().replace("-","");
        try {
            jdbc.execute("create function "+function+"() returns trigger language plpgsql as $$ begin if new.id="+orders.get(0)+" and new.order_status='COMPLETED' then raise exception 'Intentional status test failure'; end if; return new; end $$");
            jdbc.execute("create trigger "+function+" before update on orders for each row execute function "+function+"()");
            assertEquals(500,patch(admin,orders.get(0),"COMPLETED",0));
            assertEquals(7,sold());assertEquals(0,history(orders.get(0)));assertEquals(0L,version(orders.get(0)));
            assertEquals("SHIPPING",jdbc.queryForObject("select order_status from orders where id=?",String.class,orders.get(0)));
        } finally { jdbc.execute("drop trigger if exists "+function+" on orders");jdbc.execute("drop function if exists "+function+"()"); }
        assertEquals(200,patch(admin,orders.get(0),"COMPLETED",0));assertEquals(9,sold());assertEquals(1,history(orders.get(0)));
    }

    private int patch(UserPrincipal principal,long order,String state,long version) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.patch("/api/admin/orders/"+order+"/status").with(user(principal)).contentType("application/json")
                .content("{\"status\":\""+state+"\",\"version\":"+version+",\"note\":\"Concurrent staff action\"}")).andReturn().getResponse().getStatus();
    }
    private int cancelCustomer() throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/orders/"+orders.get(0)+"/cancel").with(user(customer)).contentType("application/json").content("{}"))
                .andReturn().getResponse().getStatus();
    }
    private void line(long order,long product) { jdbc.update("insert into order_items(order_id,product_id,product_name,unit_price,quantity,line_total) values(?,?,'Purchased snapshot',20,2,40)",order,product); }
    private void shipping(long order) { jdbc.update("update orders set order_status='SHIPPING',payment_status='PAID' where id=?",order); }
    private int stock() { return jdbc.queryForObject("select stock_quantity from products where id=?",Integer.class,products.get(0)); }
    private int sold() { return jdbc.queryForObject("select sold_count from products where id=?",Integer.class,products.get(0)); }
    private int history(long order) { return jdbc.queryForObject("select count(*) from order_status_history where order_id=?",Integer.class,order); }
    private long version(long order) { return jdbc.queryForObject("select version from orders where id=?",Long.class,order); }
    private List<Integer> contend(String table,long id,Callable<Integer> first,Callable<Integer> second) throws Exception {
        var pool=Executors.newFixedThreadPool(2);var requests=new ArrayList<Future<Integer>>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.queryForObject("select id from "+table+" where id=? for update",Long.class,id);
                requests.add(pool.submit(first));requests.add(pool.submit(second));awaitBlocked(table,2);
            });
            return List.of(requests.get(0).get(15,TimeUnit.SECONDS),requests.get(1).get(15,TimeUnit.SECONDS));
        } finally { pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS)); }
    }
    private void awaitBlocked(String table,int expected) {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        do {
            jdbc.execute("select pg_stat_clear_snapshot()");
            if(jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like ?",Integer.class,"%"+table+"%")>=expected) return;
            try { Thread.sleep(10); } catch(InterruptedException ex) { Thread.currentThread().interrupt();throw new AssertionError(ex); }
        } while(System.nanoTime()<deadline);
        fail("Expected blocked requests on "+table);
    }
}
