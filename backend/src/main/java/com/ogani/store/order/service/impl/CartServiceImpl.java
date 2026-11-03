
package com.ogani.store.order.service.impl;

import com.ogani.store.catalog.entity.Product;
import com.ogani.store.catalog.entity.ProductStatus;
import com.ogani.store.catalog.repository.ProductRepository;
import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import com.ogani.store.order.dto.request.cart.AddCartItemRequest;
import com.ogani.store.order.dto.request.cart.UpdateCartItemRequest;
import com.ogani.store.order.dto.response.CartItemResponse;
import com.ogani.store.order.dto.response.CartResponse;
import com.ogani.store.order.entity.Cart;
import com.ogani.store.order.entity.CartItem;
import com.ogani.store.order.repository.CartItemRepository;
import com.ogani.store.order.repository.CartRepository;
import com.ogani.store.order.service.interfaces.CartService;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.user.entity.UserStatus;
import com.ogani.store.user.entity.User;
import com.ogani.store.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CartServiceImpl implements CartService {

    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final UserRepository users;
    private final ProductRepository products;

    public CartServiceImpl(
            CartRepository carts,
            CartItemRepository cartItems,
            UserRepository users,
            ProductRepository products) {
        this.carts = carts;
        this.cartItems = cartItems;
        this.users = users;
        this.products = products;
    }

    @Override
    @Transactional
    public CartResponse addItem(UserPrincipal principal, AddCartItemRequest request) {
        if (principal == null) throw new AppException(ErrorCode.UNAUTHENTICATED);
        if (request == null || request.productId() == null || request.productId() <= 0
                || request.quantity() == null || request.quantity() <= 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        User user = lockCartOwner(principal);
        Product product = lockPurchasableProduct(request.productId());

        Cart cart = carts.findByUserId(user.getId()).orElse(null);
        CartItem item = cart == null ? null
                : cartItems.findByCartIdAndProductId(cart.getId(), product.getId()).orElse(null);
        long quantity = (item == null ? 0L : item.getQuantity().longValue()) + request.quantity();
        if (quantity > product.getStockQuantity()) {
            throw new AppException(ErrorCode.INSUFFICIENT_STOCK, product.getName());
        }

        if (cart == null) {
            cart = new Cart();
            cart.setUser(user);
            cart = carts.save(cart);
        }
        if (item == null) {
            item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
        }
        item.setQuantity((int) quantity);
        // Item updates alone do not dirty the cart entity or advance its @UpdateTimestamp.
        cart.setUpdatedAt(LocalDateTime.now());
        cartItems.saveAndFlush(item);
        return getCart(principal);
    }

    @Override
    @Transactional
    public CartResponse updateItem(UserPrincipal principal, Long itemId, UpdateCartItemRequest request) {
        if (principal == null) throw new AppException(ErrorCode.UNAUTHENTICATED);
        if (itemId == null || itemId <= 0 || request == null
                || request.quantity() == null || request.quantity() < 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        User user = lockCartOwner(principal);
        CartItem item = cartItems.findByIdAndCartUserId(itemId, user.getId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Cart item"));
        if (request.quantity() == 0) return removeOwnedItem(principal, item);
        Product product = lockPurchasableProduct(item.getProduct().getId());
        if (request.quantity() > product.getStockQuantity()) {
            throw new AppException(ErrorCode.INSUFFICIENT_STOCK, product.getName());
        }

        item.setQuantity(request.quantity());
        item.getCart().setUpdatedAt(LocalDateTime.now());
        cartItems.saveAndFlush(item);
        return getCart(principal);
    }

    @Override
    @Transactional
    public CartResponse deleteItem(UserPrincipal principal, Long itemId) {
        if (principal == null) throw new AppException(ErrorCode.UNAUTHENTICATED);
        if (itemId == null || itemId <= 0) throw new AppException(ErrorCode.VALIDATION_ERROR);
        User user = lockCartOwner(principal);
        CartItem item = cartItems.findByIdAndCartUserId(itemId, user.getId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Cart item"));
        return removeOwnedItem(principal, item);
    }

    private CartResponse removeOwnedItem(UserPrincipal principal, CartItem item) {
        // Removal must work even when the product/category is no longer purchasable.
        item.getCart().setUpdatedAt(LocalDateTime.now());
        cartItems.delete(item);
        cartItems.flush();
        return getCart(principal);
    }

    private User lockCartOwner(UserPrincipal principal) {
        // All cart writers take the user lock first, including before the first cart exists.
        User user = users.findByIdForCartUpdate(principal.getId())
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        if (user.getStatus() != UserStatus.ACTIVE) throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        return user;
    }

    private Product lockPurchasableProduct(Long productId) {
        // Serialize validation with catalog writes without changing inventory or product version.
        Product product = products.findByIdForUpdate(productId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product"));
        if (product.getStatus() != ProductStatus.ACTIVE
                || !Boolean.TRUE.equals(product.getCategory().getActive())) {
            throw new AppException(ErrorCode.PRODUCT_UNAVAILABLE);
        }
        return product;
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(UserPrincipal principal) {
        Long userId = principal.getId();

        // Read an existing cart, or return an empty view without creating records.
        Cart cart = carts.findByUserId(userId).orElse(null);
        if (cart == null) {
            return new CartResponse(null, List.of(), BigDecimal.ZERO, null,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

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
