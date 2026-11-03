package com.ogani.store.order.mapper;

import com.ogani.store.order.dto.response.checkout.CheckoutLineResponse;
import com.ogani.store.order.dto.response.checkout.OrderResponse;
import com.ogani.store.order.dto.response.checkout.ShippingResponse;
import com.ogani.store.order.entity.Order;
import com.ogani.store.order.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderResponseMapper {
    private final OrderItemRepository items;

    public OrderResponse toResponse(Order order) {
        var lines=items.findByOrderIdOrderByIdAsc(order.getId()).stream().map(i -> new CheckoutLineResponse(
                i.getProduct()==null?null:i.getProduct().getId(),i.getProductName(),i.getUnitPrice(),i.getQuantity(),i.getLineTotal())).toList();
        return new OrderResponse(order.getId(),order.getOrderCode(),order.getFirstName(),order.getLastName(),order.getEmail(),
                new ShippingResponse(order.getShipRecipientName(),order.getPhone(),order.getShipCountry(),order.getShipAddress(),
                        order.getShipCity(),order.getShipState(),order.getShipPostcode()),
                order.getOrderNotes(),lines,order.getSubtotal(),order.getDiscountAmount(),order.getShippingFee(),order.getTotal(),
                order.getCurrency(),order.getPaymentMethod(),order.getPaymentStatus(),order.getOrderStatus(),order.getCreatedAt());
    }
}
