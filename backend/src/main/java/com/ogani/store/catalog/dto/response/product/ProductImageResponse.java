package com.ogani.store.catalog.dto.response.product;

public record ProductImageResponse(Long id, Long productId, String imageUrl, Integer sortOrder) {}