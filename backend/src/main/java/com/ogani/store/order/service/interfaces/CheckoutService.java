package com.ogani.store.order.service.interfaces;

import com.ogani.store.order.dto.request.checkout.CheckoutRequest;
import com.ogani.store.order.dto.response.checkout.CheckoutPreviewResponse;
import com.ogani.store.order.dto.response.checkout.OrderResponse;
import com.ogani.store.order.dto.response.checkout.SavedAddressResponse;
import com.ogani.store.security.UserPrincipal;
import java.util.List;

public interface CheckoutService {
    record Placement(OrderResponse order, boolean replay) {}
    CheckoutPreviewResponse preview(UserPrincipal principal);
    Placement place(UserPrincipal principal, String key, CheckoutRequest request);
    OrderResponse getOrder(UserPrincipal principal, Long id);
    List<SavedAddressResponse> addresses(UserPrincipal principal);
}
