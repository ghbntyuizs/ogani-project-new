package com.ogani.store.catalog.dto.response;

public record ProductImageResponse(Long id, Long productId, String imageUrl, Integer sortOrder) {}