
package com.ogani.store.order.service.impl;

import com.ogani.store.catalog.entity.Product;
import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import com.ogani.store.order.dto.response.CartItemResponse;
import com.ogani.store.order.dto.response.CartResponse;
import com.ogani.store.order.entity.Cart;
import com.ogani.store.order.entity.CartItem;
import com.ogani.store.order.repository.CartItemRepository;
import com.ogani.store.order.repository.CartRepository;
import com.ogani.store.order.service.interfaces.CartService;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.user.entity.User;
import com.ogani.store.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CartServiceImpl implements CartService {

    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final UserRepository users;

    public CartServiceImpl(
            CartRepository carts,
            CartItemRepository cartItems,
            UserRepository users) {
        this.carts = carts;
        this.cartItems = cartItems;
        this.users = users;
    }

    @Override
    @Transactional
    public CartResponse getCart(UserPrincipal principal) {
        Long userId = principal.getId();

        // 1. Find the cart; if it doesn't exist, create an empty cart.
        Cart cart = carts.findByUserId(userId)
                .orElseGet(() -> {
                    // Token hợp lệ nhưng user không còn trong DB.
                    User user = users.findById(userId)
                            .orElseThrow(() ->
                                    new AppException(ErrorCode.UNAUTHENTICATED));

                    Cart newCart = new Cart();
                    newCart.setUser(user);

                    return carts.save(newCart);
                });

        // 2. Get the cart items.
        List<CartItem> entities =
                cartItems.findAllByCartId(cart.getId());

        // 3. Convert entities to DTOs.
        List<CartItemResponse> items = entities.stream()
                .map(this::toItemResponse)
                .toList();

        // 4. Calculate subtotal.
        BigDecimal subtotal = items.stream()
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 5. Coupons and shipping fees are not applied yet.
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal shippingFee = BigDecimal.ZERO;
        BigDecimal total = subtotal;
        String couponCode = null;

        return new CartResponse(
                cart.getId(),
                items,
                subtotal,
                couponCode,
                discountAmount,
                shippingFee,
                total
        );
    }

    private CartItemResponse toItemResponse(CartItem item) {
        Product product = item.getProduct();

        // Prioritize salePrice if it is lower than the original price.
        BigDecimal unitPrice = product.getSalePrice() != null
                && product.getSalePrice()
                .compareTo(product.getPrice()) < 0
                ? product.getSalePrice()
                : product.getPrice();

        BigDecimal lineTotal = unitPrice.multiply(
                BigDecimal.valueOf(item.getQuantity()));

        return new CartItemResponse(
                item.getId(),
                product.getId(),
                product.getName(),
                product.getThumbnailUrl(),
                unitPrice,
                item.getQuantity(),
                lineTotal
        );
    }
}