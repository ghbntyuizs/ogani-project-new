package com.ogani.store.catalog.dto.request.category;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

public record CategoryRequest(

        @NotBlank(message = "Category name is required")
        @Size(max = 100, message = "Category name must not exceed 100 characters")
        String name,

        @Size(max = 120, message = "Category slug must not exceed 120 characters")
        String slug,

        @Size(max = 255, message = "Image URL must not exceed 255 characters")
        String imageUrl,

        @Positive(message = "Parent category ID must be positive")
        Integer parentId,

        @Min(value = 0, message = "Sort order must not be negative")
        Integer sortOrder

) {}
