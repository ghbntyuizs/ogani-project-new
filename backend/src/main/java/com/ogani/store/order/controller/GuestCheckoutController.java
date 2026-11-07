package com.ogani.store.order.controller;

import com.ogani.store.common.dto.common.ApiResponse;
import com.ogani.store.order.dto.request.checkout.GuestCartRequest;
import com.ogani.store.order.dto.request.checkout.GuestCheckoutRequest;
import com.ogani.store.order.dto.response.checkout.CheckoutPreviewResponse;
import com.ogani.store.order.dto.response.checkout.OrderResponse;
import com.ogani.store.order.service.checkout.CheckoutPricing;
import com.ogani.store.order.service.checkout.GuestCheckoutService;
import com.ogani.store.order.service.checkout.GuestScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/guest")
@RequiredArgsConstructor
public class GuestCheckoutController {
    private final GuestScope scopes;
    private final GuestCheckoutService checkout;
    private final CheckoutPricing pricing;
    @PostMapping("/session")
    public ApiResponse<Map<String,String>> session(HttpServletRequest request,HttpServletResponse response){
        return ApiResponse.ok(Map.of("scope",scopes.initialize(request,response)));
    }
    @PostMapping("/cart")
    public ApiResponse<GuestCheckoutService.Basket> cart(@RequestBody GuestCartRequest request){return ApiResponse.ok(checkout.cart(request));}
    @PostMapping("/checkout/preview")
    @Transactional
    public ApiResponse<CheckoutPreviewResponse> preview(HttpServletRequest http,@RequestBody GuestCartRequest request){
        return ApiResponse.ok(pricing.guestSnapshot(scopes.require(http),request.items()).preview());
    }
    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<OrderResponse>> place(HttpServletRequest http,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody GuestCheckoutRequest request){
        var result=checkout.place(scopes.require(http),key,request);
        return ResponseEntity.status(result.replay()?200:201).body(ApiResponse.ok(result.order()));
    }
    @GetMapping("/orders/{id}")
    public ApiResponse<OrderResponse> order(HttpServletRequest http,@PathVariable Long id){return ApiResponse.ok(checkout.order(scopes.require(http),id));}
}
