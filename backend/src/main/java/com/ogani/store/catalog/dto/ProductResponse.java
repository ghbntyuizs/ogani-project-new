package com.ogani.store.catalog.dto;

import com.ogani.store.catalog.entity.ProductStatus;
import com.ogani.store.catalog.entity.WeightUnit;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(
        Long id, String name, String slug, String sku,
        Integer categoryId, String categoryName,
        String shortDescription, String description, String information,
        BigDecimal price, BigDecimal salePrice, boolean onSale, Integer discountPercent,
        BigDecimal weightValue, WeightUnit weightUnit,
        Integer stockQuantity, boolean inStock,
        String shippingInfo, String thumbnailUrl, ProductStatus status, Boolean featured,
        BigDecimal avgRating, Integer reviewCount, Integer soldCount, LocalDateTime createdAt) {}
