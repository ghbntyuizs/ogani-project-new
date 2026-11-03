package com.ogani.store.order.repository;

import com.ogani.store.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderIdOrderByIdAsc(Long orderId);

    @Query("select i.product.id as productId, i.quantity as quantity from OrderItem i where i.order.id=:orderId order by i.id")
    List<OrderRestockLine> findRestockLines(Long orderId);

    @Query("""
            select i.id as itemId, i.product.id as productId, i.productName as productName,
                   i.unitPrice as unitPrice, i.quantity as quantity, i.lineTotal as lineTotal
            from OrderItem i where i.order.id=:orderId order by i.id
            """)
    List<OrderItemSnapshotView> findSnapshotsByOrderId(Long orderId);
}
