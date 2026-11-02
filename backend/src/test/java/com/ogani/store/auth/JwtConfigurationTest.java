package com.ogani.store.auth;

import com.ogani.store.security.JwtProperties;
import com.ogani.store.security.JwtService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JwtConfigurationTest {
    @Test void missingOrShortSecretFailsFast() {
        assertThrows(IllegalStateException.class, () -> new JwtService(new JwtProperties(null, 15, 7)));
        assertThrows(IllegalStateException.class, () -> new JwtService(new JwtProperties("", 15, 7)));
        assertThrows(IllegalStateException.class, () -> new JwtService(new JwtProperties("short", 15, 7)));
    }
    @Test void configuredSecretIsAccepted() {
        assertEquals(900, new JwtService(new JwtProperties("a".repeat(32), 15, 7)).accessTtlSeconds());
    }
}
