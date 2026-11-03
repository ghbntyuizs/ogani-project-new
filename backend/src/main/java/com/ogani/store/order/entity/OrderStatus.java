package com.ogani.store.order.entity;

public enum OrderStatus {
    PENDING, CONFIRMED, SHIPPING, COMPLETED, CANCELLED;

    public boolean canTransitionTo(OrderStatus next) {
        if(next==null) return false;
        return switch(this) {
            case PENDING -> next==CONFIRMED || next==CANCELLED;
            case CONFIRMED -> next==SHIPPING || next==CANCELLED;
            case SHIPPING -> next==COMPLETED;
            case COMPLETED,CANCELLED -> false;
        };
    }
}
