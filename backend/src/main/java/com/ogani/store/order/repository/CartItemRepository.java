package com.ogani.store.order.repository;

import com.ogani.store.order.entity.CartItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CartItemRepository
        extends JpaRepository<CartItem, Long> {

    @EntityGraph(attributePaths = "product")
    List<CartItem> findAllByCartId(Long cartId);

    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    Optional<CartItem> findByIdAndCartUserId(Long id, Long userId);

    @Query("select i.id as itemId, i.product.id as productId, i.quantity as quantity from CartItem i where i.cart.id=:cartId order by i.product.id")
    List<CartCheckoutLine> findCheckoutLines(Long cartId);
}
