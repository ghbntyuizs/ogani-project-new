package com.ogani.store.auth.reset;

import com.ogani.store.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="password_reset_tokens")
@Getter @Setter @NoArgsConstructor
public class PasswordResetToken {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",nullable=false) private User user;
    @Column(name="token_hash",nullable=false,length=64,unique=true) private String tokenHash;
    @Column(name="issued_token_version",nullable=false) private Long issuedTokenVersion;
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME) @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME) @Column(name="expires_at",nullable=false) private LocalDateTime expiresAt;
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME) @Column(name="used_at") private LocalDateTime usedAt;
    @Column(nullable=false) private boolean revoked;
}
