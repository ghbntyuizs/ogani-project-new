package com.ogani.store.catalog.service.interfaces;

import com.ogani.store.catalog.dto.request.product.ProductImageOrderRequest;
import com.ogani.store.catalog.dto.request.product.ProductImageRequest;
import com.ogani.store.catalog.dto.response.product.ProductImageResponse;
import com.ogani.store.catalog.dto.response.product.GalleryResponse;
import java.util.List;

public interface ProductImageService {
    GalleryResponse state(Long productId);
    GalleryResponse addToGallery(Long productId, ProductImageRequest request, Long version, boolean thumbnail);
    GalleryResponse removeFromGallery(Long productId, Long imageId, Long version);
    GalleryResponse reorderGallery(Long productId, ProductImageOrderRequest request, Long version);
    GalleryResponse selectThumbnail(Long productId, Long imageId, Long version);

    /** Admin: gallery của sản phẩm (kể cả sản phẩm HIDDEN). */
    List<ProductImageResponse> listByProductId(Long productId);

    /** Public: gallery theo slug; sản phẩm HIDDEN -> 404. */
    List<ProductImageResponse> listBySlug(String slug);

    ProductImageResponse add(Long productId, ProductImageRequest request, Long version);

    ProductImageResponse update(Long productId, Long imageId, ProductImageRequest request, Long version);

    List<ProductImageResponse> reorder(Long productId, ProductImageOrderRequest request, Long version);

    void delete(Long productId, Long imageId, Long version);
}
