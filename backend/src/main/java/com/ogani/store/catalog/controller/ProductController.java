package com.ogani.store.catalog.controller;

import com.ogani.store.catalog.dto.*;
import com.ogani.store.catalog.service.ProductService;
import com.ogani.store.common.dto.ApiResponse;
import com.ogani.store.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

/** API công khai cho trang Shop / Shop Details của Ogani. */
@RestController
@RequestMapping("/api/products")
@Tag(name = "Products (public)")
public class ProductController {
    private final ProductService service;

    public ProductController(ProductService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Danh sách sản phẩm: lọc theo danh mục/giá/sale/featured, sort=latest|price_asc|price_desc|rating|popular")
    public ApiResponse<PageResponse<ProductResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean onSale,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        ProductFilter f = new ProductFilter(keyword, categoryId, minPrice, maxPrice, onSale, featured, null);
        return ApiResponse.ok(service.search(f, ProductSort.from(sort), page, size, true));
    }

    @GetMapping("/{slug}")
    public ApiResponse<ProductResponse> detail(@PathVariable String slug) {
        return ApiResponse.ok(service.getBySlug(slug));
    }
}
