package com.ogani.store.user.service;

import com.ogani.store.user.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TokenMaintenanceService {
    private final RefreshTokenRepository tokens;
    @Transactional
    public int cleanup(LocalDateTime now) { return tokens.deleteInactiveBatch(now); }
}
