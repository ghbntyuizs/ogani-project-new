package com.ogani.store.auth.reset;

import com.ogani.store.user.entity.User;
import com.ogani.store.user.entity.UserStatus;
import com.ogani.store.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PasswordResetIssuer {
    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordResetProperties props;
    private final Clock clock;
    private final ResetRateLimiter rates;

    public record Delivery(String email,String token) {
        @Override public String toString() { return "ResetDelivery[REDACTED]"; }
    }
    @Transactional
    public Delivery issue(String email,ResetRateLimiter.Permit permit) {
        var user=users.findByEmailForUpdate(email).orElse(null);
        if(!eligible(user) || user.getTokenVersion()==Long.MAX_VALUE) return null;
        if(!ResetTokenCodec.hash("forgot-email:"+email).equals(permit.bucketKey()) || !rates.isCurrent(permit)) return null;
        LocalDateTime now=LocalDateTime.now(clock);String raw=ResetTokenCodec.generate();
        tokens.revokeUnused(user.getId());
        var token=new PasswordResetToken();token.setUser(user);token.setTokenHash(ResetTokenCodec.hash(raw));
        token.setIssuedTokenVersion(user.getTokenVersion());token.setCreatedAt(now);token.setExpiresAt(now.plusMinutes(props.ttlMinutes()));
        tokens.saveAndFlush(token);
        return new Delivery(user.getEmail(),raw);
    }
    static boolean eligible(User user) {
        return user!=null && user.getStatus()==UserStatus.ACTIVE && user.getRole()!=null && "CUSTOMER".equals(user.getRole().getName());
    }
}
