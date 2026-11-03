package com.ogani.store.order.service;

import com.ogani.store.common.dto.PageResponse;
import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import com.ogani.store.order.dto.request.order.AdminOrderFilter;
import com.ogani.store.order.dto.response.order.AdminOrderListResponse;
import com.ogani.store.order.repository.AdminOrderListView;
import com.ogani.store.order.repository.AdminOrderSpecifications;
import com.ogani.store.order.repository.OrderRepository;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.user.entity.UserStatus;
import com.ogani.store.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminOrderQueryService {
    private final UserRepository users;
    private final OrderRepository orders;

    @Transactional(readOnly=true)
    public PageResponse<AdminOrderListResponse> list(UserPrincipal principal,AdminOrderFilter filter) {
        if(principal==null) throw new AppException(ErrorCode.UNAUTHENTICATED);
        var actor=users.findById(principal.getId()).orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        if(actor.getStatus()!=UserStatus.ACTIVE) throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        if(actor.getRole()==null || !Set.of("ADMIN","STAFF").contains(actor.getRole().getName())) throw new AppException(ErrorCode.FORBIDDEN);
        var page=orders.findBy(AdminOrderSpecifications.from(filter),q -> q.as(AdminOrderListView.class).page(filter.pageable()));
        return PageResponse.of(page.map(o -> new AdminOrderListResponse(o.getId(),o.getOrderCode(),
                o.getFirstName()+" "+o.getLastName(),o.getEmail(),o.getOrderStatus(),o.getPaymentStatus(),o.getPaymentMethod(),
                o.getTotal(),o.getCurrency(),o.getCreatedAt())));
    }
}
