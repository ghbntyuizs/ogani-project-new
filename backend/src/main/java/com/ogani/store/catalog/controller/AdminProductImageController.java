package com.ogani.store.catalog.controller;

import com.ogani.store.catalog.dto.request.ProductImageOrderRequest;
import com.ogani.store.catalog.dto.request.ProductImageRequest;
import com.ogani.store.catalog.dto.response.ProductImageResponse;
import com.ogani.store.catalog.service.interfaces.ProductImageService;
import com.ogani.store.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Gallery ảnh của sản phẩm (trang product-form). ADMIN + STAFF đều được thao tác (theo rule /api/admin/**). */
@RestController
@RequestMapping("/api/admin/products/{productId}/images")
@Tag(name = "Product images (admin)")
@RequiredArgsConstructor
public class AdminProductImageController {

    private final ProductImageService productImageService;

    @GetMapping
    public ApiResponse<List<ProductImageResponse>> list(@PathVariable Long productId) {
        return ApiResponse.ok(productImageService.listByProductId(productId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductImageResponse> add(@PathVariable Long productId,
                                                 @Valid @RequestBody ProductImageRequest r) {
        return ApiResponse.ok("Image added", productImageService.add(productId, r));
    }

    @PutMapping("/{imageId}")
    public ApiResponse<ProductImageResponse> update(@PathVariable Long productId,
                                                    @PathVariable Long imageId,
                                                    @Valid @RequestBody ProductImageRequest r) {
        return ApiResponse.ok("Image updated", productImageService.update(productId, imageId, r));
    }

    /** Sắp xếp lại cả gallery 1 lần (kéo-thả ở FE). */
    @PutMapping("/order")
    public ApiResponse<List<ProductImageResponse>> reorder(@PathVariable Long productId,
                                                           @Valid @RequestBody ProductImageOrderRequest r) {
        return ApiResponse.ok("Images reordered", productImageService.reorder(productId, r));
    }

    @DeleteMapping("/{imageId}")
    public ApiResponse<Void> delete(@PathVariable Long productId, @PathVariable Long imageId) {
        productImageService.delete(productId, imageId);
        return ApiResponse.ok("Image deleted", null);
    }
}