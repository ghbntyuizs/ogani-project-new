package com.ogani.store.user.repository;

import com.ogani.store.catalog.entity.*;
import com.ogani.store.user.entity.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByToken(String token);

    @Modifying
    @Query(value = "delete from refresh_tokens where id in (select id from refresh_tokens "
            + "where revoked=true or expires_at<=:now order by id limit 1000 for update skip locked)", nativeQuery = true)
    int deleteInactiveBatch(LocalDateTime now);
}
