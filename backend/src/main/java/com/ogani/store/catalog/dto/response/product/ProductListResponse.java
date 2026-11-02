package com.ogani.store.catalog.dto.response.product;

import com.ogani.store.catalog.entity.ProductStatus;
import com.ogani.store.catalog.entity.WeightUnit;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Lightweight card/table data; full text is available from product detail. */
public record ProductListResponse(
        Long id, String name, String slug, String sku,
        Integer categoryId, String categoryName,
        BigDecimal price, BigDecimal salePrice, boolean onSale, Integer discountPercent,
        BigDecimal weightValue, WeightUnit weightUnit,
        Integer stockQuantity, boolean inStock, String thumbnailUrl, ProductStatus status,
        Boolean featured, BigDecimal avgRating, Integer reviewCount, Integer soldCount,
        LocalDateTime createdAt) {}
