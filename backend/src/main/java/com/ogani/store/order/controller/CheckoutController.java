package com.ogani.store.order.controller;

import com.ogani.store.common.dto.common.ApiResponse;
import com.ogani.store.order.dto.request.checkout.CheckoutRequest;
import com.ogani.store.order.dto.response.checkout.CheckoutPreviewResponse;
import com.ogani.store.order.dto.response.checkout.OrderResponse;
import com.ogani.store.order.dto.response.checkout.SavedAddressResponse;
import com.ogani.store.order.service.interfaces.CheckoutService;
import com.ogani.store.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class CheckoutController {
    private final CheckoutService checkout;

    @PostMapping("/api/checkout/preview")
    public ApiResponse<CheckoutPreviewResponse> preview(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(checkout.preview(principal));
    }

    @PostMapping("/api/checkout")
    public ResponseEntity<ApiResponse<OrderResponse>> place(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CheckoutRequest request) {
        var result=checkout.place(principal,key,request);
        return ResponseEntity.status(result.replay()?200:201).body(ApiResponse.ok(result.order()));
    }

    @GetMapping("/api/checkout/addresses")
    public ApiResponse<List<SavedAddressResponse>> addresses(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(checkout.addresses(principal));
    }

    @GetMapping("/api/orders/{id}")
    public ApiResponse<OrderResponse> order(@AuthenticationPrincipal UserPrincipal principal,@PathVariable Long id) {
        return ApiResponse.ok(checkout.getOrder(principal,id));
    }
}
