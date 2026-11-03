package com.ogani.store.order.service;

import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import com.ogani.store.order.dto.request.order.UpdateOrderPaymentRequest;
import com.ogani.store.order.dto.response.order.AdminOrderPaymentResponse;
import com.ogani.store.order.entity.OrderStatus;
import com.ogani.store.order.entity.PaymentMethod;
import com.ogani.store.order.entity.PaymentStatus;
import com.ogani.store.order.repository.OrderRepository;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AdminOrderPaymentService {
    private final AdminOrderAccess access;
    private final UserRepository users;
    private final OrderRepository orders;

    @Transactional
    public AdminOrderPaymentResponse confirm(UserPrincipal principal,Long id,UpdateOrderPaymentRequest request) {
        var actor=access.requireActive(principal);
        if(id==null || id<=0 || request==null || request.paymentStatus()!=PaymentStatus.PAID
                || request.version()==null || request.version()<0) throw new AppException(ErrorCode.VALIDATION_ERROR);
        var owner=orders.findOwnerById(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,"Order"));
        Long lockedOwner=owner.getUserId();
        // Match checkout/cancellation/status lock order; never lock a separate actor first.
        if(lockedOwner!=null && users.findByIdForCartUpdate(lockedOwner).isEmpty()) lockedOwner=null;
        var order=orders.findForStatusUpdate(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,"Order"));
        Long actualOwner=order.getUser()==null?null:order.getUser().getId();
        if(!Objects.equals(lockedOwner,actualOwner)) throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        if(order.getPaymentMethod()!=PaymentMethod.COD) throw conflict("Only COD payments can be confirmed manually");
        if(order.getPaymentStatus()!=PaymentStatus.PAID) {
            if(!request.version().equals(order.getVersion())) throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
            if(order.getPaymentStatus()!=PaymentStatus.UNPAID || order.getOrderStatus()!=OrderStatus.SHIPPING)
                throw conflict("Only UNPAID COD orders in SHIPPING can be confirmed");
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setPaidAt(LocalDateTime.now());
            order.setPaymentConfirmedBy(actor);
            orders.flush();
        }
        return new AdminOrderPaymentResponse(order.getId(),order.getOrderCode(),order.getOrderStatus(),
                order.getPaymentStatus(),order.getVersion(),order.getPaidAt(),
                order.getPaymentConfirmedBy()==null?null:order.getPaymentConfirmedBy().getId(),order.getUpdatedAt());
    }

    private static AppException conflict(String message) {
        return AppException.withCustomMessage(ErrorCode.ORDER_PAYMENT_CONFLICT,message);
    }
}
