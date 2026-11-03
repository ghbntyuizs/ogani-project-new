package com.ogani.store.order.service.checkout;

import com.ogani.store.catalog.entity.Product;
import com.ogani.store.catalog.entity.ProductStatus;
import com.ogani.store.catalog.repository.CategoryRepository;
import com.ogani.store.catalog.repository.ProductRepository;
import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.BusinessValidationException;
import com.ogani.store.common.exception.ErrorCode;
import com.ogani.store.order.dto.response.checkout.CheckoutLineResponse;
import com.ogani.store.order.dto.response.checkout.CheckoutPreviewResponse;
import com.ogani.store.order.entity.Cart;
import com.ogani.store.order.repository.CartItemRepository;
import com.ogani.store.order.repository.CartRepository;
import com.ogani.store.order.repository.CheckoutSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CheckoutPricing {
    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final CheckoutSettingsRepository settings;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    public record Purchase(Long itemId, Product product, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}
    public record Snapshot(Cart cart, List<Purchase> purchases, CheckoutPreviewResponse preview) {}

    // Caller holds the user lock. Never prefetch Product entities from the cart before locking them.
    public Snapshot snapshot(Long userId) {
        Cart cart = carts.findByUserId(userId).orElseThrow(() -> conflict("Cart is empty"));
        if (cart.getCoupon() != null) throw conflict("Coupons are not supported by COD checkout yet");
        var rows = cartItems.findCheckoutLines(cart.getId());
        if (rows.isEmpty()) throw conflict("Cart is empty");
        List<Purchase> purchases = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
        TreeSet<Integer> categoryIds = new TreeSet<>();
        List<String> fingerprint = new ArrayList<>();
        fingerprint.add(cart.getId().toString());
        for (var row : rows) {
            Product product = products.findByIdForUpdate(row.getProductId())
                    .orElseThrow(() -> itemConflict(row.getProductId(), "Product no longer exists"));
            if (row.getQuantity() == null || row.getQuantity() <= 0) throw itemConflict(product.getId(), "Invalid cart quantity");
            if (product.getStatus() != ProductStatus.ACTIVE) throw itemConflict(product.getId(), "Product is not active");
            if (row.getQuantity() > product.getStockQuantity()) throw itemConflict(product.getId(), "Insufficient stock");
            BigDecimal price = amount(product.getPrice());
            BigDecimal sale = product.getSalePrice() == null ? null : amount(product.getSalePrice());
            BigDecimal unit = sale != null && sale.compareTo(price) < 0 ? sale : price;
            BigDecimal line = amount(unit.multiply(BigDecimal.valueOf(row.getQuantity())));
            subtotal = amount(subtotal.add(line));
            purchases.add(new Purchase(row.getItemId(), product, row.getQuantity(), unit, line));
            categoryIds.add(product.getCategory().getId());
            fingerprint.addAll(List.of(row.getItemId().toString(), product.getId().toString(),
                    row.getQuantity().toString(), product.getName(), unit.toPlainString(), product.getCategory().getId().toString()));
        }
        // Keep categories stable through commit, after acquiring all products in ascending ID order.
        for (Integer id : categoryIds) {
            var category = categories.findByIdForCheckout(id).orElseThrow(() -> conflict("Category no longer exists"));
            if (!Boolean.TRUE.equals(category.getActive())) throw conflict("A product category is inactive");
        }
        Map<String,String> values = settings.findCheckoutSettings().stream()
                .collect(Collectors.toMap(s -> s.getSettingKey(), s -> s.getSettingValue()));
        BigDecimal fee;
        BigDecimal threshold;
        String currency;
        try {
            fee = amount(new BigDecimal(values.get("shipping_fee").strip()));
            threshold = amount(new BigDecimal(values.get("free_shipping_threshold").strip()));
            currency = values.get("currency").strip();
            if (!currency.matches("[A-Z]{3}")) throw new IllegalArgumentException();
            Currency.getInstance(currency);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw conflict("Shipping settings are missing or invalid");
        }
        BigDecimal shipping = subtotal.compareTo(threshold) >= 0 ? BigDecimal.ZERO.setScale(2) : fee;
        BigDecimal total = amount(subtotal.add(shipping));
        fingerprint.addAll(List.of(fee.toPlainString(), threshold.toPlainString(), currency));
        var items = purchases.stream().map(p -> new CheckoutLineResponse(p.product().getId(),
                p.product().getName(), p.unitPrice(), p.quantity(), p.lineTotal())).toList();
        var preview = new CheckoutPreviewResponse(items, subtotal, BigDecimal.ZERO.setScale(2), shipping,
                total, currency, CheckoutFingerprint.hash(fingerprint));
        return new Snapshot(cart, List.copyOf(purchases), preview);
    }

    private static BigDecimal amount(BigDecimal value) {
        try {
            if (value == null || value.signum() < 0 || value.compareTo(MAX_AMOUNT) > 0) throw new ArithmeticException();
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw conflict("Checkout monetary value is invalid or exceeds the supported range");
        }
    }

    private static AppException conflict(String message) {
        return AppException.withCustomMessage(ErrorCode.CHECKOUT_CONFLICT,message);
    }

    private static BusinessValidationException itemConflict(Long productId, String reason) {
        return new BusinessValidationException(ErrorCode.CHECKOUT_CONFLICT, Map.of("productId",productId,"reason",reason));
    }
}
