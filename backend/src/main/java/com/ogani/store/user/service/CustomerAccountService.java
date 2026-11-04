package com.ogani.store.user.service;

import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import com.ogani.store.common.util.PasswordRules;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.user.dto.request.ChangePasswordRequest;
import com.ogani.store.user.dto.request.UpdateProfileRequest;
import com.ogani.store.user.dto.response.CustomerProfileResponse;
import com.ogani.store.user.entity.User;
import com.ogani.store.user.entity.UserStatus;
import com.ogani.store.user.repository.RefreshTokenRepository;
import com.ogani.store.user.repository.UserRepository;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomerAccountService {
    private final UserRepository users;
    private final RefreshTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final Validator validator;

    @Transactional
    public CustomerProfileResponse update(UserPrincipal principal,UpdateProfileRequest request) {
        var user=owner(principal);
        if(request==null || !validator.validate(request).isEmpty() || request.provided()==null || request.provided().isEmpty()
                || !Set.of("firstName","lastName","phone").containsAll(request.provided())) throw invalid("Invalid profile input");
        if(request.provided().contains("firstName")) {
            if(request.firstName()==null || request.firstName().isBlank()) throw invalid("firstName must not be blank");
            user.setFirstName(request.firstName());
        }
        if(request.provided().contains("lastName")) {
            if(request.lastName()==null || request.lastName().isBlank()) throw invalid("lastName must not be blank");
            user.setLastName(request.lastName());
        }
        if(request.provided().contains("phone")) user.setPhone(request.phone());
        users.flush();
        return new CustomerProfileResponse(user.getId(),user.getEmail(),user.getFirstName(),user.getLastName(),
                user.getFirstName()+" "+user.getLastName(),user.getPhone());
    }

    @Transactional
    public void changePassword(UserPrincipal principal,ChangePasswordRequest request) {
        var user=owner(principal);
        if(request==null || !validator.validate(request).isEmpty()) throw invalid("Invalid password input");
        PasswordRules.requireBcryptLength(request.currentPassword());
        PasswordRules.requireBcryptLength(request.newPassword());
        if(!request.newPassword().equals(request.confirmPassword())) throw invalid("Password confirmation does not match");
        if(!encoder.matches(request.currentPassword(),user.getPasswordHash())) throw invalid("Current password is incorrect");
        if(encoder.matches(request.newPassword(),user.getPasswordHash())) throw invalid("New password must differ from the current password");
        if(user.getTokenVersion()==Long.MAX_VALUE) throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        user.setPasswordHash(encoder.encode(request.newPassword()));
        user.setTokenVersion(user.getTokenVersion()+1);
        // Flush and clear after bulk revocation to avoid retaining stale managed token flags.
        tokens.revokeAllByUserId(user.getId());
    }

    private User owner(UserPrincipal principal) {
        if(principal==null) throw new AppException(ErrorCode.UNAUTHENTICATED);
        var user=users.findByIdForCartUpdate(principal.getId()).orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        if(user.getStatus()!=UserStatus.ACTIVE) throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        if(user.getRole()==null || !"CUSTOMER".equals(user.getRole().getName())) throw new AppException(ErrorCode.FORBIDDEN);
        if(user.getTokenVersion()!=principal.getTokenVersion()) throw new AppException(ErrorCode.UNAUTHENTICATED);
        return user;
    }

    private static AppException invalid(String message) { return AppException.withCustomMessage(ErrorCode.VALIDATION_ERROR,message); }
}
