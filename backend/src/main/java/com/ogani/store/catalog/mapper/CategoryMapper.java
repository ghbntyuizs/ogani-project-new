package com.ogani.store.catalog.mapper;

import com.ogani.store.catalog.dto.response.category.CategoryResponse;
import com.ogani.store.catalog.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getImageUrl(),
                category.getParent() == null
                        ? null
                        : category.getParent().getId(),
                category.getSortOrder(),
                category.getActive()
        );
    }
}