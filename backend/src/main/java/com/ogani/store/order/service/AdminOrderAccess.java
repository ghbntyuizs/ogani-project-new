package com.ogani.store.order.service;

import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.user.entity.User;
import com.ogani.store.user.entity.UserStatus;
import com.ogani.store.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AdminOrderAccess {
    private final UserRepository users;

    public User requireActive(UserPrincipal principal) {
        if(principal==null) throw new AppException(ErrorCode.UNAUTHENTICATED);
        var actor=users.findById(principal.getId()).orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        if(actor.getStatus()!=UserStatus.ACTIVE) throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        if(actor.getRole()==null || !Set.of("ADMIN","STAFF").contains(actor.getRole().getName())) throw new AppException(ErrorCode.FORBIDDEN);
        return actor;
    }
}
