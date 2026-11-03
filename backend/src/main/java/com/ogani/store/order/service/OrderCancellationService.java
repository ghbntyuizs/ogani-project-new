package com.ogani.store.order.service;

import com.ogani.store.catalog.entity.Product;
import com.ogani.store.catalog.entity.ProductStatus;
import com.ogani.store.catalog.repository.ProductRepository;
import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import com.ogani.store.order.dto.request.order.CancelOrderRequest;
import com.ogani.store.order.dto.response.checkout.OrderResponse;
import com.ogani.store.order.entity.OrderStatus;
import com.ogani.store.order.entity.OrderStatusHistory;
import com.ogani.store.order.entity.PaymentMethod;
import com.ogani.store.order.entity.PaymentStatus;
import com.ogani.store.order.mapper.OrderResponseMapper;
import com.ogani.store.order.repository.OrderItemRepository;
import com.ogani.store.order.repository.OrderRepository;
import com.ogani.store.order.repository.OrderStatusHistoryRepository;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.user.entity.UserStatus;
import com.ogani.store.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class OrderCancellationService {
    private final UserRepository users;
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final ProductRepository products;
    private final OrderStatusHistoryRepository history;
    private final OrderResponseMapper mapper;

    @Transactional
    public OrderResponse cancel(UserPrincipal principal,Long id,CancelOrderRequest request) {
        if(principal==null) throw new AppException(ErrorCode.UNAUTHENTICATED);
        if(id==null || id<=0) throw new AppException(ErrorCode.VALIDATION_ERROR);
        String reason=request==null?null:request.reason();
        if(reason!=null && reason.length()>200) throw new AppException(ErrorCode.VALIDATION_ERROR);
        var buyer=users.findByIdForCartUpdate(principal.getId()).orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        if(buyer.getStatus()!=UserStatus.ACTIVE) throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        var order=orders.findOwnedByIdForUpdate(id,buyer.getId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,"Order"));
        if(order.getOrderStatus()==OrderStatus.CANCELLED) return mapper.toResponse(order);
        if(order.getOrderStatus()!=OrderStatus.PENDING || order.getPaymentMethod()!=PaymentMethod.COD
                || order.getPaymentStatus()!=PaymentStatus.UNPAID) throw conflict("Only PENDING, unpaid COD orders can be cancelled");

        var lines=items.findRestockLines(id);
        if(lines.isEmpty()) throw conflict("Order has no purchased items");
        Map<Long,Long> quantities=new TreeMap<>();
        int deletedLines=0;
        for(var line:lines) {
            if(line.getQuantity()==null || line.getQuantity()<=0) throw conflict("Order contains invalid quantities");
            if(line.getProductId()==null) { deletedLines++;continue; }
            long sum=quantities.getOrDefault(line.getProductId(),0L)+line.getQuantity();
            if(sum>Integer.MAX_VALUE) throw conflict("Restock quantity exceeds supported range");
            quantities.put(line.getProductId(),sum);
        }
        Map<Long,Product> locked=new TreeMap<>();
        Map<Long,Integer> restored=new TreeMap<>();
        // Same user-first protocol as checkout; acquire shared-product locks in ID order.
        for(var entry:quantities.entrySet()) {
            Product product=products.findByIdForUpdate(entry.getKey()).orElse(null);
            if(product==null) { deletedLines++;continue; }
            long stock=product.getStockQuantity().longValue()+entry.getValue();
            if(product.getStockQuantity()<0 || stock>Integer.MAX_VALUE) throw conflict("Restocked inventory exceeds supported range");
            locked.put(entry.getKey(),product);restored.put(entry.getKey(),(int)stock);
        }
        for(var entry:locked.entrySet()) {
            Product product=entry.getValue();
            boolean depleted=product.getStockQuantity()==0 && product.getStatus()==ProductStatus.OUT_OF_STOCK;
            product.setStockQuantity(restored.get(entry.getKey()));
            if(depleted) product.setStatus(ProductStatus.ACTIVE);
        }
        order.setOrderStatus(OrderStatus.CANCELLED);
        var event=new OrderStatusHistory();
        event.setOrder(order);event.setFromStatus("PENDING");event.setToStatus("CANCELLED");event.setChangedBy(buyer);
        String note=reason==null || reason.isBlank()?"Customer cancelled order":"Customer cancelled order: "+reason.strip();
        if(deletedLines>0) note+="; skipped: "+deletedLines;
        event.setNote(note);
        history.save(event);
        orders.flush();
        return mapper.toResponse(order);
    }

    private static AppException conflict(String message) {
        return AppException.withCustomMessage(ErrorCode.ORDER_CANCELLATION_NOT_ALLOWED,message);
    }
}
