package com.ogani.store.order.entity;

/**
 * Trạng thái đơn hàng (khớp cột orders.order_status).
 * TODO (tự làm): thêm state machine – PENDING -> CONFIRMED -> SHIPPING -> COMPLETED,
 * PENDING/CONFIRMED -> CANCELLED; ví dụ method canTransitionTo(OrderStatus next) + unit test.
 */
public enum OrderStatus {
    PENDING, CONFIRMED, SHIPPING, COMPLETED, CANCELLED
}
