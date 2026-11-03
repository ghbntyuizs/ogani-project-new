package com.ogani.store.order.repository;

public interface AdminOrderListView extends OrderListView {
    String getFirstName();
    String getLastName();
    String getEmail();
}
