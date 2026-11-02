package com.ogani.store.auth;

import com.ogani.store.auth.service.impl.AuthServiceImpl;
import com.ogani.store.common.exception.AppException;
import com.ogani.store.security.*;
import com.ogani.store.user.entity.*;
import com.ogani.store.user.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthRefreshTest {
    @Test void rejectsInactiveUserBeforeIssuingTokens() {
        var tokens = mock(RefreshTokenRepository.class);
        var user = new User();
        user.setStatus(UserStatus.BLOCKED);
        var role = new Role(); role.setName("CUSTOMER"); user.setRole(role);
        var token = new RefreshToken();
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(tokens.findByToken(anyString())).thenReturn(Optional.of(token));
        var service = new AuthServiceImpl(mock(AuthenticationManager.class), mock(UserRepository.class),
                mock(RoleRepository.class), tokens, mock(PasswordEncoder.class),
                mock(JwtService.class), new JwtProperties("a".repeat(32), 15, 7));
        assertThrows(AppException.class, () -> service.refresh("raw-token"));
        verify(tokens, never()).save(any());
    }
}
