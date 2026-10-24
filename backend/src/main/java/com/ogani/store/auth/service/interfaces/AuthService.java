package com.ogani.store.auth.service.interfaces;

import com.ogani.store.auth.dto.request.LoginRequest;
import com.ogani.store.auth.dto.request.RegisterRequest;
import com.ogani.store.auth.dto.response.AuthResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(String rawToken);

    void logout(String rawToken);
}
