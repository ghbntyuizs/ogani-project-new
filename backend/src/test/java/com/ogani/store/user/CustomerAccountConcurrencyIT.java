package com.ogani.store.user;

import com.jayway.jsonpath.JsonPath;
import com.ogani.store.auth.dto.request.RegisterRequest;
import com.ogani.store.auth.dto.response.AuthResponse;
import com.ogani.store.auth.service.interfaces.AuthService;
import com.ogani.store.common.AbstractIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Transactional(propagation=Propagation.NOT_SUPPORTED)
class CustomerAccountConcurrencyIT extends AbstractIntegrationTest {
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired PasswordEncoder encoder;
    AuthResponse session;
    String email;
    Long id;
    @BeforeEach void fixture() {
        assertTrue(jdbc.queryForObject("select current_database()",String.class).endsWith("_test"));
        email="account-race-"+UUID.randomUUID()+"@example.test";
        session=auth.register(new RegisterRequest(email,"oldPassword123","Original","Buyer",null));id=session.user().id();
    }
    @AfterEach void cleanup() { if(id!=null) jdbc.update("delete from users where id=?",id); }
    @Test void concurrentPasswordChangesHaveOneWinner() throws Exception {
        var results=contend(this::change,this::change);
        assertEquals(List.of(200,401),results.stream().map(MockHttpServletResponse::getStatus).sorted().toList());
        assertEquals(1L,version());assertEquals(0,activeTokens());assertEquals(401,me(session.accessToken()));
        assertTrue(encoder.matches("newPassword456",jdbc.queryForObject("select password_hash from users where id=?",String.class,id)));
    }
    @Test void refreshCannotResurrectSessionDuringPasswordChange() throws Exception {
        var results=contend(this::change,() -> mvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh").contentType("application/json")
                .content("{\"refreshToken\":\""+session.refreshToken()+"\"}")).andReturn().getResponse());
        assertEquals(200,results.get(0).getStatus());assertTrue(Set.of(200,401).contains(results.get(1).getStatus()));
        assertEquals(0,activeTokens());assertEquals(1L,version());assertEquals(401,me(session.accessToken()));
        if(results.get(1).getStatus()==200) {
            String access=JsonPath.read(results.get(1).getContentAsString(),"$.data.accessToken");
            String refresh=JsonPath.read(results.get(1).getContentAsString(),"$.data.refreshToken");
            assertEquals(401,me(access));
            assertEquals(401,mvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh").contentType("application/json").content("{\"refreshToken\":\""+refresh+"\"}")).andReturn().getResponse().getStatus());
        }
    }
    @Test void loginWithOldPasswordCannotCreateSurvivingSession() throws Exception {
        var results=contend(this::change,() -> login("oldPassword123"));
        assertEquals(200,results.get(0).getStatus());assertTrue(Set.of(200,401).contains(results.get(1).getStatus()));assertEquals(0,activeTokens());
        if(results.get(1).getStatus()==200) assertEquals(401,me(JsonPath.read(results.get(1).getContentAsString(),"$.data.accessToken")));
        var fresh=login("newPassword456");assertEquals(200,fresh.getStatus());
        assertEquals(200,me(JsonPath.read(fresh.getContentAsString(),"$.data.accessToken")));
    }
    @Test void profileAndPasswordSerializeWithoutOverwritingCredentials() throws Exception {
        var results=contend(this::change,() -> patch("{\"firstName\":\"Changed\"}"));
        assertEquals(200,results.get(0).getStatus());assertTrue(Set.of(200,401).contains(results.get(1).getStatus()));
        assertEquals(results.get(1).getStatus()==200?"Changed":"Original",jdbc.queryForObject("select first_name from users where id=?",String.class,id));
        assertEquals(1L,version());assertTrue(encoder.matches("newPassword456",jdbc.queryForObject("select password_hash from users where id=?",String.class,id)));
    }
    @Test void concurrentPartialProfileChangesPreserveBothFields() throws Exception {
        var results=contend(() -> patch("{\"firstName\":\"Changed\"}"),() -> patch("{\"phone\":\"0901234567\"}"));
        assertEquals(List.of(200,200),results.stream().map(MockHttpServletResponse::getStatus).toList());
        assertEquals("Changed",jdbc.queryForObject("select first_name from users where id=?",String.class,id));
        assertEquals("0901234567",jdbc.queryForObject("select phone from users where id=?",String.class,id));assertEquals(0L,version());
    }
    @Test void lateRevocationFailureRollsBackPasswordVersionAndTokens() throws Exception {
        String function="account_fail_"+UUID.randomUUID().toString().replace("-","");
        var before=jdbc.queryForList("select token,revoked from refresh_tokens where user_id=? order by id",id);
        try {
            jdbc.execute("create function "+function+"() returns trigger language plpgsql as $$ begin if new.user_id="+id+" and new.revoked then raise exception 'Intentional account failure'; end if; return new; end $$");
            jdbc.execute("create trigger "+function+" before update on refresh_tokens for each row execute function "+function+"()");
            assertEquals(500,change().getStatus());assertEquals(0L,version());assertEquals(200,me(session.accessToken()));
            assertTrue(encoder.matches("oldPassword123",jdbc.queryForObject("select password_hash from users where id=?",String.class,id)));
            assertEquals(before,jdbc.queryForList("select token,revoked from refresh_tokens where user_id=? order by id",id));
        } finally { jdbc.execute("drop trigger if exists "+function+" on refresh_tokens");jdbc.execute("drop function if exists "+function+"()"); }
        assertEquals(200,change().getStatus());assertEquals(1L,version());assertEquals(0,activeTokens());assertEquals(401,me(session.accessToken()));
    }
    MockHttpServletResponse change() throws Exception {
        return mvc.perform(MockMvcRequestBuilders.put("/api/users/me/password").header("Authorization","Bearer "+session.accessToken())
                .contentType("application/json").content(CustomerAccountIT.PASSWORD_BODY)).andReturn().getResponse();
    }
    MockHttpServletResponse patch(String body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.patch("/api/users/me").header("Authorization","Bearer "+session.accessToken()).contentType("application/json").content(body)).andReturn().getResponse();
    }
    MockHttpServletResponse login(String password) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/auth/login").contentType("application/json").content("{\"email\":\""+email+"\",\"password\":\""+password+"\"}")).andReturn().getResponse();
    }
    int me(String token) throws Exception { return mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").header("Authorization","Bearer "+token)).andReturn().getResponse().getStatus(); }
    long version() { return jdbc.queryForObject("select token_version from users where id=?",Long.class,id); }
    int activeTokens() { return jdbc.queryForObject("select count(*) from refresh_tokens where user_id=? and not revoked",Integer.class,id); }
    List<MockHttpServletResponse> contend(Callable<MockHttpServletResponse> first,Callable<MockHttpServletResponse> second) throws Exception {
        var pool=Executors.newFixedThreadPool(2);var requests=new ArrayList<Future<MockHttpServletResponse>>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.queryForObject("select id from users where id=? for update",Long.class,id);
                requests.add(pool.submit(first));requests.add(pool.submit(second));
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                while(true) {
                    jdbc.execute("select pg_stat_clear_snapshot()");
                    if(jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like '%users%'",Integer.class)>=2) break;
                    if(System.nanoTime()>deadline) fail("Expected two session/profile requests to wait on user lock");
                    try { Thread.sleep(10); } catch(InterruptedException ex) { Thread.currentThread().interrupt();throw new AssertionError(ex); }
                }
            });
            return List.of(requests.get(0).get(15,TimeUnit.SECONDS),requests.get(1).get(15,TimeUnit.SECONDS));
        } finally { pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS)); }
    }
}
