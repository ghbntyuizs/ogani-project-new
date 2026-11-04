package com.ogani.store.user.controller;

import com.ogani.store.common.dto.common.ApiResponse;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.user.dto.request.ChangePasswordRequest;
import com.ogani.store.user.dto.request.UpdateProfileRequest;
import com.ogani.store.user.dto.response.CustomerProfileResponse;
import com.ogani.store.user.service.CustomerAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
@Tag(name="Customer account")
public class CustomerAccountController {
    private final CustomerAccountService accounts;
    @PatchMapping
    @Operation(summary="Update the current customer's names/phone; omitted unchanged, phone null clears; protected fields rejected")
    public ApiResponse<CustomerProfileResponse> update(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(accounts.update(principal,request));
    }
    @PutMapping("/password")
    @Operation(summary="Change current customer password and revoke every access/refresh session; login required afterward")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        accounts.changePassword(principal,request);
        return ApiResponse.ok("Password changed; sign in again",null);
    }
}
