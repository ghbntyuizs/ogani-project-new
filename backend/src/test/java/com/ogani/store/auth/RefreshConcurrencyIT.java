package com.ogani.store.auth;

import com.ogani.store.auth.dto.request.RegisterRequest;
import com.ogani.store.auth.service.interfaces.AuthService;
import com.ogani.store.common.AbstractIntegrationTest;
import com.ogani.store.common.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class RefreshConcurrencyIT extends AbstractIntegrationTest {
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void onlyOneConcurrentRefreshSucceeds() throws Exception {
        var email = "refresh-" + UUID.randomUUID() + "@example.com";
        var response = auth.register(new RegisterRequest(email, "testPassword123", "Refresh", "Test", null));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1);
            Callable<Boolean> refresh = () -> {
                start.await();
                try { auth.refresh(response.refreshToken()); return true; }
                catch (AppException ex) { return false; }
            };
            var first = executor.submit(refresh); var second = executor.submit(refresh);
            start.countDown();
            assertNotEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThrows(AppException.class, () -> auth.refresh(response.refreshToken()));
        } finally {
            executor.shutdownNow(); executor.awaitTermination(10, TimeUnit.SECONDS);
            jdbc.update("delete from refresh_tokens where user_id = (select id from users where email = ?)", email);
            jdbc.update("delete from users where email = ?", email);
        }
    }
}
