package com.ogani.store.auth.reset;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.concurrent.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class ResetMailDispatcher {
    private final ResetMailSender mail;
    private final PasswordResetIssuer issuer;
    private final ExecutorService workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(50),task -> {
        var thread=new Thread(task,"password-reset-mail");thread.setDaemon(true);return thread;
    },new ThreadPoolExecutor.AbortPolicy());
    public void enqueue(String email,ResetRateLimiter.Permit permit) {
        try {
            workers.execute(() -> {
                try {
                    // The issuer proxy commits before SMTP; superseded queued requests are ignored.
                    var delivery=issuer.issue(email,permit);
                    if(delivery!=null) mail.send(delivery.email(),delivery.token());
                } catch(RuntimeException ex) {
                    log.warn("Reset email delivery failed ({})",ex.getClass().getSimpleName());
                }
            });
        } catch(RejectedExecutionException ex) {
            log.warn("Reset email queue unavailable");
        }
    }
    @PreDestroy public void close() { workers.shutdown(); }
}
