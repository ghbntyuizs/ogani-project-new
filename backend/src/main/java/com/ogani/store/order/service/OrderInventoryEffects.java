package com.ogani.store.order.service;

import com.ogani.store.catalog.entity.Product;
import com.ogani.store.catalog.entity.ProductStatus;
import com.ogani.store.catalog.repository.ProductRepository;
import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import com.ogani.store.order.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.TreeMap;

@Component
@RequiredArgsConstructor
public class OrderInventoryEffects {
    private final OrderItemRepository items;
    private final ProductRepository products;
    private record Quantities(Map<Long,Long> products,int deletedLines) {}

    @Transactional(propagation=Propagation.MANDATORY)
    public void validateItems(Long orderId) { quantities(orderId,ErrorCode.ORDER_STATUS_CONFLICT); }

    @Transactional(propagation=Propagation.MANDATORY)
    public int restore(Long orderId) { return apply(orderId,true); }

    @Transactional(propagation=Propagation.MANDATORY)
    public int recordSale(Long orderId) { return apply(orderId,false); }

    // Caller already holds buyer/order locks; product entities must not be preloaded by item reads.
    private int apply(Long orderId,boolean restore) {
        ErrorCode errorCode=restore?ErrorCode.ORDER_CANCELLATION_NOT_ALLOWED:ErrorCode.ORDER_STATUS_CONFLICT;
        var lines=quantities(orderId,errorCode);int skipped=lines.deletedLines();
        Map<Long,Product> locked=new TreeMap<>();Map<Long,Integer> next=new TreeMap<>();
        for(var entry:lines.products().entrySet()) {
            Product product=products.findByIdForUpdate(entry.getKey()).orElse(null);
            if(product==null) { skipped++;continue; }
            int current=restore?product.getStockQuantity():product.getSoldCount();
            long value=current+entry.getValue();
            if(current<0 || value>Integer.MAX_VALUE) throw conflict(errorCode,restore
                    ?"Restocked inventory exceeds supported range":"Sold count exceeds supported range");
            locked.put(entry.getKey(),product);next.put(entry.getKey(),(int)value);
        }
        for(var entry:locked.entrySet()) {
            Product product=entry.getValue();
            if(restore) {
                boolean depleted=product.getStockQuantity()==0 && product.getStatus()==ProductStatus.OUT_OF_STOCK;
                product.setStockQuantity(next.get(entry.getKey()));
                if(depleted) product.setStatus(ProductStatus.ACTIVE);
            } else product.setSoldCount(next.get(entry.getKey()));
        }
        return skipped;
    }

    private Quantities quantities(Long orderId,ErrorCode errorCode) {
        var lines=items.findRestockLines(orderId);
        if(lines.isEmpty()) throw conflict(errorCode,"Order has no purchased items");
        Map<Long,Long> quantities=new TreeMap<>();int deleted=0;
        for(var line:lines) {
            if(line.getQuantity()==null || line.getQuantity()<=0) throw conflict(errorCode,"Order contains invalid quantities");
            if(line.getProductId()==null) { deleted++;continue; }
            long quantity=quantities.getOrDefault(line.getProductId(),0L)+line.getQuantity();
            if(quantity>Integer.MAX_VALUE) throw conflict(errorCode,errorCode==ErrorCode.ORDER_CANCELLATION_NOT_ALLOWED
                    ?"Restock quantity exceeds supported range":"Aggregate order quantity exceeds supported range");
            quantities.put(line.getProductId(),quantity);
        }
        return new Quantities(quantities,deleted);
    }

    private static AppException conflict(ErrorCode code,String message) { return AppException.withCustomMessage(code,message); }
}
