package com.ogani.store.catalog.controller;

import com.ogani.store.catalog.dto.*;
import com.ogani.store.catalog.dto.request.ProductRequest;
import com.ogani.store.catalog.dto.response.ProductResponse;
import com.ogani.store.catalog.entity.ProductStatus;
import com.ogani.store.catalog.service.interfaces.ProductService;
import com.ogani.store.common.dto.ApiResponse;
import com.ogani.store.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

/** API cho trang Products của Spark Admin. ADMIN + STAFF được xem/sửa, chỉ ADMIN được xoá. */
@RestController
@RequestMapping("/api/admin/products")
@Tag(name = "Products (admin)")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    @GetMapping
    public ApiResponse<PageResponse<ProductResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ProductFilter f = new ProductFilter(keyword, categoryId, minPrice, maxPrice, null, null, status);
        return ApiResponse.ok(productService.search(f, ProductSort.from(sort), page, size, false));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> get(@PathVariable Long id) { return ApiResponse.ok(productService.getById(id)); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> create(@Valid @RequestBody ProductRequest r) {
        return ApiResponse.ok("Product created", productService.create(r));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductResponse> update(@PathVariable Long id, @Valid @RequestBody ProductRequest r) {
        return ApiResponse.ok("Product updated", productService.update(id, r));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ApiResponse.ok("Product deleted", null);
    }
}
