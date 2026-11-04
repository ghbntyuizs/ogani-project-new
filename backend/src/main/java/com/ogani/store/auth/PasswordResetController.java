package com.ogani.store.auth;

import com.ogani.store.auth.dto.request.ForgotPasswordRequest;
import com.ogani.store.auth.dto.request.ResetPasswordRequest;
import com.ogani.store.auth.reset.PasswordResetService;
import com.ogani.store.common.dto.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name="Password recovery")
public class PasswordResetController {
    private final PasswordResetService resets;
    @PostMapping("/forgot-password")
    @Operation(summary="Request a customer password reset email; same response for missing/ineligible/throttled email")
    public ApiResponse<Void> forgot(@Valid @RequestBody ForgotPasswordRequest body,HttpServletRequest request) {
        resets.forgot(body,request.getRemoteAddr());
        return ApiResponse.ok("If this email is eligible, a password reset link will be sent",null);
    }
    @PostMapping("/reset-password")
    @Operation(summary="Use a valid single-use reset token and revoke every old access/refresh session")
    public ApiResponse<Void> reset(@Valid @RequestBody ResetPasswordRequest body,HttpServletRequest request) {
        resets.reset(body,request.getRemoteAddr());
        return ApiResponse.ok("Password reset; sign in again",null);
    }
}
