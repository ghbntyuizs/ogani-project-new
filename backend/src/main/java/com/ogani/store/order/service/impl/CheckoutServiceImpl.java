package com.ogani.store.order.service.impl;

import com.ogani.store.catalog.entity.ProductStatus;
import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import com.ogani.store.order.dto.request.checkout.CheckoutRequest;
import com.ogani.store.order.dto.request.checkout.DeliveryAddress;
import com.ogani.store.order.dto.response.checkout.*;
import com.ogani.store.order.entity.Order;
import com.ogani.store.order.entity.OrderItem;
import com.ogani.store.order.entity.OrderStatusHistory;
import com.ogani.store.order.entity.PaymentMethod;
import com.ogani.store.order.repository.CartItemRepository;
import com.ogani.store.order.repository.OrderRepository;
import com.ogani.store.order.repository.OrderItemRepository;
import com.ogani.store.order.repository.OrderStatusHistoryRepository;
import com.ogani.store.order.service.checkout.CheckoutFingerprint;
import com.ogani.store.order.service.checkout.CheckoutPricing;
import com.ogani.store.order.service.interfaces.CheckoutService;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.user.entity.User;
import com.ogani.store.user.entity.UserStatus;
import com.ogani.store.user.repository.CustomerAddressRepository;
import com.ogani.store.user.repository.UserRepository;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import static com.ogani.store.order.service.checkout.CheckoutFingerprint.text;

@Service
@RequiredArgsConstructor
@Transactional
public class CheckoutServiceImpl implements CheckoutService {
    private final UserRepository users;
    private final CustomerAddressRepository addressRepository;
    private final CheckoutPricing pricing;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final OrderStatusHistoryRepository history;
    private final CartItemRepository cartItems;
    private final JdbcTemplate jdbc;
    private final Validator validator;

    @Override public CheckoutPreviewResponse preview(UserPrincipal principal) {
        return pricing.snapshot(owner(principal).getId()).preview();
    }

    @Override public Placement place(UserPrincipal principal, String key, CheckoutRequest request) {
        User buyer = owner(principal);
        if (key == null || !key.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) throw invalid("Idempotency-Key must be a UUID");
        key = UUID.fromString(key).toString();
        if (request == null || !validator.validate(request).isEmpty()) throw invalid("Invalid checkout request");
        if (request.paymentMethod() != PaymentMethod.COD) throw invalid("Only COD is supported");
        if (text(request.couponCode()) != null) throw invalid("Coupons are not supported yet");
        if ((request.addressId() == null) == (request.deliveryAddress() == null)) throw invalid("Choose addressId or deliveryAddress, exclusively");
        String hash = requestHash(request);
        var existing = orders.findByUserIdAndCheckoutKey(buyer.getId(),key);
        if (existing.isPresent()) {
            if (!hash.equals(existing.get().getCheckoutRequestHash())) throw conflict("Idempotency-Key was used for a different request");
            return new Placement(response(existing.get()),true);
        }
        ShippingResponse shipping = resolveShipping(buyer,request);
        var snapshot = pricing.snapshot(buyer.getId());
        var quote = snapshot.preview();
        if (!quote.cartToken().equals(request.cartToken())) throw conflict("Cart, prices or shipping changed; preview checkout again");
        Order order = new Order();
        long sequence = jdbc.queryForObject("select nextval('checkout_order_code_seq')",Long.class);
        order.setOrderCode("COD-"+Long.toString(sequence,36).toUpperCase(Locale.ROOT));
        order.setUser(buyer);
        order.setCheckoutKey(key);
        order.setCheckoutRequestHash(hash);
        order.setCurrency(quote.currency());
        order.setFirstName(text(request.firstName())); order.setLastName(text(request.lastName()));
        order.setEmail(text(request.email())); order.setPhone(shipping.phone());
        order.setShipRecipientName(shipping.recipientName());
        order.setShipCountry(shipping.country()); order.setShipAddress(shipping.address());
        order.setShipCity(shipping.city()); order.setShipState(shipping.state()); order.setShipPostcode(shipping.postcode());
        order.setOrderNotes(text(request.orderNotes()));
        order.setSubtotal(quote.subtotal()); order.setShippingFee(quote.shippingFee());
        order.setDiscountAmount(quote.discountAmount()); order.setTotal(quote.total());
        order = orders.save(order);
        for (var purchase : snapshot.purchases()) {
            var product = purchase.product();
            OrderItem item = new OrderItem();
            item.setOrder(order); item.setProduct(product); item.setProductName(product.getName());
            item.setUnitPrice(purchase.unitPrice()); item.setQuantity(purchase.quantity()); item.setLineTotal(purchase.lineTotal());
            orderItems.save(item);
            int remaining = product.getStockQuantity()-purchase.quantity();
            product.setStockQuantity(remaining);
            if (remaining == 0) product.setStatus(ProductStatus.OUT_OF_STOCK);
        }
        OrderStatusHistory event = new OrderStatusHistory();
        event.setOrder(order); event.setToStatus("PENDING"); event.setChangedBy(buyer); event.setNote("COD order placed");
        history.save(event);
        cartItems.deleteAllById(snapshot.purchases().stream().map(CheckoutPricing.Purchase::itemId).toList());
        snapshot.cart().setUpdatedAt(LocalDateTime.now());
        orders.flush();
        return new Placement(response(order),false);
    }

