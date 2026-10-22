
package com.ogani.store.order.service;

import com.ogani.store.catalog.entity.Product;
import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import com.ogani.store.order.dto.CartItemResponse;
import com.ogani.store.order.dto.CartResponse;
import com.ogani.store.order.entity.Cart;
import com.ogani.store.order.entity.CartItem;
import com.ogani.store.order.repository.CartItemRepository;
import com.ogani.store.order.repository.CartRepository;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.user.entity.User;
import com.ogani.store.user.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CartService {

    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final UserRepository users;

    public CartService(
            CartRepository carts,
            CartItemRepository cartItems,
            UserRepository users) {
        this.carts = carts;
        this.cartItems = cartItems;
        this.users = users;
    }

    @Transactional
    public CartResponse getCart(UserPrincipal principal) {
        Long userId = principal.getId();

        // 1. Find the cart; if it doesn't exist, create an empty cart.
        Cart cart = carts.findByUserId(userId)
                .orElseGet(() -> {
                    // Token hợp lệ nhưng user không còn trong DB -> coi như chưa xác thực.
                    User user = users.findById(userId)
                            .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

                    Cart newCart = new Cart();
                    newCart.setUser(user);
                    return carts.save(newCart);
                });

        // 2. Take the list of products in the cart.
        List<CartItem> entities =
                cartItems.findAllByCartId(cart.getId());

        // 3. Convert entity to DTO and calculate subtotal.
        List<CartItemResponse> items = entities.stream()
                .map(this::toItemResponse)
                .toList();

        BigDecimal subtotal = items.stream()
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. Coupons and shipping fees are not applied for the time being.
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

        // Prioritize salePrice if the sale price is lower than the original price.
        BigDecimal unitPrice = product.getSalePrice() != null
                && product.getSalePrice().compareTo(product.getPrice()) < 0
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
