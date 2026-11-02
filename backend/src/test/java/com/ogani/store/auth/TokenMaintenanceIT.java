package com.ogani.store.auth;

import com.ogani.store.common.AbstractIntegrationTest;
import com.ogani.store.user.service.TokenMaintenanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TokenMaintenanceIT extends AbstractIntegrationTest {
    @Autowired TokenMaintenanceService maintenance;
    @Autowired JdbcTemplate jdbc;
    @Test void cleanupRetainsActiveTokensAndRemovesExpiredOrRevokedTokens() {
        String active = UUID.randomUUID().toString(), revoked = UUID.randomUUID().toString(), expired = UUID.randomUUID().toString();
        var now = LocalDateTime.now();
        jdbc.update("insert into refresh_tokens(user_id,token,expires_at,revoked) values (3,?,?,false)", active, now.plusDays(1));
        jdbc.update("insert into refresh_tokens(user_id,token,expires_at,revoked) values (3,?,?,true)", revoked, now.plusDays(1));
        jdbc.update("insert into refresh_tokens(user_id,token,expires_at,revoked) values (3,?,?,false)", expired, now.minusDays(1));
        maintenance.cleanup(now);
        assertEquals(1, jdbc.queryForObject("select count(*) from refresh_tokens where token=?", Integer.class, active));
        assertEquals(0, jdbc.queryForObject("select count(*) from refresh_tokens where token in (?,?)", Integer.class, revoked, expired));
    }
}
