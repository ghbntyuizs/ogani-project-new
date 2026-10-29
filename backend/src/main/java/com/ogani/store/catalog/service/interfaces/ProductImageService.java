package com.ogani.store.catalog.service.interfaces;

import com.ogani.store.catalog.dto.request.product.ProductImageOrderRequest;
import com.ogani.store.catalog.dto.request.product.ProductImageRequest;
import com.ogani.store.catalog.dto.response.product.ProductImageResponse;
import java.util.List;

public interface ProductImageService {

    /** Admin: gallery của sản phẩm (kể cả sản phẩm HIDDEN). */
    List<ProductImageResponse> listByProductId(Long productId);

    /** Public: gallery theo slug; sản phẩm HIDDEN -> 404. */
    List<ProductImageResponse> listBySlug(String slug);

    ProductImageResponse add(Long productId, ProductImageRequest request);

    ProductImageResponse update(Long productId, Long imageId, ProductImageRequest request);

    List<ProductImageResponse> reorder(Long productId, ProductImageOrderRequest request);

    void delete(Long productId, Long imageId);
}