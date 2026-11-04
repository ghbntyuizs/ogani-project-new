package com.ogani.store.auth;

import com.jayway.jsonpath.JsonPath;
import com.ogani.store.auth.dto.request.RegisterRequest;
import com.ogani.store.auth.dto.response.AuthResponse;
import com.ogani.store.auth.service.interfaces.AuthService;
import com.ogani.store.auth.reset.ResetTokenCodec;
import com.ogani.store.common.AbstractIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Transactional(propagation=Propagation.NOT_SUPPORTED)
@TestPropertySource(properties={"app.password-reset.ip-limit=5","app.password-reset.reset-ip-limit=5","management.health.mail.enabled=false"})
class PasswordResetConcurrencyIT extends AbstractIntegrationTest {
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean JavaMailSender sender;
    @MockitoBean Clock clock;
    static final AtomicInteger IPS=new AtomicInteger(100);
    final BlockingQueue<SimpleMailMessage> mails=new LinkedBlockingQueue<>();
    final Set<String> rateKeys=ConcurrentHashMap.newKeySet();
    AuthResponse session;
    String email,ip;
    Long id;
    @BeforeEach void fixture() {
        assertTrue(jdbc.queryForObject("select current_database()",String.class).endsWith("_test"));
        when(clock.instant()).thenReturn(PasswordResetIT.NOW);when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        doAnswer(call -> {mails.add(new SimpleMailMessage(call.getArgument(0,SimpleMailMessage.class)));return null;}).when(sender).send(any(SimpleMailMessage.class));
        email="reset-race-"+UUID.randomUUID()+"@example.test";ip="198.51.100."+IPS.incrementAndGet();
        session=auth.register(new RegisterRequest(email,"oldPassword123","Recovery","Buyer",null));id=session.user().id();
        rateKeys.add("forgot-ip:"+ip);rateKeys.add("reset-ip:"+ip);
    }
    @AfterEach void cleanup() {
        if(id!=null) jdbc.update("delete from users where id=?",id);
        for(String key:rateKeys) jdbc.update("delete from password_reset_rate_limits where bucket_key=?",ResetTokenCodec.hash(key));
    }
    MockHttpServletResponse forgot(String address) throws Exception {
        rateKeys.add("forgot-email:"+address);
        return mvc.perform(MockMvcRequestBuilders.post("/api/auth/forgot-password").with(req -> {req.setRemoteAddr(ip);return req;})
                .contentType("application/json").content("{\"email\":\""+address+"\"}")).andReturn().getResponse();
    }
    MockHttpServletResponse reset(String token,String password) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/auth/reset-password").with(req -> {req.setRemoteAddr(ip);return req;})
                .contentType("application/json").content("{\"token\":\""+token+"\",\"newPassword\":\""+password+"\",\"confirmPassword\":\""+password+"\"}")).andReturn().getResponse();
    }
    String token() throws Exception {assertEquals(200,forgot(email).getStatus());return receivedToken();}
    String receivedToken() throws Exception {
        var message=mails.poll(5,TimeUnit.SECONDS);assertNotNull(message);
        var match=Pattern.compile("#token=([A-Za-z0-9_-]{43})").matcher(message.getText());assertTrue(match.find());return match.group(1);
    }
    @Test void sameTokenConcurrentResetHasOneWinner() throws Exception {
        String token=token();
        var results=contend(() -> reset(token,"resetPassword456"),() -> reset(token,"anotherPassword789"));
        assertEquals(List.of(200,400),results.stream().map(MockHttpServletResponse::getStatus).sorted().toList());
        assertEquals(1L,version());assertEquals(0,activeTokens());
        assertTrue(encoder.matches(results.get(0).getStatus()==200?"resetPassword456":"anotherPassword789",storedHash()));
    }
    @Test void resetAndPasswordChangeCannotBothUseOldCredentials() throws Exception {
        String token=token();var result=contend(() -> reset(token,"resetPassword456"),this::change);
        if(result.get(0).getStatus()==200) {assertEquals(401,result.get(1).getStatus());assertTrue(encoder.matches("resetPassword456",storedHash()));}
        else {assertEquals(400,result.get(0).getStatus());assertEquals(200,result.get(1).getStatus());assertTrue(encoder.matches("changedPassword789",storedHash()));}
        assertEquals(1L,version());assertEquals(0,activeTokens());
    }
    @Test void resetAndRefreshCannotLeaveSurvivingOldSession() throws Exception {
        String token=token();var result=contend(() -> reset(token,"resetPassword456"),() -> mvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh")
                .contentType("application/json").content("{\"refreshToken\":\""+session.refreshToken()+"\"}")).andReturn().getResponse());
        assertEquals(200,result.get(0).getStatus());assertTrue(Set.of(200,401).contains(result.get(1).getStatus()));assertEquals(0,activeTokens());
        if(result.get(1).getStatus()==200) assertEquals(401,me(JsonPath.read(result.get(1).getContentAsString(),"$.data.accessToken")));
    }
    @Test void resetAndOldPasswordLoginCannotLeaveSurvivingOldSession() throws Exception {
        String token=token();var result=contend(() -> reset(token,"resetPassword456"),() -> mvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                .contentType("application/json").content("{\"email\":\""+email+"\",\"password\":\"oldPassword123\"}")).andReturn().getResponse());
        assertEquals(200,result.get(0).getStatus());assertTrue(Set.of(200,401).contains(result.get(1).getStatus()));assertEquals(0,activeTokens());
        if(result.get(1).getStatus()==200) assertEquals(401,me(JsonPath.read(result.get(1).getContentAsString(),"$.data.accessToken")));
    }
    @Test void newForgotAndOldResetSerializeWithoutDeadlock() throws Exception {
        String old=token();when(clock.instant()).thenReturn(PasswordResetIT.NOW.plusSeconds(61));
        var result=contend(() -> reset(old,"resetPassword456"),() -> forgot(email));
        assertEquals(200,result.get(1).getStatus());assertTrue(Set.of(200,400).contains(result.get(0).getStatus()));
        String fresh=receivedToken();assertEquals(400,reset(old,"anotherPassword789").getStatus());
        assertEquals(200,reset(fresh,"anotherPassword789").getStatus());
    }
    @Test void expiryIsRecheckedAfterWaitingForUserLock() throws Exception {
        String token=token();var pool=Executors.newSingleThreadExecutor();var requests=new ArrayList<Future<MockHttpServletResponse>>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.queryForObject("select id from users where id=? for update",Long.class,id);
                requests.add(pool.submit(() -> reset(token,"resetPassword456")));awaitBlocked(1);
                when(clock.instant()).thenReturn(PasswordResetIT.NOW.plusSeconds(900));
            });
            assertEquals(400,requests.get(0).get(15,TimeUnit.SECONDS).getStatus());assertEquals(0L,version());
        } finally {pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
    }
    @Test void failedRevocationRollsBackPasswordTokenUseAndVersion() throws Exception {
        String token=token(),function="reset_fail_"+UUID.randomUUID().toString().replace("-","");
        try {
            jdbc.execute("create function "+function+"() returns trigger language plpgsql as $$ begin if new.user_id="+id+" and new.revoked then raise exception 'Intentional reset failure'; end if; return new; end $$");
            jdbc.execute("create trigger "+function+" before update on refresh_tokens for each row execute function "+function+"()");
            assertEquals(500,reset(token,"resetPassword456").getStatus());assertEquals(0L,version());assertEquals(1,activeTokens());
            assertTrue(encoder.matches("oldPassword123",storedHash()));
            assertNull(jdbc.queryForObject("select used_at from password_reset_tokens where token_hash=?",Object.class,ResetTokenCodec.hash(token)));
            assertEquals(200,me(session.accessToken()));
            assertEquals(1,jdbc.queryForObject("select request_count from password_reset_rate_limits where bucket_key=?",Integer.class,ResetTokenCodec.hash("reset-ip:"+ip)));
        } finally {jdbc.execute("drop trigger if exists "+function+" on refresh_tokens");jdbc.execute("drop function if exists "+function+"()");}
        assertEquals(200,reset(token,"resetPassword456").getStatus());assertEquals(1L,version());assertEquals(0,activeTokens());
    }
    @Test void competingForgotRequestsDoNotExceedEmailQuota() throws Exception {
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<Integer> request=() -> {start.await();return forgot(email).getStatus();};
            var first=pool.submit(request);var second=pool.submit(request);start.countDown();
            assertEquals(200,first.get(10,TimeUnit.SECONDS));assertEquals(200,second.get(10,TimeUnit.SECONDS));receivedToken();
            verify(sender,after(100).times(1)).send(any(SimpleMailMessage.class));
            assertEquals(1,jdbc.queryForObject("select count(*) from password_reset_tokens where user_id=?",Integer.class,id));
        } finally {pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
    }
    @Test void delayedOlderAcceptedRequestCannotSupersedeNewerRequest() throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            jdbc.queryForObject("select id from users where id=? for update",Long.class,id);
            try {
                assertEquals(200,forgot(email).getStatus());
                when(clock.instant()).thenReturn(PasswordResetIT.NOW.plusSeconds(61));
                assertEquals(200,forgot(email).getStatus());awaitBlocked(2);
            } catch(Exception ex) {throw new AssertionError(ex);}
        });
        String newest=receivedToken();verify(sender,after(500).times(1)).send(any(SimpleMailMessage.class));
        assertEquals(1,jdbc.queryForObject("select count(*) from password_reset_tokens where user_id=?",Integer.class,id));
        assertEquals(200,reset(newest,"resetPassword456").getStatus());
    }
    @Test void concurrentUnknownEmailsRespectIpCapWithoutConnectionPoolDeadlock() throws Exception {
        var pool=Executors.newFixedThreadPool(8);var start=new CountDownLatch(1);var requests=new ArrayList<Future<Integer>>();
        try {
            for(int n=0;n<8;n++) {String address="missing-"+n+email;requests.add(pool.submit(() -> {start.await();return forgot(address).getStatus();}));}
            start.countDown();for(var request:requests) assertEquals(200,request.get(15,TimeUnit.SECONDS));
            assertEquals(5,jdbc.queryForObject("select request_count from password_reset_rate_limits where bucket_key=?",Integer.class,ResetTokenCodec.hash("forgot-ip:"+ip)));
            verify(sender,after(100).never()).send(any(SimpleMailMessage.class));
        } finally {pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
    }
    MockHttpServletResponse change() throws Exception {
        return mvc.perform(MockMvcRequestBuilders.put("/api/users/me/password").header("Authorization","Bearer "+session.accessToken()).contentType("application/json")
                .content("{\"currentPassword\":\"oldPassword123\",\"newPassword\":\"changedPassword789\",\"confirmPassword\":\"changedPassword789\"}")).andReturn().getResponse();
    }
    long version() {return jdbc.queryForObject("select token_version from users where id=?",Long.class,id);}
    int activeTokens() {return jdbc.queryForObject("select count(*) from refresh_tokens where user_id=? and not revoked",Integer.class,id);}
    String storedHash() {return jdbc.queryForObject("select password_hash from users where id=?",String.class,id);}
    int me(String access) throws Exception {return mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").header("Authorization","Bearer "+access)).andReturn().getResponse().getStatus();}
    List<MockHttpServletResponse> contend(Callable<MockHttpServletResponse> first,Callable<MockHttpServletResponse> second) throws Exception {
        var pool=Executors.newFixedThreadPool(2);var requests=new ArrayList<Future<MockHttpServletResponse>>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.queryForObject("select id from users where id=? for update",Long.class,id);
                requests.add(pool.submit(first));requests.add(pool.submit(second));awaitBlocked(2);
            });
            return List.of(requests.get(0).get(15,TimeUnit.SECONDS),requests.get(1).get(15,TimeUnit.SECONDS));
        } finally {pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
    }
    void awaitBlocked(int expected) {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(true) {
            jdbc.execute("select pg_stat_clear_snapshot()");
            if(jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like '%users%'",Integer.class)>=expected) return;
            if(System.nanoTime()>deadline) fail("Expected recovery/session requests to overlap on user lock");
            try {Thread.sleep(10);} catch(InterruptedException ex) {Thread.currentThread().interrupt();throw new AssertionError(ex);}
        }
    }
}
