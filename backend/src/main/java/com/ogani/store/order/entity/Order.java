package com.ogani.store.order.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import com.ogani.store.user.entity.*;
import com.ogani.store.catalog.entity.*;
import com.ogani.store.order.entity.*;
import com.ogani.store.content.entity.*;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "order_code", nullable = false, length = 20, unique = true)
    private String orderCode;

    @Column(name = "checkout_key", length = 36)
    private String checkoutKey;

    @Column(name = "checkout_request_hash", length = 64)
    private String checkoutRequestHash;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "ship_recipient_name", nullable = false, length = 121)
    private String shipRecipientName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "first_name", nullable = false, length = 60)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 60)
    private String lastName;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "ship_country", nullable = false, length = 60)
    private String shipCountry;

    @Column(name = "ship_address", nullable = false, length = 255)
    private String shipAddress;

    @Column(name = "ship_city", nullable = false, length = 80)
    private String shipCity;

    @Column(name = "ship_district", length = 80)
    private String shipDistrict;

    @Column(name = "ship_ward", length = 80)
    private String shipWard;

    @Column(name = "ship_state", length = 80)
    private String shipState;

    @Column(name = "ship_postcode", length = 20)
    private String shipPostcode;

    @Column(name = "order_notes")
    private String orderNotes;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "shipping_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id")
    private Coupon coupon;

    @Column(name = "coupon_code", length = 30)
    private String couponCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod = PaymentMethod.COD;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_confirmed_by")
    private User paymentConfirmedBy;

    @Column(name = "transaction_ref", length = 100)
    private String transactionRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false)
    private OrderStatus orderStatus = OrderStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
