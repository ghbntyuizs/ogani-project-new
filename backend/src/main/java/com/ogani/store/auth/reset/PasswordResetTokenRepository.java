package com.ogani.store.auth.reset;

import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken,Long> {
    @Query("select t.user.id from PasswordResetToken t where t.tokenHash=:hash")
    Optional<Long> findUserIdByHash(String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t where t.tokenHash=:hash")
    Optional<PasswordResetToken> findForUpdate(String hash);
    @Modifying(flushAutomatically=true)
    @Query("update PasswordResetToken t set t.revoked=true where t.user.id=:userId and t.usedAt is null and t.revoked=false")
    int revokeUnused(Long userId);
    @Modifying
    @Query(value="delete from password_reset_tokens where id in (select id from password_reset_tokens where expires_at<:cutoff order by expires_at,id limit 1000 for update skip locked)",nativeQuery=true)
    int cleanup(LocalDateTime cutoff);
}
