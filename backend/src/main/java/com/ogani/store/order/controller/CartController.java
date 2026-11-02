package com.ogani.store.order.controller;

import com.ogani.store.common.dto.common.ApiResponse;
import com.ogani.store.order.dto.response.CartResponse;
import com.ogani.store.order.service.interfaces.CartService;
import com.ogani.store.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ApiResponse<CartResponse> getCart(
            @AuthenticationPrincipal UserPrincipal principal) {

        return ApiResponse.ok(
                cartService.getCart(principal)
        );
    }
}
