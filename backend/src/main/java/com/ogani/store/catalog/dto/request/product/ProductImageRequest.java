package com.ogani.store.catalog.dto.request.product;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** sortOrder bỏ trống -> tự xếp xuống cuối gallery. */
public record ProductImageRequest(
        @NotBlank @Size(max = 255) String imageUrl,
        @Min(0) Integer sortOrder) {}