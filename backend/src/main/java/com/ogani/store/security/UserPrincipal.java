package com.ogani.store.security;

import com.ogani.store.user.entity.User;
import com.ogani.store.user.entity.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;

public class UserPrincipal implements UserDetails {
    private final Long id;
    private final String email;
    private final String passwordHash;
    private final String role;
    private final boolean enabled;

    private UserPrincipal(Long id, String email, String passwordHash, String role, boolean enabled) {
        this.id = id; this.email = email; this.passwordHash = passwordHash; this.role = role; this.enabled = enabled;
    }

    public static UserPrincipal from(User u) {
        return new UserPrincipal(u.getId(), u.getEmail(), u.getPasswordHash(), u.getRole().getName(), u.getStatus() == UserStatus.ACTIVE);
    }

    public Long getId() { return id; }
    public String getRole() { return role; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(new SimpleGrantedAuthority("ROLE_" + role)); }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isEnabled() { return enabled; }
}
