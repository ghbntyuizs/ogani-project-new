package com.ogani.store.user.controller;

import com.ogani.store.common.dto.common.ApiResponse;
import com.ogani.store.order.dto.response.checkout.SavedAddressResponse;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.user.dto.request.CreateAddressRequest;
import com.ogani.store.user.service.CustomerAddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
@Tag(name="Customer addresses")
public class CustomerAddressController {
    private final CustomerAddressService addresses;
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="Save a Vietnam address for the current customer; maximum 10, first address defaults automatically")
    public ApiResponse<SavedAddressResponse> create(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateAddressRequest request) {
        return ApiResponse.ok(addresses.create(principal,request));
    }

    @PutMapping("/{id}")
    @Operation(summary="Replace supported fields of an owned address; false preserves its current default, true switches default")
    public ApiResponse<SavedAddressResponse> update(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,@Valid @RequestBody CreateAddressRequest request) {
        return ApiResponse.ok(addresses.update(principal,id,request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary="Delete an owned address; oldest remaining address replaces a deleted default; order snapshots stay intact")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal principal,@PathVariable Long id) {
        addresses.delete(principal,id);
        return ApiResponse.ok("Address deleted",null);
    }

    @PatchMapping("/{id}/default")
    @Operation(summary="Select an owned default address atomically; no request body; repeating the same target is safe")
    public ApiResponse<SavedAddressResponse> setDefault(@AuthenticationPrincipal UserPrincipal principal,@PathVariable Long id) {
        return ApiResponse.ok(addresses.setDefault(principal,id));
    }
}
