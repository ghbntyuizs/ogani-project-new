package com.ogani.store.order.dto.request.checkout;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.ogani.store.order.repository.CartCheckoutLine;
import tools.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Comparator;

public record GuestCartRequest(List<Line> items) {
    public record Line(Long productId, Integer quantity) implements CartCheckoutLine {
        public Long getItemId(){return productId;}
        public Long getProductId(){return productId;}
        public Integer getQuantity(){return quantity;}
    }
    public GuestCartRequest {
        if(items==null || items.size()>100) throw new IllegalArgumentException("At most 100 product types are supported");
        var ids=new HashSet<Long>();
        for(var line:items) if(line==null || line.productId()==null || line.productId()<=0 || line.quantity()==null || line.quantity()<=0 || !ids.add(line.productId()))
            throw new IllegalArgumentException("Unique products and positive integer quantities are required");
        items=items.stream().sorted(Comparator.comparing(Line::productId)).toList();
    }
    @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
    public static GuestCartRequest fromJson(JsonNode node) {
        var array=node==null?null:node.get("items");
        return fromItems(array);
    }
    public static GuestCartRequest fromItems(JsonNode array) {
        if(array==null || !array.isArray() || array.size()>100) throw new IllegalArgumentException("items must be an array of at most 100 products");
        var lines=new ArrayList<Line>();
        for(var item:array) {
            var id=item.get("productId");var qty=item.get("quantity");
            if(id==null || !id.isIntegralNumber() || !id.canConvertToLong() || qty==null || !qty.isIntegralNumber() || !qty.canConvertToInt())
                throw new IllegalArgumentException("Product ID and quantity must be whole integers");
            lines.add(new Line(id.longValue(),qty.intValue()));
        }
        return new GuestCartRequest(lines);
    }
}
