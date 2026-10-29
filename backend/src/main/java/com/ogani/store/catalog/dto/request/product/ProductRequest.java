package com.ogani.store.catalog.dto.request.product;

import com.ogani.store.catalog.entity.ProductStatus;
import com.ogani.store.catalog.entity.WeightUnit;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 220) String slug,                       // bỏ trống -> tự sinh từ name
        @NotBlank @Size(max = 50) String sku,
        @NotNull Integer categoryId,
        @Size(max = 500) String shortDescription,
        String description,
        String information,
        @NotNull @DecimalMin("0.0") BigDecimal price,
        @DecimalMin("0.0") BigDecimal salePrice,            // null = không giảm giá
        @DecimalMin("0.0") BigDecimal weightValue,
        WeightUnit weightUnit,
        @NotNull @Min(0) Integer stockQuantity,
        @Size(max = 255) String shippingInfo,
        @Size(max = 255) String thumbnailUrl,
        ProductStatus status,
        Boolean featured) {}
