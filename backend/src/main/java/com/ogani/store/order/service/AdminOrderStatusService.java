package com.ogani.store.order.service;

import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import com.ogani.store.order.dto.request.order.UpdateOrderStatusRequest;
import com.ogani.store.order.dto.response.order.AdminOrderStatusResponse;
import com.ogani.store.order.entity.Order;
import com.ogani.store.order.entity.OrderStatus;
import com.ogani.store.order.entity.OrderStatusHistory;
import com.ogani.store.order.entity.PaymentMethod;
import com.ogani.store.order.entity.PaymentStatus;
import com.ogani.store.order.repository.OrderRepository;
import com.ogani.store.order.repository.OrderStatusHistoryRepository;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AdminOrderStatusService {
    private final AdminOrderAccess access;
    private final UserRepository users;
    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final OrderInventoryEffects inventory;

    @Transactional
    public AdminOrderStatusResponse update(UserPrincipal principal,Long id,UpdateOrderStatusRequest request) {
        var actor=access.requireActive(principal);
        if(id==null || id<=0 || request==null || request.status()==null || request.version()==null || request.version()<0
                || (request.note()!=null && request.note().length()>200)) throw new AppException(ErrorCode.VALIDATION_ERROR);
        var owner=orders.findOwnerById(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,"Order"));
        Long lockedOwner=owner.getUserId();
        // Never lock actor then a different buyer: cross-account staff actions could deadlock.
        if(lockedOwner!=null && users.findByIdForCartUpdate(lockedOwner).isEmpty()) lockedOwner=null;
        Order order=orders.findForStatusUpdate(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,"Order"));
        Long actualOwner=order.getUser()==null?null:order.getUser().getId();
        if(!Objects.equals(lockedOwner,actualOwner)) throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        OrderStatus from=order.getOrderStatus(),target=request.status();
        if(from==target) return response(order);
        if(!request.version().equals(order.getVersion())) throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        if(!from.canTransitionTo(target)) throw conflict("Invalid order status transition");
        validatePayment(order,target);
        int skipped=0;
        if(target==OrderStatus.CANCELLED) skipped=inventory.restore(id);
        else if(target==OrderStatus.COMPLETED) skipped=inventory.recordSale(id);
        else inventory.validateItems(id);

        order.setOrderStatus(target);
        var event=new OrderStatusHistory();event.setOrder(order);event.setFromStatus(from.name());event.setToStatus(target.name());event.setChangedBy(actor);
        String note=request.note()==null || request.note().isBlank()?"Staff changed order status":"Staff status change: "+request.note().strip();
        if(skipped>0) note+="; skipped: "+skipped;
        event.setNote(note);history.save(event);orders.flush();
        return response(order);
    }

    private void validatePayment(Order order,OrderStatus target) {
        var payment=order.getPaymentStatus();
        if(target==OrderStatus.CANCELLED) {
            if(payment==PaymentStatus.PAID) throw conflict("Refund paid orders before cancellation");
        } else if(target==OrderStatus.COMPLETED) {
            if(payment!=PaymentStatus.PAID) throw conflict("Payment must be PAID before completion");
        } else if(payment==PaymentStatus.REFUNDED || (order.getPaymentMethod()!=PaymentMethod.COD && payment!=PaymentStatus.PAID)) {
            throw conflict("Payment does not allow fulfillment");
        }
    }

    private AdminOrderStatusResponse response(Order order) {
        return new AdminOrderStatusResponse(order.getId(),order.getOrderCode(),order.getOrderStatus(),order.getPaymentStatus(),order.getVersion(),order.getUpdatedAt());
    }

    private static AppException conflict(String message) { return AppException.withCustomMessage(ErrorCode.ORDER_STATUS_CONFLICT,message); }
}
