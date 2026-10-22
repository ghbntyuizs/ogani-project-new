package com.ogani.store.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    private final Duration accessTtl;

    public JwtService(JwtProperties props) {
        if (props.secret() == null || props.secret().length() < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(props.accessTtlMinutes());
    }

    public String generateAccessToken(UserPrincipal p) {
        Date now = new Date();
        return Jwts.builder()
                .subject(p.getUsername())
                .claim("uid", p.getId())
                .claim("role", p.getRole())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTtl.toMillis()))
                .signWith(key)
                .compact();
    }

    /** Ném JwtException nếu token sai chữ ký / hết hạn. */
    public String extractEmail(String token) throws JwtException {
        Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return c.getSubject();
    }

    public long accessTtlSeconds() { return accessTtl.toSeconds(); }
}
