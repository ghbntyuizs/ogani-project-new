package com.ogani.store.order.controller;

import com.ogani.store.common.dto.PageResponse;
import com.ogani.store.common.dto.common.ApiResponse;
import com.ogani.store.order.dto.response.order.OrderListResponse;
import com.ogani.store.order.service.OrderQueryService;
import com.ogani.store.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name="Orders (customer)")
public class OrderController {
    private final OrderQueryService orders;

    @GetMapping
    @Operation(summary="List your own orders; newest first by default, zero-based pagination")
    public ApiResponse<PageResponse<OrderListResponse>> list(@AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description="Zero-based page, default 0",example="0") @RequestParam(required=false) String page,
            @Parameter(description="Page size 1–100, default 20",example="20") @RequestParam(required=false) String size,
            @Parameter(schema=@Schema(allowableValues={"PENDING","CONFIRMED","SHIPPING","COMPLETED","CANCELLED"}))
            @RequestParam(required=false) String status,
            @Parameter(description="Default createdAt,desc",schema=@Schema(allowableValues={"createdAt,desc","createdAt,asc"}))
            @RequestParam(required=false) String sort) {
        return ApiResponse.ok(orders.list(principal,page,size,status,sort));
    }
}
