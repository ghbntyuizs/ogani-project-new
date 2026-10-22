package com.ogani.store.auth.dto.response;

import com.ogani.store.auth.dto.UserInfo;

public record AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds, UserInfo user) {}
