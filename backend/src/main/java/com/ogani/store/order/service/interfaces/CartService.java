package com.ogani.store.order.service.interfaces;

import com.ogani.store.order.dto.response.CartResponse;
import com.ogani.store.order.dto.request.cart.AddCartItemRequest;
import com.ogani.store.security.UserPrincipal;

public interface CartService {

    CartResponse getCart(UserPrincipal principal);

    CartResponse addItem(UserPrincipal principal, AddCartItemRequest request);
}
