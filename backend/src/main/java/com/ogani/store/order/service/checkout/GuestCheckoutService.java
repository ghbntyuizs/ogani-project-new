package com.ogani.store.order.service.checkout;

import com.ogani.store.catalog.entity.ProductStatus;
import com.ogani.store.catalog.repository.ProductRepository;
import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import com.ogani.store.order.dto.request.checkout.GuestCartRequest;
import com.ogani.store.order.dto.request.checkout.GuestCheckoutRequest;
import com.ogani.store.order.dto.response.checkout.OrderResponse;
import com.ogani.store.order.entity.Order;
import com.ogani.store.order.entity.OrderItem;
import com.ogani.store.order.entity.OrderStatusHistory;
import com.ogani.store.order.mapper.OrderResponseMapper;
import com.ogani.store.order.repository.OrderRepository;
import com.ogani.store.order.repository.OrderItemRepository;
import com.ogani.store.order.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static com.ogani.store.order.service.checkout.CheckoutFingerprint.text;

@Service
@RequiredArgsConstructor
@Transactional
public class GuestCheckoutService {
    private final CheckoutPricing pricing;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderStatusHistoryRepository history;
    private final OrderResponseMapper mapper;
    private final JdbcTemplate jdbc;
    public record Placement(OrderResponse order,boolean replay){}
    public record CartLine(Long itemId,Long productId,String productName,String thumbnailUrl,BigDecimal unitPrice,int quantity,BigDecimal lineTotal,boolean available){}
    public record Basket(List<CartLine> items,BigDecimal subtotal,boolean canCheckout){}

    @Transactional(readOnly=true)
    public Basket cart(GuestCartRequest request) {
        var catalog=products.findAllById(request.items().stream().map(GuestCartRequest.Line::productId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(p->p.getId(),p->p));
        List<CartLine> lines=new ArrayList<>();BigDecimal subtotal=BigDecimal.ZERO;
        for(var row:request.items()){
            var p=catalog.get(row.productId());
            boolean visible=p!=null && p.getStatus()==ProductStatus.ACTIVE && Boolean.TRUE.equals(p.getCategory().getActive());
            boolean available=visible && row.quantity()<=p.getStockQuantity();
            BigDecimal unit=!visible?BigDecimal.ZERO:p.getSalePrice()!=null && p.getSalePrice().compareTo(p.getPrice())<0?p.getSalePrice():p.getPrice();
            BigDecimal line=unit.multiply(BigDecimal.valueOf(row.quantity()));
            if(available)subtotal=subtotal.add(line);
            lines.add(new CartLine(row.productId(),row.productId(),visible?p.getName():"Sản phẩm không còn khả dụng",visible?p.getThumbnailUrl():null,unit,row.quantity(),line,available));
        }
        return new Basket(lines,subtotal,!lines.isEmpty() && lines.stream().allMatch(CartLine::available));
    }
    public Placement place(String scope,String key,GuestCheckoutRequest request) {
        if(!scope.equals(request.scope()))throw new AppException(ErrorCode.FORBIDDEN);
        if(key==null || !key.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))
            throw AppException.withCustomMessage(ErrorCode.VALIDATION_ERROR,"Idempotency-Key must be a UUID");
        key=UUID.fromString(key).toString();
        // Serialize same browser scope, including distinct keys. Lock lives through commit/rollback.
        jdbc.queryForList("select pg_advisory_xact_lock(?)",Long.parseUnsignedLong(scope.substring(0,16),16));
        var a=request.deliveryAddress();
        var fields=new ArrayList<>(Arrays.asList(request.scope(),request.cartToken(),text(request.firstName()),text(request.lastName()),text(request.email()),
                request.paymentMethod(),a.recipientName(),a.phone(),a.city(),a.district(),a.ward(),a.address(),text(request.orderNotes())));
        for(var line:request.items())fields.add(line.productId()+":"+line.quantity());
        String hash=CheckoutFingerprint.hash(fields);
        var existing=orders.findByGuestScopeAndCheckoutKey(scope,key);
        if(existing.isPresent()) {
            if(!hash.equals(existing.get().getCheckoutRequestHash()))throw conflict("Idempotency-Key was used for a different request");
            return new Placement(mapper.toResponse(existing.get()),true);
        }
        var snapshot=pricing.guestSnapshot(scope,request.items());var quote=snapshot.preview();
        if(!quote.cartToken().equals(request.cartToken()))throw conflict("Cart, prices or shipping changed; preview again");
        Order order=new Order();
        long sequence=jdbc.queryForObject("select nextval('checkout_order_code_seq')",Long.class);
        order.setOrderCode("COD-"+Long.toString(sequence,36).toUpperCase(Locale.ROOT));
        order.setGuestScope(scope);order.setCheckoutKey(key);order.setCheckoutRequestHash(hash);
        order.setFirstName(text(request.firstName()));order.setLastName(text(request.lastName()));order.setEmail(text(request.email()));
        order.setPhone(a.phone());order.setShipRecipientName(a.recipientName());order.setShipCountry("Vietnam");
        order.setShipCity(a.city());order.setShipDistrict(a.district());order.setShipWard(a.ward());order.setShipAddress(a.address());
        order.setOrderNotes(text(request.orderNotes()));order.setCurrency(quote.currency());
        order.setSubtotal(quote.subtotal());order.setDiscountAmount(quote.discountAmount());order.setShippingFee(quote.shippingFee());order.setTotal(quote.total());
        order=orders.save(order);
        for(var purchase:snapshot.purchases()){
            var product=purchase.product();var item=new OrderItem();item.setOrder(order);item.setProduct(product);
            item.setProductName(product.getName());item.setQuantity(purchase.quantity());item.setUnitPrice(purchase.unitPrice());item.setLineTotal(purchase.lineTotal());items.save(item);
            product.setStockQuantity(product.getStockQuantity()-purchase.quantity());
            if(product.getStockQuantity()==0)product.setStatus(ProductStatus.OUT_OF_STOCK);
        }
        var event=new OrderStatusHistory();event.setOrder(order);event.setToStatus("PENDING");event.setNote("Guest COD order placed");history.save(event);
        orders.flush();return new Placement(mapper.toResponse(order),false);
    }
    @Transactional(readOnly=true)
    public OrderResponse order(String scope,Long id) {
        return mapper.toResponse(orders.findByIdAndGuestScope(id,scope).orElseThrow(()->new AppException(ErrorCode.RESOURCE_NOT_FOUND,"Order")));
    }
    private static AppException conflict(String message){return AppException.withCustomMessage(ErrorCode.CHECKOUT_CONFLICT,message);}
}
