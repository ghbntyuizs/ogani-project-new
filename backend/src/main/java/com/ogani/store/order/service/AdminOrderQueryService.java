package com.ogani.store.order.service;

import com.ogani.store.common.dto.PageResponse;
import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import com.ogani.store.order.dto.request.order.AdminOrderFilter;
import com.ogani.store.order.dto.response.order.AdminOrderListResponse;
import com.ogani.store.order.dto.response.order.AdminOrderDetailResponse;
import com.ogani.store.order.dto.response.order.AdminOrderDetailResponse.Customer;
import com.ogani.store.order.dto.response.order.AdminOrderDetailResponse.Item;
import com.ogani.store.order.dto.response.order.AdminOrderDetailResponse.Payment;
import com.ogani.store.order.dto.response.order.AdminOrderDetailResponse.Coupon;
import com.ogani.store.order.dto.response.order.AdminOrderDetailResponse.Actor;
import com.ogani.store.order.dto.response.order.AdminOrderDetailResponse.History;
import com.ogani.store.order.dto.response.checkout.ShippingResponse;
import com.ogani.store.order.repository.AdminOrderListView;
import com.ogani.store.order.repository.AdminOrderSpecifications;
import com.ogani.store.order.repository.OrderRepository;
import com.ogani.store.order.repository.OrderItemRepository;
import com.ogani.store.order.repository.OrderStatusHistoryRepository;
import com.ogani.store.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
@RequiredArgsConstructor
public class AdminOrderQueryService {
    private final AdminOrderAccess access;
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderStatusHistoryRepository history;

    @Transactional(readOnly=true)
    public PageResponse<AdminOrderListResponse> list(UserPrincipal principal,AdminOrderFilter filter) {
        requireAdmin(principal);
        var page=orders.findBy(AdminOrderSpecifications.from(filter),q -> q.as(AdminOrderListView.class).page(filter.pageable()));
        return PageResponse.of(page.map(o -> new AdminOrderListResponse(o.getId(),o.getOrderCode(),
                o.getFirstName()+" "+o.getLastName(),o.getEmail(),o.getOrderStatus(),o.getPaymentStatus(),o.getPaymentMethod(),
                o.getTotal(),o.getCurrency(),o.getCreatedAt())));
    }

    // Keep header/items/history coherent without taking locks on checkout/status writers.
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public AdminOrderDetailResponse detail(UserPrincipal principal,Long id) {
        requireAdmin(principal);
        if(id==null || id<=0) throw new AppException(ErrorCode.VALIDATION_ERROR);
        var order=orders.findAdminDetailById(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,"Order"));
        var lines=items.findSnapshotsByOrderId(id).stream().map(i -> new Item(i.getItemId(),i.getProductId(),
                i.getProductName(),i.getUnitPrice(),i.getQuantity(),i.getLineTotal())).toList();
        var timeline=history.findTimelineByOrderId(id).stream().map(h -> new History(h.getId(),h.getFromStatus(),
                h.getToStatus(),h.getNote(),h.getActorId()==null?null:new Actor(h.getActorId(),
                h.getActorFirstName()+" "+h.getActorLastName()),h.getChangedAt())).toList();
        var coupon=order.getCouponId()==null && order.getCouponCode()==null?null:new Coupon(order.getCouponId(),order.getCouponCode());
        return new AdminOrderDetailResponse(order.getId(),order.getOrderCode(),order.getVersion(),
                new Customer(order.getUserId(),order.getFirstName(),order.getLastName(),order.getEmail(),order.getPhone()),lines,
                new ShippingResponse(order.getShipRecipientName(),order.getPhone(),order.getShipCountry(),order.getShipAddress(),
                        order.getShipCity(),order.getShipState(),order.getShipPostcode()),
                new Payment(order.getPaymentMethod(),order.getPaymentStatus(),order.getTransactionRef(),order.getPaidAt(),order.getPaymentConfirmedById()),coupon,
                order.getSubtotal(),order.getDiscountAmount(),order.getShippingFee(),order.getTotal(),order.getCurrency(),
                order.getOrderStatus(),order.getOrderNotes(),timeline,order.getCreatedAt(),order.getUpdatedAt());
    }

    private void requireAdmin(UserPrincipal principal) {
        access.requireActive(principal);
    }
}
