package com.ogani.store.order.service;

import com.ogani.store.common.dto.PageResponse;
import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import com.ogani.store.order.dto.response.order.OrderListResponse;
import com.ogani.store.order.entity.OrderStatus;
import com.ogani.store.order.repository.OrderRepository;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.user.entity.UserStatus;
import com.ogani.store.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderQueryService {
    private final OrderRepository orders;
    private final UserRepository users;

    @Transactional(readOnly=true)
    public PageResponse<OrderListResponse> list(UserPrincipal principal, String page, String size,
                                               String status, String sort) {
        if(principal==null) throw new AppException(ErrorCode.UNAUTHENTICATED);
        var owner=users.findById(principal.getId()).orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        if(owner.getStatus()!=UserStatus.ACTIVE) throw new AppException(ErrorCode.ACCOUNT_LOCKED);

        int pageNumber=integer(page,0,"page");
        int pageSize=integer(size,20,"size");
        if(pageSize<1 || pageSize>100) throw invalid("size must be between 1 and 100");
        if((long)pageNumber*pageSize>Integer.MAX_VALUE) throw invalid("Pagination offset exceeds the supported range");
        OrderStatus filter=null;
        if(status!=null) {
            try { filter=OrderStatus.valueOf(status); }
            catch(IllegalArgumentException ex) { throw invalid("status must be PENDING, CONFIRMED, SHIPPING, COMPLETED or CANCELLED"); }
        }
        String sorting=sort==null?"createdAt,desc":sort;
        if(!sorting.equals("createdAt,asc") && !sorting.equals("createdAt,desc")) {
            throw invalid("sort must be createdAt,asc or createdAt,desc");
        }
        Sort.Direction direction=sorting.endsWith("asc")?Sort.Direction.ASC:Sort.Direction.DESC;
        var pageable=PageRequest.of(pageNumber,pageSize,Sort.by(direction,"createdAt","id"));
        var result=filter==null?orders.findAllProjectedByUserId(owner.getId(),pageable)
                :orders.findAllProjectedByUserIdAndOrderStatus(owner.getId(),filter,pageable);
        return PageResponse.of(result.map(o -> new OrderListResponse(o.getId(),o.getOrderCode(),o.getOrderStatus(),
                o.getPaymentStatus(),o.getPaymentMethod(),o.getTotal(),o.getCurrency(),o.getCreatedAt())));
    }

    private static int integer(String raw,int fallback,String name) {
        if(raw==null) return fallback;
        if(!raw.matches("[0-9]+")) throw invalid(name+" must be a non-negative integer");
        try { return Integer.parseInt(raw); }
        catch(NumberFormatException ex) { throw invalid(name+" exceeds the supported integer range"); }
    }

    private static AppException invalid(String message) {
        return AppException.withCustomMessage(ErrorCode.VALIDATION_ERROR,message);
    }
}
