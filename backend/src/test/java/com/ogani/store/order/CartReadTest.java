package com.ogani.store.order;

import com.ogani.store.order.entity.Cart;
import com.ogani.store.order.repository.*;
import com.ogani.store.order.service.impl.CartServiceImpl;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.user.entity.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CartReadTest {
    @Test void emptyCartReadDoesNotCreateRecords() {
        var carts = mock(CartRepository.class);
        var role = new Role(); role.setName("CUSTOMER");
        var user = new User(); user.setId(1L); user.setRole(role);
        when(carts.findByUserId(1L)).thenReturn(Optional.empty());
        when(carts.save(any())).thenAnswer(invocation -> {
            var cart = (Cart) invocation.getArgument(0);
            cart.setId(99L); return cart;
        });
        var result = new CartServiceImpl(carts, mock(CartItemRepository.class))
                .getCart(UserPrincipal.from(user));
        assertNull(result.cartId());
        assertTrue(result.items().isEmpty());
        assertEquals(0, result.total().signum());
        verify(carts, never()).save(any());
    }
}