    @Override public OrderResponse getOrder(UserPrincipal principal,Long id) {
        User buyer=owner(principal);
        if (id == null || id <= 0) throw invalid("Invalid order ID");
        return response(orders.findByIdAndUserId(id,buyer.getId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,"Order")));
    }

    @Override public List<SavedAddressResponse> addresses(UserPrincipal principal) {
        User buyer=owner(principal);
        return addressRepository.findByUserIdOrderByDefaultAddressDescIdAsc(buyer.getId()).stream()
                .map(a -> new SavedAddressResponse(a.getId(),a.getRecipientName(),a.getPhone(),a.getCountry(),
                        a.getAddressLine(),a.getCity(),a.getState(),a.getPostcode(),Boolean.TRUE.equals(a.getDefaultAddress()))).toList();
    }

    private User owner(UserPrincipal principal) {
        if (principal == null) throw new AppException(ErrorCode.UNAUTHENTICATED);
        User buyer=users.findByIdForCartUpdate(principal.getId()).orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        if (buyer.getStatus()!=UserStatus.ACTIVE) throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        return buyer;
    }

    private ShippingResponse resolveShipping(User buyer,CheckoutRequest request) {
        DeliveryAddress delivery=request.deliveryAddress();
        String recipient=text(request.firstName())+" "+text(request.lastName());
        if(request.addressId()!=null) {
            var saved=addressRepository.findByIdAndUserId(request.addressId(),buyer.getId())
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,"Address"));
            recipient=text(saved.getRecipientName());
            delivery=new DeliveryAddress(saved.getPhone(),saved.getCountry(),saved.getAddressLine(),saved.getCity(),saved.getState(),saved.getPostcode());
        }
        if (recipient==null || recipient.length()>121 || !validator.validate(delivery).isEmpty()) throw invalid("Invalid delivery address");
        return new ShippingResponse(recipient,text(delivery.phone()),text(delivery.country()),text(delivery.address()),
                text(delivery.city()),text(delivery.state()),text(delivery.postcode()));
    }

    private String requestHash(CheckoutRequest r) {
        var a=r.deliveryAddress();
        return CheckoutFingerprint.hash(Arrays.asList(r.cartToken(),text(r.firstName()),text(r.lastName()),text(r.email()),
                r.paymentMethod().name(),r.addressId()==null?null:r.addressId().toString(),text(r.orderNotes()),
                a==null?null:text(a.phone()),a==null?null:text(a.country()),a==null?null:text(a.address()),
                a==null?null:text(a.city()),a==null?null:text(a.state()),a==null?null:text(a.postcode())));
    }

    private OrderResponse response(Order o) {
        var items=orderItems.findByOrderIdOrderByIdAsc(o.getId()).stream().map(i -> new CheckoutLineResponse(
                i.getProduct()==null?null:i.getProduct().getId(),i.getProductName(),i.getUnitPrice(),i.getQuantity(),i.getLineTotal())).toList();
        return new OrderResponse(o.getId(),o.getOrderCode(),o.getFirstName(),o.getLastName(),o.getEmail(),
                new ShippingResponse(o.getShipRecipientName(),o.getPhone(),o.getShipCountry(),o.getShipAddress(),o.getShipCity(),o.getShipState(),o.getShipPostcode()),
                o.getOrderNotes(),items,o.getSubtotal(),o.getDiscountAmount(),o.getShippingFee(),o.getTotal(),o.getCurrency(),
                o.getPaymentMethod(),o.getPaymentStatus(),o.getOrderStatus(),o.getCreatedAt());
    }

    private static AppException invalid(String message) { return AppException.withCustomMessage(ErrorCode.VALIDATION_ERROR,message); }
    private static AppException conflict(String message) { return AppException.withCustomMessage(ErrorCode.CHECKOUT_CONFLICT,message); }
}
