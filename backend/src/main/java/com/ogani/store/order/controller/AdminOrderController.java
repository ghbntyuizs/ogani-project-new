package com.ogani.store.order.controller;

import com.ogani.store.common.dto.PageResponse;
import com.ogani.store.common.dto.common.ApiResponse;
import com.ogani.store.order.dto.request.order.AdminOrderFilter;
import com.ogani.store.order.dto.response.order.AdminOrderListResponse;
import com.ogani.store.order.dto.response.order.AdminOrderDetailResponse;
import com.ogani.store.order.dto.request.order.UpdateOrderStatusRequest;
import com.ogani.store.order.dto.response.order.AdminOrderStatusResponse;
import com.ogani.store.order.service.AdminOrderStatusService;
import com.ogani.store.order.service.AdminOrderPaymentService;
import com.ogani.store.order.dto.request.order.UpdateOrderPaymentRequest;
import com.ogani.store.order.dto.response.order.AdminOrderPaymentResponse;
import jakarta.validation.Valid;
import com.ogani.store.order.service.AdminOrderQueryService;
import com.ogani.store.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@Tag(name="Orders (admin/staff)")
public class AdminOrderController {
    private final AdminOrderQueryService orders;
    private final AdminOrderStatusService statuses;
    private final AdminOrderPaymentService payments;

    @GetMapping
    @Operation(summary="List all orders; search historical order code/customer name/email and combine filters")
    public ApiResponse<PageResponse<AdminOrderListResponse>> list(@AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description="Case-insensitive literal substring, max200 characters") @RequestParam(required=false) String keyword,
            @Parameter(schema=@Schema(allowableValues={"PENDING","CONFIRMED","SHIPPING","COMPLETED","CANCELLED"})) @RequestParam(required=false) String status,
            @Parameter(schema=@Schema(allowableValues={"UNPAID","PAID","REFUNDED"})) @RequestParam(required=false) String paymentStatus,
            @Parameter(description="Inclusive date in createdAt representation, yyyy-MM-dd",example="2026-11-01") @RequestParam(required=false) String dateFrom,
            @Parameter(description="Inclusive date in createdAt representation, yyyy-MM-dd",example="2026-11-03") @RequestParam(required=false) String dateTo,
            @Parameter(description="Zero-based page, default0",example="0") @RequestParam(required=false) String page,
            @Parameter(description="Page size1–100, default20",example="20") @RequestParam(required=false) String size,
            @Parameter(description="Default createdAt,desc",schema=@Schema(allowableValues={"createdAt,desc","createdAt,asc"})) @RequestParam(required=false) String sort) {
        return ApiResponse.ok(orders.list(principal,AdminOrderFilter.parse(keyword,status,paymentStatus,dateFrom,dateTo,page,size,sort)));
    }

    @GetMapping("/{id}")
    @Operation(summary="Order snapshots, payment/coupon and chronological status history; Admin/Staff only")
    public ApiResponse<AdminOrderDetailResponse> detail(@AuthenticationPrincipal UserPrincipal principal,@PathVariable Long id) {
        return ApiResponse.ok(orders.detail(principal,id));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary="Change order status with current version; completion requires PAID")
    public ApiResponse<AdminOrderStatusResponse> updateStatus(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,@Valid @RequestBody UpdateOrderStatusRequest request) {
        return ApiResponse.ok(statuses.update(principal,id,request));
    }

    @PatchMapping("/{id}/payment-status")
    @Operation(summary="Confirm COD payment as PAID while SHIPPING; requires current version; retries preserve audit")
    public ApiResponse<AdminOrderPaymentResponse> confirmPayment(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,@Valid @RequestBody UpdateOrderPaymentRequest request) {
        return ApiResponse.ok(payments.confirm(principal,id,request));
    }
}
