package com.ogani.store.auth;

import com.ogani.store.auth.dto.request.RegisterRequest;
import com.ogani.store.auth.dto.response.AuthResponse;
import com.ogani.store.auth.service.interfaces.AuthService;
import com.ogani.store.auth.reset.PasswordResetMaintenanceService;
import com.ogani.store.common.AbstractIntegrationTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Transactional(propagation=Propagation.NOT_SUPPORTED)
@TestPropertySource(properties={"app.password-reset.ip-limit=5","app.password-reset.reset-ip-limit=5","management.health.mail.enabled=false"})
class PasswordResetIT extends AbstractIntegrationTest {
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired PasswordResetMaintenanceService maintenance;
    @MockitoBean JavaMailSender sender;
    @MockitoBean Clock clock;
    static final Instant NOW=Instant.parse("2026-11-04T00:00:00Z");
    static final AtomicInteger IPS=new AtomicInteger();
    final BlockingQueue<SimpleMailMessage> mails=new LinkedBlockingQueue<>();
    AuthResponse session;
    String email,ip;
    Long id;
    @BeforeEach void fixture() {
        assertTrue(jdbc.queryForObject("select current_database()",String.class).endsWith("_test"));
        when(clock.instant()).thenReturn(NOW);when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        doAnswer(call -> { mails.add(new SimpleMailMessage(call.getArgument(0,SimpleMailMessage.class)));return null; }).when(sender).send(any(SimpleMailMessage.class));
        email="recovery-"+UUID.randomUUID()+"@example.test";ip="198.51.100."+IPS.incrementAndGet();
        session=auth.register(new RegisterRequest(email,"oldPassword123","Recovery","Buyer",null));id=session.user().id();
    }
    @AfterEach void cleanup() throws Exception {
        if(id!=null) jdbc.update("delete from users where id=?",id);
        if(jdbc.queryForObject("select to_regclass('password_reset_rate_limits')",String.class)!=null) {
            for(String key:List.of("forgot-ip:"+ip,"reset-ip:"+ip,"forgot-email:"+email,"forgot-email:missing-"+email,"forgot-email:admin@ogani.local"))
                jdbc.update("delete from password_reset_rate_limits where bucket_key=?",hash(key));
        }
    }
    MockHttpServletResponse forgot(String address) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/auth/forgot-password").with(req -> {req.setRemoteAddr(ip);return req;})
                .contentType("application/json").content("{\"email\":\""+address+"\"}")).andReturn().getResponse();
    }
    MockHttpServletResponse reset(String token,String password,String confirm) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/auth/reset-password").with(req -> {req.setRemoteAddr(ip);return req;})
                .contentType("application/json").content("{\"token\":\""+token+"\",\"newPassword\":\""+password+"\",\"confirmPassword\":\""+confirm+"\"}")).andReturn().getResponse();
    }
    String requestToken() throws Exception { assertEquals(200,forgot(email).getStatus());return mailToken(); }
    String mailToken() throws Exception {
        var message=mails.poll(5,TimeUnit.SECONDS);assertNotNull(message,"Expected a reset email after transaction commit");
        assertArrayEquals(new String[]{email},message.getTo());
        var match=Pattern.compile("#token=([A-Za-z0-9_-]{43})").matcher(message.getText());assertTrue(match.find());return match.group(1);
    }
    void advance(long seconds) { when(clock.instant()).thenReturn(NOW.plusSeconds(seconds)); }
    @Test void forgotNormalizesEmailAndNeverReturnsRawToken() throws Exception {
        var response=forgot("  "+email.toUpperCase(Locale.ROOT)+"  ");assertEquals(200,response.getStatus());String token=mailToken();
        assertFalse(response.getContentAsString().contains(token));assertFalse(response.getContentAsString().contains(email));
        var row=jdbc.queryForMap("select token_hash,expires_at,issued_token_version from password_reset_tokens where user_id=?",id);
        assertEquals(hash(token),row.get("token_hash"));assertNotEquals(token,row.get("token_hash"));
        assertEquals(LocalDateTime.ofInstant(NOW.plusSeconds(900),ZoneOffset.UTC),jdbc.queryForObject("select expires_at from password_reset_tokens where user_id=?",LocalDateTime.class,id));
    }
    @Test void missingBlockedAndNonCustomerReceiveSameResponseWithoutEmail() throws Exception {
        var missing=forgot("missing-"+email);assertEquals(200,missing.getStatus());
        jdbc.update("update users set status='BLOCKED' where id=?",id);
        var blocked=forgot(email);var admin=forgot("admin@ogani.local");
        assertEquals(missing.getContentAsString(),blocked.getContentAsString());assertEquals(missing.getContentAsString(),admin.getContentAsString());
        verify(sender,after(100).never()).send(any(SimpleMailMessage.class));
    }
    @ParameterizedTest @ValueSource(strings={"","   ","not-an-email","a@@example.test"})
    void invalidEmailReturns400(String address) throws Exception { assertEquals(400,forgot(address).getStatus());verify(sender,never()).send(any(SimpleMailMessage.class)); }
    @Test void resetConsumesOnceRevokesAllSessionsAndAllowsNewLogin() throws Exception {
        String token=requestToken();
        var result=reset(token,"newPassword456","newPassword456");assertEquals(200,result.getStatus());
        assertFalse(result.getContentAsString().contains("newPassword456"));assertFalse(result.getContentAsString().contains("passwordHash"));
        assertTrue(encoder.matches("newPassword456",jdbc.queryForObject("select password_hash from users where id=?",String.class,id)));
        assertNotNull(jdbc.queryForObject("select used_at from password_reset_tokens where user_id=?",Object.class,id));
        assertEquals(1L,jdbc.queryForObject("select token_version from users where id=?",Long.class,id));
        assertEquals(0,jdbc.queryForObject("select count(*) from refresh_tokens where user_id=? and not revoked",Integer.class,id));
        assertEquals(400,reset(token,"anotherPassword789","anotherPassword789").getStatus());
        assertEquals(401,mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").header("Authorization","Bearer "+session.accessToken())).andReturn().getResponse().getStatus());
        assertEquals(401,mvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh").contentType("application/json").content("{\"refreshToken\":\""+session.refreshToken()+"\"}")).andReturn().getResponse().getStatus());
        var login=mvc.perform(MockMvcRequestBuilders.post("/api/auth/login").contentType("application/json").content("{\"email\":\""+email+"\",\"password\":\"newPassword456\"}")).andReturn().getResponse();assertEquals(200,login.getStatus());
    }
    @ParameterizedTest @ValueSource(longs={899,900,901})
    void expiryBoundaryIsExclusive(long seconds) throws Exception {
        String token=requestToken();advance(seconds);
        assertEquals(seconds<900?200:400,reset(token,"newPassword456","newPassword456").getStatus());
    }
    @Test void newLinkInvalidatesOldAndCooldownDoesNotRotateToken() throws Exception {
        String first=requestToken();var response=forgot(email);assertEquals(200,response.getStatus());
        verify(sender,after(100).times(1)).send(any(SimpleMailMessage.class));
        advance(61);assertEquals(response.getContentAsString(),forgot(email).getContentAsString());String second=mailToken();
        assertEquals(400,reset(first,"newPassword456","newPassword456").getStatus());
        assertEquals(200,reset(second,"newPassword456","newPassword456").getStatus());
    }
    @Test void emailLimitAndWindowAreEnforcedPrivately() throws Exception {
        for(int i=0;i<3;i++) {advance(i*61L);requestToken();}
        advance(183);assertEquals(200,forgot(email).getStatus());verify(sender,after(100).times(3)).send(any(SimpleMailMessage.class));
        advance(901);requestToken();verify(sender,times(4)).send(any(SimpleMailMessage.class));
    }
    @Test void ipLimitAppliesToMissingEmailsAndResetsAfterWindow() throws Exception {
        for(int i=0;i<5;i++) assertEquals(200,forgot("missing-"+email).getStatus());
        assertEquals(200,forgot(email).getStatus());verify(sender,after(100).never()).send(any(SimpleMailMessage.class));
        advance(901);requestToken();
    }
    @Test void invalidResetAttemptsStillCountDespiteTransactionRollback() throws Exception {
        for(int i=0;i<5;i++) assertEquals(400,reset("A".repeat(43),"newPassword456","newPassword456").getStatus());
        assertEquals(429,reset("A".repeat(43),"newPassword456","newPassword456").getStatus());
    }
    @Test void passwordChangeInvalidatesOutstandingResetLink() throws Exception {
        String token=requestToken();
        assertEquals(200,mvc.perform(MockMvcRequestBuilders.put("/api/users/me/password").header("Authorization","Bearer "+session.accessToken()).contentType("application/json")
                .content("{\"currentPassword\":\"oldPassword123\",\"newPassword\":\"changedPassword789\",\"confirmPassword\":\"changedPassword789\"}")).andReturn().getResponse().getStatus());
        assertEquals(400,reset(token,"newPassword456","newPassword456").getStatus());
    }
    @ParameterizedTest @ValueSource(strings={"short","oldPassword123","      "})
    void invalidNewPasswordsDoNotConsumeToken(String password) throws Exception {
        String token=requestToken();assertEquals(400,reset(token,password,password).getStatus());
        assertNull(jdbc.queryForObject("select used_at from password_reset_tokens where user_id=?",Object.class,id));
        assertEquals(200,reset(token,"newPassword456","newPassword456").getStatus());
    }
    @Test void mismatchAndUtf8OverflowDoNotConsumeToken() throws Exception {
        String token=requestToken();assertEquals(400,reset(token,"newPassword456","differentPassword").getStatus());
        assertEquals(400,reset(token,"é".repeat(37),"é".repeat(37)).getStatus());
        assertEquals(200,reset(token,"newPassword456","newPassword456").getStatus());
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void ineligibleAccountCannotUseAnAlreadyIssuedToken(boolean blocked) throws Exception {
        String token=requestToken();
        if(blocked) jdbc.update("update users set status='BLOCKED' where id=?",id);
        else jdbc.update("update users set role_id=2 where id=?",id);
        assertEquals(400,reset(token,"newPassword456","newPassword456").getStatus());
    }
    @Test void strictRequestTypesAndMissingFieldsAreRejected() throws Exception {
        for(String body:List.of("null","[]","{}","{\"email\":123}","{\"email\":null}","{\"email\":\"a@example.test\",\"userId\":1}"))
            assertEquals(400,mvc.perform(MockMvcRequestBuilders.post("/api/auth/forgot-password").contentType("application/json").content(body)).andReturn().getResponse().getStatus());
        for(String body:List.of("null","{}","{\"token\":123}","{\"token\":\"short\",\"newPassword\":\"newPassword456\",\"confirmPassword\":\"newPassword456\"}",
                "{\"token\":\""+"A".repeat(43)+"\",\"newPassword\":123,\"confirmPassword\":\"newPassword456\"}"))
            assertEquals(400,mvc.perform(MockMvcRequestBuilders.post("/api/auth/reset-password").contentType("application/json").content(body)).andReturn().getResponse().getStatus());
    }
    @Test void resetPageAndItsAssetsArePublic() throws Exception {
        for(String path:List.of("/reset-password.html","/password-reset.js","/password-reset.css"))
            assertEquals(200,mvc.perform(MockMvcRequestBuilders.get(path)).andReturn().getResponse().getStatus());
    }
    @Test void requestHostCannotPoisonTheResetLink() throws Exception {
        var result=mvc.perform(MockMvcRequestBuilders.post("/api/auth/forgot-password").header("Host","attacker.example.test")
                .with(req -> {req.setRemoteAddr(ip);return req;}).contentType("application/json").content("{\"email\":\""+email+"\"}")).andReturn().getResponse();
        assertEquals(200,result.getStatus());var message=mails.poll(5,TimeUnit.SECONDS);assertNotNull(message);
        assertTrue(message.getText().contains("http://localhost:8080/reset-password.html#token="));assertFalse(message.getText().contains("attacker.example.test"));
    }
    @Test void slowSmtpDoesNotBlockForgotResponse() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var finished=new CountDownLatch(1);
        doAnswer(call -> {entered.countDown();try {release.await(5,TimeUnit.SECONDS);} finally {finished.countDown();}return null;}).when(sender).send(any(SimpleMailMessage.class));
        var pool=Executors.newSingleThreadExecutor();
        try {
            var response=pool.submit(() -> forgot(email));
            assertEquals(200,response.get(3,TimeUnit.SECONDS).getStatus());assertTrue(entered.await(5,TimeUnit.SECONDS));
        } finally {release.countDown();assertTrue(finished.await(5,TimeUnit.SECONDS));pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
    }
    @Test void cleanupRemovesOldRecordsAndPreservesActiveToken() throws Exception {
        String old=requestToken();advance(61);String active=requestToken();
        var cutoff=LocalDateTime.ofInstant(NOW,ZoneOffset.UTC).minusDays(2);
        jdbc.update("update password_reset_tokens set created_at=?,expires_at=? where token_hash=?",cutoff.minusMinutes(15),cutoff,hash(old));
        String oldBucket=hash("old-extra:"+email);
        try {
            jdbc.update("insert into password_reset_rate_limits(bucket_key,window_started_at,request_count) values(?,?,1)",oldBucket,cutoff);
            assertTrue(maintenance.cleanup()>=2);
            assertEquals(0,jdbc.queryForObject("select count(*) from password_reset_tokens where token_hash=?",Integer.class,hash(old)));
            assertEquals(1,jdbc.queryForObject("select count(*) from password_reset_tokens where token_hash=?",Integer.class,hash(active)));
            assertEquals(200,reset(active,"newPassword456","newPassword456").getStatus());
        } finally {jdbc.update("delete from password_reset_rate_limits where bucket_key=?",oldBucket);}
    }
    @Test void smtpFailureDoesNotExposeAccountOrReturnToken() throws Exception {
        doThrow(new MailSendException("test-only SMTP unavailable")).when(sender).send(any(SimpleMailMessage.class));
        var response=forgot(email);assertEquals(200,response.getStatus());
        verify(sender,timeout(2000)).send(any(SimpleMailMessage.class));
        assertEquals(response.getContentAsString(),forgot("missing-"+email).getContentAsString());
    }
    static String hash(String raw) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
}
