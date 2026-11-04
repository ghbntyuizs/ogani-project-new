package com.ogani.store.auth.reset;

import com.ogani.store.auth.dto.request.ResetPasswordRequest;
import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import com.ogani.store.user.repository.RefreshTokenRepository;
import com.ogani.store.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PasswordResetConsumer {
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final Clock clock;

    @Transactional
    public void consume(ResetPasswordRequest request) {
        String hash=ResetTokenCodec.hash(request.token());
        Long owner=tokens.findUserIdByHash(hash).orElseThrow(PasswordResetConsumer::invalidToken);
        var user=users.findByIdForCartUpdate(owner).orElseThrow(PasswordResetConsumer::invalidToken);
        var token=tokens.findForUpdate(hash).orElseThrow(PasswordResetConsumer::invalidToken);
        LocalDateTime now=LocalDateTime.now(clock);
        if(!PasswordResetIssuer.eligible(user) || !token.getUser().getId().equals(owner) || token.isRevoked() || token.getUsedAt()!=null
                || !token.getExpiresAt().isAfter(now) || !token.getIssuedTokenVersion().equals(user.getTokenVersion())
                || user.getTokenVersion()==Long.MAX_VALUE) throw invalidToken();
        if(encoder.matches(request.newPassword(),user.getPasswordHash()))
            throw AppException.withCustomMessage(ErrorCode.VALIDATION_ERROR,"New password must differ from the current password");
        user.setPasswordHash(encoder.encode(request.newPassword()));user.setTokenVersion(user.getTokenVersion()+1);token.setUsedAt(now);
        tokens.revokeUnused(owner);refreshTokens.revokeAllByUserId(owner);
    }
    private static AppException invalidToken() { return new AppException(ErrorCode.INVALID_RESET_TOKEN); }
}
