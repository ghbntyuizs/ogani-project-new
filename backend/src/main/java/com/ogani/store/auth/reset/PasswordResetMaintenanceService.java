package com.ogani.store.auth.reset;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PasswordResetMaintenanceService {
    private final PasswordResetTokenRepository tokens;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    @Transactional
    public int cleanup() {
        var cutoff=LocalDateTime.now(clock).minusDays(1);
        return tokens.cleanup(cutoff)+jdbc.update("delete from password_reset_rate_limits where bucket_key in (select bucket_key from password_reset_rate_limits where window_started_at<? order by window_started_at limit 1000 for update skip locked)",cutoff);
    }
}
