package com.ogani.store.order.controller;

import com.ogani.store.common.dto.common.ApiResponse;
import com.ogani.store.order.dto.request.order.CancelOrderRequest;
import com.ogani.store.order.dto.response.checkout.OrderResponse;
import com.ogani.store.order.service.OrderCancellationService;
import com.ogani.store.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name="Orders (customer)")
public class OrderCancellationController {
    private final OrderCancellationService cancellation;

    @PostMapping("/api/orders/{id}/cancel")
    @Operation(summary="Cancel your pending unpaid COD order; repeated cancellation does not restock twice")
    public ApiResponse<OrderResponse> cancel(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,@Valid @RequestBody(required=false) CancelOrderRequest request) {
        return ApiResponse.ok(cancellation.cancel(principal,id,request));
    }
}
