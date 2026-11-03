package com.ogani.store.order.repository;

public interface CartCheckoutLine {
    Long getItemId();
    Long getProductId();
    Integer getQuantity();
}
