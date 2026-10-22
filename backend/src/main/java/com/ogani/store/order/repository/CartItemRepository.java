package com.ogani.store.order.repository;

import com.ogani.store.order.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CartItemRepository
        extends JpaRepository<CartItem, Long> {

    List<CartItem> findAllByCartId(Long cartId);
}