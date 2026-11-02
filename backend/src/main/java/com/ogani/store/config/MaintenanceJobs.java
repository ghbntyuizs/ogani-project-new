package com.ogani.store.config;

import com.ogani.store.catalog.service.impl.ManagedUploadService;
import com.ogani.store.user.service.TokenMaintenanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "app.maintenance", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class MaintenanceJobs {
    private final ManagedUploadService uploads;
    private final TokenMaintenanceService tokens;
    @Value("${app.maintenance.upload-retention-hours:24}")
    private long uploadRetentionHours;

    @Scheduled(cron = "${app.maintenance.cron:0 0 * * * *}", zone = "UTC")
    public void cleanup() {
        LocalDateTime now = LocalDateTime.now();
        int tokenCount = tokens.cleanup(now);
        int imageCount = uploads.cleanupUnclaimed(now.minusHours(Math.max(24, uploadRetentionHours)), 100);
        log.info("Maintenance removed {} inactive tokens and {} never-used uploads", tokenCount, imageCount);
    }
}
