package com.ogani.store.order.repository;

import java.math.BigDecimal;

public interface OrderItemSnapshotView {
    Long getItemId();
    Long getProductId();
    String getProductName();
    BigDecimal getUnitPrice();
    Integer getQuantity();
    BigDecimal getLineTotal();
}
