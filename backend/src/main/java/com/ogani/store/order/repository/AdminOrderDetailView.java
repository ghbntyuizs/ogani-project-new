package com.ogani.store.order.repository;

import com.ogani.store.order.entity.OrderStatus;
import com.ogani.store.order.entity.PaymentMethod;
import com.ogani.store.order.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface AdminOrderDetailView {
    Long getId();
    String getOrderCode();
    Long getVersion();
    Long getUserId();
    String getFirstName();
    String getLastName();
    String getEmail();
    String getPhone();
    String getShipRecipientName();
    String getShipCountry();
    String getShipAddress();
    String getShipCity();
    String getShipState();
    String getShipPostcode();
    String getOrderNotes();
    BigDecimal getSubtotal();
    BigDecimal getDiscountAmount();
    BigDecimal getShippingFee();
    BigDecimal getTotal();
    String getCurrency();
    Long getCouponId();
    String getCouponCode();
    PaymentMethod getPaymentMethod();
    PaymentStatus getPaymentStatus();
    String getTransactionRef();
    OrderStatus getOrderStatus();
    LocalDateTime getCreatedAt();
    LocalDateTime getUpdatedAt();
}
