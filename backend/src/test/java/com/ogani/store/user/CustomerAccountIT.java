package com.ogani.store.user;

import com.jayway.jsonpath.JsonPath;
import com.ogani.store.auth.dto.request.RegisterRequest;
import com.ogani.store.auth.dto.request.LoginRequest;
import com.ogani.store.auth.dto.response.AuthResponse;
import com.ogani.store.auth.service.interfaces.AuthService;
import com.ogani.store.common.AbstractIntegrationTest;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.security.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import com.ogani.store.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import java.util.UUID;
import java.util.List;
import jakarta.persistence.EntityManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CustomerAccountIT extends AbstractIntegrationTest {
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager em;
    @Autowired JwtProperties jwtProperties;
    AuthResponse session;
    String email;
    long id;
    static final String PASSWORD_BODY="{\"currentPassword\":\"oldPassword123\",\"newPassword\":\"newPassword456\",\"confirmPassword\":\"newPassword456\"}";
    @BeforeEach void fixture() {
        email="account-"+UUID.randomUUID()+"@example.test";
        session=auth.register(new RegisterRequest(email,"oldPassword123","Original","Buyer","0901234567"));
        id=session.user().id();
    }
    ResultActions profile(String body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.patch("/api/users/me").header("Authorization","Bearer "+session.accessToken()).contentType("application/json").content(body));
    }
    ResultActions password(String body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.put("/api/users/me/password").header("Authorization","Bearer "+session.accessToken()).contentType("application/json").content(body));
    }
    @Test void partialProfileUpdateNormalizesAndPreservesProtectedFields() throws Exception {
        var before=jdbc.queryForMap("select id,email,role_id,status,password_hash from users where id=?",id);
        profile("{\"firstName\":\"  Nguyễn   Văn \",\"phone\":\"+84901234567\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Nguyễn Văn")).andExpect(jsonPath("$.data.lastName").value("Buyer"))
                .andExpect(jsonPath("$.data.fullName").value("Nguyễn Văn Buyer")).andExpect(jsonPath("$.data.phone").value("0901234567"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist()).andExpect(jsonPath("$.data.password").doesNotExist());
        assertEquals(before,jdbc.queryForMap("select id,email,role_id,status,password_hash from users where id=?",id));
        mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").header("Authorization","Bearer "+session.accessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fullName").value("Nguyễn Văn Buyer"));
        profile("{\"phone\":null}").andExpect(status().isOk());
        assertNull(jdbc.queryForObject("select phone from users where id=?",String.class,id));
    }
    @ParameterizedTest @ValueSource(strings={"{}","null","[]","{\"firstName\":null}","{\"lastName\":\"   \"}","{\"firstName\":123}","{\"phone\":\"\"}","{\"phone\":\"invalid\"}","{\"phone\":123}","{\"role\":\"ADMIN\"}","{\"status\":\"ACTIVE\"}","{\"userId\":1}","{\"id\":1}","{\"email\":\"x@example.test\"}","{\"firstName\":\"Valid\",\"passwordHash\":\"x\"}"})
    void rejectsInvalidOrProtectedProfileFields(String body) throws Exception { profile(body).andExpect(status().isBadRequest()); }
    @Test void validatesNameLimitsAndAllowsSharedPhone() throws Exception {
        profile("{\"firstName\":\""+"a".repeat(60)+"\",\"lastName\":\""+"b".repeat(60)+"\"}").andExpect(status().isOk());
        profile("{\"firstName\":\""+"a".repeat(61)+"\"}").andExpect(status().isBadRequest());
        var other=auth.register(new RegisterRequest("other-"+UUID.randomUUID()+"@example.test","oldPassword123","Other","Buyer","0901234567"));
        profile("{\"phone\":\"0901234567\"}").andExpect(status().isOk());
        assertEquals(2,jdbc.queryForObject("select count(*) from users where id in (?,?) and phone='0901234567'",Integer.class,id,other.user().id()));
    }
    @Test void passwordChangeHashesAndRevokesAllSessionsImmediately() throws Exception {
        var second=auth.login(new LoginRequest(email,"oldPassword123"));
        password(PASSWORD_BODY).andExpect(status().isOk()).andExpect(jsonPath("$.data.password").doesNotExist()).andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        assertTrue(encoder.matches("newPassword456",jdbc.queryForObject("select password_hash from users where id=?",String.class,id)));
        assertEquals(0,jdbc.queryForObject("select count(*) from refresh_tokens where user_id=? and not revoked",Integer.class,id));
        for(var old:List.of(session,second)) {
            mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").header("Authorization","Bearer "+old.accessToken())).andExpect(status().isUnauthorized());
            mvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh").contentType("application/json").content("{\"refreshToken\":\""+old.refreshToken()+"\"}")).andExpect(status().isUnauthorized());
        }
        mvc.perform(MockMvcRequestBuilders.post("/api/auth/login").contentType("application/json").content("{\"email\":\""+email+"\",\"password\":\"oldPassword123\"}")).andExpect(status().isUnauthorized());
        var fresh=auth.login(new LoginRequest(email,"newPassword456"));
        mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").header("Authorization","Bearer "+fresh.accessToken())).andExpect(status().isOk());
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"currentPassword\":null}","{\"currentPassword\":123}","{\"currentPassword\":\"wrong\",\"newPassword\":\"newPassword456\",\"confirmPassword\":\"newPassword456\"}","{\"currentPassword\":\"oldPassword123\",\"newPassword\":\"short\",\"confirmPassword\":\"short\"}","{\"currentPassword\":\"oldPassword123\",\"newPassword\":\"oldPassword123\",\"confirmPassword\":\"oldPassword123\"}","{\"currentPassword\":\"oldPassword123\",\"newPassword\":\"newPassword456\",\"confirmPassword\":\"different\"}","{\"currentPassword\":\"oldPassword123\",\"newPassword\":\"      \",\"confirmPassword\":\"      \"}"})
    void rejectsBadPasswordInputWithoutChangingCredentials(String body) throws Exception {
        var hash=jdbc.queryForObject("select password_hash from users where id=?",String.class,id);
        password(body).andExpect(status().isBadRequest());
        assertEquals(hash,jdbc.queryForObject("select password_hash from users where id=?",String.class,id));
    }
    @Test void rejectsPasswordBeyondBcryptUtf8ByteLimit() throws Exception {
        password(PASSWORD_BODY.replace("newPassword456","é".repeat(37))).andExpect(status().isBadRequest());
    }
    @Test void registrationRejectsPasswordBeyondBcryptUtf8ByteLimit() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/auth/register").contentType("application/json")
                .content("{\"email\":\"bad-"+UUID.randomUUID()+"@example.test\",\"password\":\""+"é".repeat(37)+"\",\"firstName\":\"Test\",\"lastName\":\"Buyer\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void loginRejectsPasswordBeyondBcryptUtf8ByteLimit() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\""+email+"\",\"password\":\""+"é".repeat(37)+"\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void acceptsExactBcryptUtf8BoundaryAndDoesNotTrimPasswords() throws Exception {
        String password="é".repeat(36);
        password(PASSWORD_BODY.replace("newPassword456",password)).andExpect(status().isOk());
        assertNotNull(auth.login(new LoginRequest(email,password)).accessToken());
    }
    @Test void legacyAccessTokenWorksOnlyUntilFirstPasswordChange() throws Exception {
        String legacy=Jwts.builder().subject(email).expiration(new Date(System.currentTimeMillis()+60000))
                .signWith(Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8))).compact();
        mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").header("Authorization","Bearer "+legacy)).andExpect(status().isOk());
        password(PASSWORD_BODY).andExpect(status().isOk());
        mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").header("Authorization","Bearer "+legacy)).andExpect(status().isUnauthorized());
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void checksAnonymousAdminStaffAndBlockedUser(boolean changingPassword) throws Exception {
        String path=changingPassword?"/api/users/me/password":"/api/users/me",body=changingPassword?PASSWORD_BODY:"{\"firstName\":\"Valid\"}";
        var req=changingPassword?MockMvcRequestBuilders.put(path):MockMvcRequestBuilders.patch(path);
        mvc.perform(req.contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        for(String actor:new String[]{ADMIN,STAFF}) {
            req=changingPassword?MockMvcRequestBuilders.put(path):MockMvcRequestBuilders.patch(path);
            mvc.perform(req.with(user(UserPrincipal.from(users.findByEmail(actor).orElseThrow()))).contentType("application/json").content(body)).andExpect(status().isForbidden());
        }
        jdbc.update("update users set status='BLOCKED' where id=?",id);
        em.clear();
        req=changingPassword?MockMvcRequestBuilders.put(path):MockMvcRequestBuilders.patch(path);
        mvc.perform(req.header("Authorization","Bearer "+session.accessToken()).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
    }
}
