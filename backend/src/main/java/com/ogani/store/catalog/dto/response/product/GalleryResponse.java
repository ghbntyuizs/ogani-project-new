package com.ogani.store.catalog.dto.response.product;

import java.util.List;

public record GalleryResponse(Long productId, Long version, String thumbnailUrl,
                              List<ProductImageResponse> images) {}
