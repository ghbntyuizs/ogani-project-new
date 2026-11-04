package com.ogani.store.auth;

import com.ogani.store.security.JwtProperties;
import com.ogani.store.security.JwtService;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.user.entity.Role;
import com.ogani.store.user.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.stream.Stream;
import java.math.BigInteger;
import static org.junit.jupiter.api.Assertions.*;

class JwtTokenVersionTest {
    static final String SECRET="test-version-secret-at-least-32-characters";
    final JwtService jwt=new JwtService(new JwtProperties(SECRET,15,7));
    @Test void legacySignedTokenDefaultsToVersionZero() {
        String token=Jwts.builder().subject("buyer@example.test").expiration(new Date(System.currentTimeMillis()+60000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        assertEquals(0,jwt.extractIdentity(token).tokenVersion());
        assertEquals("buyer@example.test",jwt.extractEmail(token));
    }
    @Test void issuedTokenCarriesCurrentUserVersion() {
        var user=new User();user.setId(1L);user.setEmail("buyer@example.test");user.setTokenVersion(4L);
        var role=new Role();role.setName("CUSTOMER");user.setRole(role);
        assertEquals(4,jwt.extractIdentity(jwt.generateAccessToken(UserPrincipal.from(user))).tokenVersion());
    }
    static Stream<Object> invalidVersions() { return Stream.of(-1,0.5,true,"0",new BigInteger("18446744073709551616")); }
    @ParameterizedTest @MethodSource("invalidVersions")
    void rejectsMalformedVersionClaims(Object version) {
        String token=Jwts.builder().subject("buyer@example.test").claim("ver",version).expiration(new Date(System.currentTimeMillis()+60000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        assertThrows(JwtException.class,() -> jwt.extractIdentity(token));
    }
}
