package com.ogani.store.auth.service.impl;


import com.ogani.store.auth.dto.UserInfo;
import com.ogani.store.auth.dto.request.LoginRequest;
import com.ogani.store.auth.dto.request.RegisterRequest;
import com.ogani.store.auth.dto.response.AuthResponse;
import com.ogani.store.auth.service.interfaces.AuthService;
import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import com.ogani.store.security.JwtProperties;
import com.ogani.store.security.JwtService;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.user.entity.RefreshToken;
import com.ogani.store.user.entity.Role;
import com.ogani.store.user.entity.User;
import com.ogani.store.user.repository.RefreshTokenRepository;
import com.ogani.store.user.repository.RoleRepository;
import com.ogani.store.user.repository.UserRepository;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authManager;
    private final UserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final JwtProperties props;

    private final SecureRandom random = new SecureRandom();

    public AuthServiceImpl(
            AuthenticationManager authManager,
            UserRepository users,
            RoleRepository roles,
            RefreshTokenRepository refreshTokens,
            PasswordEncoder encoder,
            JwtService jwt,
            JwtProperties props
    ) {
        this.authManager = authManager;
        this.users = users;
        this.roles = roles;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.props = props;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest r) {
        String email = r.email().trim().toLowerCase(Locale.ROOT);

        if (users.existsByEmail(email)) {
            throw new AppException(
                    ErrorCode.RESOURCE_ALREADY_EXISTS, "Email"
            );
        }

        Role customer = roles.findByName("CUSTOMER")
                .orElseThrow(() ->
                        new IllegalStateException("Role CUSTOMER is missing")
                );

        User u = new User();
        u.setRole(customer);
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(r.password()));
        u.setFirstName(r.firstName().trim());
        u.setLastName(r.lastName().trim());
        u.setPhone(r.phone());

        users.save(u);

        return issueTokens(u);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest r) {
        String email = r.email().trim().toLowerCase(Locale.ROOT);

        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        email, r.password()
                )
        );

        User u = users.findByEmail(email).orElseThrow();

        return issueTokens(u);
    }

    /**
     * Refresh token rotation:
     * Revoke the old refresh token and issue a new one.
     */
    @Override
    @Transactional
    public AuthResponse refresh(String rawToken) {
        RefreshToken t = refreshTokens.findByToken(hash(rawToken))
                .orElseThrow(() ->
                        new AppException(ErrorCode.INVALID_TOKEN)
                );

        if (t.getRevoked()
                || t.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        t.setRevoked(true);

        return issueTokens(t.getUser());
    }

    @Override
    @Transactional
    public void logout(String rawToken) {
        refreshTokens.findByToken(hash(rawToken))
                .ifPresent(t -> t.setRevoked(true));
    }

    private AuthResponse issueTokens(User u) {
        UserPrincipal principal = UserPrincipal.from(u);

        String raw = newRawToken();

        RefreshToken rt = new RefreshToken();
        rt.setUser(u);
        rt.setToken(hash(raw));
        rt.setExpiresAt(
                LocalDateTime.now().plusDays(props.refreshTtlDays())
        );

        refreshTokens.save(rt);

        UserInfo info = new UserInfo(
                u.getId(),
                u.getEmail(),
                u.getFirstName() + " " + u.getLastName(),
                u.getRole().getName()
        );

        return new AuthResponse(
                jwt.generateAccessToken(principal),
                raw,
                "Bearer",
                jwt.accessTtlSeconds(),
                info
        );
    }

    private String newRawToken() {
        byte[] b = new byte[32];
        random.nextBytes(b);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(b);
    }

    private static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(raw.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}