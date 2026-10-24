package com.ogani.store.auth;

import com.ogani.store.auth.dto.*;
import com.ogani.store.auth.dto.request.LoginRequest;
import com.ogani.store.auth.dto.request.RefreshRequest;
import com.ogani.store.auth.dto.request.RegisterRequest;
import com.ogani.store.auth.dto.response.AuthResponse;
import com.ogani.store.auth.service.impl.AuthServiceImpl;
import com.ogani.store.common.dto.ApiResponse;
import com.ogani.store.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthServiceImpl authServiceImpl;

    public AuthController(AuthServiceImpl authServiceImpl) { this.authServiceImpl = authServiceImpl; }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest r) {
        return ApiResponse.ok("Registered", authServiceImpl.register(r));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest r) {
        return ApiResponse.ok(authServiceImpl.login(r));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest r) {
        return ApiResponse.ok(authServiceImpl.refresh(r.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest r) {
        authServiceImpl.logout(r.refreshToken());
        return ApiResponse.ok("Logged out", null);
    }

    @GetMapping("/me")
    public ApiResponse<UserInfo> me(@AuthenticationPrincipal UserPrincipal p) {
        return ApiResponse.ok(new UserInfo(p.getId(), p.getUsername(), null, p.getRole()));
    }
}
