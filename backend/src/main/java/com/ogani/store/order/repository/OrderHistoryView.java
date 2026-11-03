package com.ogani.store.order.repository;

import java.time.LocalDateTime;

public interface OrderHistoryView {
    Long getId();
    String getFromStatus();
    String getToStatus();
    String getNote();
    LocalDateTime getChangedAt();
    Long getActorId();
    String getActorFirstName();
    String getActorLastName();
}
