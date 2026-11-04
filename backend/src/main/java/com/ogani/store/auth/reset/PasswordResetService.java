package com.ogani.store.auth.reset;

import com.ogani.store.auth.dto.request.ForgotPasswordRequest;
import com.ogani.store.auth.dto.request.ResetPasswordRequest;
import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import com.ogani.store.common.util.PasswordRules;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
@RequiredArgsConstructor
public class PasswordResetService {
    private final ResetRateLimiter rates;
    private final ResetMailDispatcher mail;
    private final PasswordResetProperties props;
    private final Validator validator;
    private final PasswordResetConsumer consumer;

    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public void forgot(ForgotPasswordRequest request,String sourceIp) {
        if(request==null || !validator.validate(request).isEmpty()) throw new AppException(ErrorCode.VALIDATION_ERROR);
        if(!rates.consume("forgot-ip:"+sourceIp,props.ipLimit(),0)) return;
        var permit=rates.acquire("forgot-email:"+request.email(),props.emailLimit(),props.cooldownSeconds());
        if(permit==null) return;
        mail.enqueue(request.email(),permit);
    }

    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public void reset(ResetPasswordRequest request,String sourceIp) {
        if(request==null || !validator.validate(request).isEmpty()) throw new AppException(ErrorCode.VALIDATION_ERROR);
        if(!rates.consume("reset-ip:"+sourceIp,props.resetIpLimit(),0)) throw new AppException(ErrorCode.PASSWORD_RESET_RATE_LIMITED);
        PasswordRules.requireBcryptLength(request.newPassword());
        if(!request.newPassword().equals(request.confirmPassword())) throw invalidInput("Password confirmation does not match");
        consumer.consume(request);
    }

    private static AppException invalidInput(String message) { return AppException.withCustomMessage(ErrorCode.VALIDATION_ERROR,message); }
}
