package com.ogani.store.catalog.service.impl;

import com.ogani.store.catalog.dto.request.category.CategoryRequest;
import com.ogani.store.catalog.dto.response.category.CategoryResponse;
import com.ogani.store.catalog.entity.Category;
import com.ogani.store.catalog.mapper.CategoryMapper;
import com.ogani.store.catalog.repository.CategoryRepository;
import com.ogani.store.catalog.service.interfaces.CategoryService;
import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import com.ogani.store.common.util.SlugUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categories;
    private final CategoryMapper categoryMapper;

    @Override
    public List<CategoryResponse> list() {
        return categories.findByActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();

        // Không có slug hoặc slug chỉ chứa khoảng trắng -> tự sinh từ tên.
        String rawSlug = request.slug() == null || request.slug().isBlank()
                ? name
                : request.slug();

        String slug = SlugUtil.slugify(rawSlug);

        // Ví dụ: tên chỉ có emoji/ký tự không tạo được slug hợp lệ.
        if (slug.isBlank()) {
            throw AppException.withCustomMessage(
                    ErrorCode.VALIDATION_ERROR,
                    "Category slug cannot be empty after normalization"
            );
        }

        if (categories.existsBySlug(slug)) {
            throw new AppException(
                    ErrorCode.RESOURCE_ALREADY_EXISTS,
                    "Category slug"
            );
        }

        Category parent = request.parentId() == null
                ? null
                : findActiveParent(request.parentId());

        Category category = new Category();
        category.setName(name);
        category.setSlug(slug);
        category.setImageUrl(normalizeOptional(request.imageUrl()));
        category.setParent(parent);
        category.setSortOrder(
                request.sortOrder() == null ? 0 : request.sortOrder()
        );
        category.setActive(true);

        Category saved = categories.save(category);

        return categoryMapper.toResponse(saved);
    }

    private Category findActiveParent(Integer parentId) {
        Category parent = categories.findById(parentId)
                .orElseThrow(() ->
                        new AppException(
                                ErrorCode.RESOURCE_NOT_FOUND,
                                "Parent category"
                        )
                );

        if (!Boolean.TRUE.equals(parent.getActive())) {
            throw AppException.withCustomMessage(
                    ErrorCode.VALIDATION_ERROR,
                    "Parent category must be active"
            );
        }

        return parent;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
