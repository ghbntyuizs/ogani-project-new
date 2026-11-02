package com.ogani.store.catalog.controller.adminController;

import com.ogani.store.catalog.dto.request.product.ProductImageOrderRequest;
import com.ogani.store.catalog.dto.request.product.ProductImageRequest;
import com.ogani.store.catalog.dto.response.product.ProductImageResponse;
import com.ogani.store.catalog.dto.response.product.GalleryResponse;
import com.ogani.store.catalog.service.interfaces.ProductImageService;
import com.ogani.store.common.dto.common.ApiResponse;
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

    @GetMapping("/state")
    public ApiResponse<GalleryResponse> state(@PathVariable Long productId) {
        return ApiResponse.ok(productImageService.state(productId));
    }

    @PostMapping("/state")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GalleryResponse> addWithState(@PathVariable Long productId,
            @RequestHeader("If-Match") Long version,
            @RequestParam(defaultValue = "false") boolean thumbnail,
            @Valid @RequestBody ProductImageRequest request) {
        return ApiResponse.ok(productImageService.addToGallery(productId, request, version, thumbnail));
    }

    @DeleteMapping("/state/{imageId}")
    public ApiResponse<GalleryResponse> deleteWithState(@PathVariable Long productId, @PathVariable Long imageId,
            @RequestHeader("If-Match") Long version) {
        return ApiResponse.ok(productImageService.removeFromGallery(productId, imageId, version));
    }

    @PutMapping("/state/order")
    public ApiResponse<GalleryResponse> reorderWithState(@PathVariable Long productId,
            @RequestHeader("If-Match") Long version, @Valid @RequestBody ProductImageOrderRequest request) {
        return ApiResponse.ok(productImageService.reorderGallery(productId, request, version));
    }

    @PutMapping("/state/{imageId}/thumbnail")
    public ApiResponse<GalleryResponse> selectThumbnail(@PathVariable Long productId, @PathVariable Long imageId,
            @RequestHeader("If-Match") Long version) {
        return ApiResponse.ok(productImageService.selectThumbnail(productId, imageId, version));
    }

    @GetMapping
    public ApiResponse<List<ProductImageResponse>> list(@PathVariable Long productId) {
        return ApiResponse.ok(productImageService.listByProductId(productId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductImageResponse> add(@PathVariable Long productId,
            @RequestHeader("If-Match") Long version, @Valid @RequestBody ProductImageRequest r) {
        return ApiResponse.ok("Image added", productImageService.add(productId, r, version));
    }

    @PutMapping("/{imageId}")
    public ApiResponse<ProductImageResponse> update(@PathVariable Long productId,
                                                    @PathVariable Long imageId,
            @RequestHeader("If-Match") Long version, @Valid @RequestBody ProductImageRequest r) {
        return ApiResponse.ok("Image updated", productImageService.update(productId, imageId, r, version));
    }

    /** Sắp xếp lại cả gallery 1 lần (kéo-thả ở FE). */
    @PutMapping("/order")
    public ApiResponse<List<ProductImageResponse>> reorder(@PathVariable Long productId,
            @RequestHeader("If-Match") Long version, @Valid @RequestBody ProductImageOrderRequest r) {
        return ApiResponse.ok("Images reordered", productImageService.reorder(productId, r, version));
    }

    @DeleteMapping("/{imageId}")
    public ApiResponse<Void> delete(@PathVariable Long productId, @PathVariable Long imageId,
            @RequestHeader("If-Match") Long version) {
        productImageService.delete(productId, imageId, version);
        return ApiResponse.ok("Image deleted", null);
    }
}
