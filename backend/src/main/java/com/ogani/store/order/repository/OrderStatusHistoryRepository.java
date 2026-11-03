package com.ogani.store.order.repository;

import com.ogani.store.order.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {
    @Query("""
            select h.id as id, h.fromStatus as fromStatus, h.toStatus as toStatus, h.note as note,
                   h.changedAt as changedAt, actor.id as actorId,
                   actor.firstName as actorFirstName, actor.lastName as actorLastName
            from OrderStatusHistory h left join h.changedBy actor
            where h.order.id=:orderId order by h.changedAt, h.id
            """)
    List<OrderHistoryView> findTimelineByOrderId(Long orderId);
}
