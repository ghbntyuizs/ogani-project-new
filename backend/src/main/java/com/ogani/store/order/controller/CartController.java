package com.ogani.store.order.controller;

import com.ogani.store.common.dto.common.ApiResponse;
import com.ogani.store.order.dto.request.cart.AddCartItemRequest;
import com.ogani.store.order.dto.request.cart.UpdateCartItemRequest;
import jakarta.validation.Valid;
import com.ogani.store.order.dto.response.CartResponse;
import com.ogani.store.order.service.interfaces.CartService;
import com.ogani.store.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping("/items")
    public ApiResponse<CartResponse> addItem(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AddCartItemRequest request) {
        return ApiResponse.ok(cartService.addItem(principal, request));
    }

    @GetMapping
    public ApiResponse<CartResponse> getCart(
            @AuthenticationPrincipal UserPrincipal principal) {

        return ApiResponse.ok(
                cartService.getCart(principal)
        );
    }

    @PutMapping("/items/{itemId}")
    public ApiResponse<CartResponse> updateItem(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        return ApiResponse.ok(cartService.updateItem(principal, itemId, request));
    }

    @DeleteMapping("/items/{itemId}")
    public ApiResponse<CartResponse> deleteItem(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long itemId) {
        return ApiResponse.ok(cartService.deleteItem(principal, itemId));
    }
}
