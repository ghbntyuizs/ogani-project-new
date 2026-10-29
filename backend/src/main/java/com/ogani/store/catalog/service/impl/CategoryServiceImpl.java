package com.ogani.store.catalog.service.impl;

import com.ogani.store.catalog.dto.request.category.CategoryRequest;
import com.ogani.store.catalog.dto.response.category.CategoryResponse;
import com.ogani.store.catalog.entity.Category;
import com.ogani.store.catalog.mapper.CategoryMapper;
import com.ogani.store.catalog.repository.CategoryRepository;
import com.ogani.store.catalog.repository.ProductRepository;
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
    private final ProductRepository products;

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

    @Override
    @Transactional
    public CategoryResponse update(Integer id, CategoryRequest request) {
        Category category = categories.findById(id)
                .orElseThrow(() ->
                        new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Category"));

        String name = request.name().trim();

        // PUT không truyền slug hoặc truyền chuỗi trắng -> giữ slug cũ.
        String slug = request.slug() == null || request.slug().isBlank()
                ? category.getSlug()
                : SlugUtil.slugify(request.slug());

        if (slug.isBlank()) {
            throw AppException.withCustomMessage(
                    ErrorCode.VALIDATION_ERROR,
                    "Category slug cannot be empty after normalization"
            );
        }

        // Cho phép giữ slug hiện tại, chỉ chặn slug của category khác.
        if (categories.existsBySlugAndIdNot(slug, id)) {
            throw new AppException(
                    ErrorCode.RESOURCE_ALREADY_EXISTS,
                    "Category slug"
            );
        }

        Category parent = request.parentId() == null
                ? null
                : findValidParent(id, request.parentId());

        category.setName(name);
        category.setSlug(slug);
        category.setImageUrl(normalizeOptional(request.imageUrl()));
        category.setParent(parent);
        category.setSortOrder(
                request.sortOrder() == null ? 0 : request.sortOrder()
        );

        // Không set active ở đây: trạng thái hiện tại được giữ nguyên.
        Category saved = categories.save(category);

        return categoryMapper.toResponse(saved);
    }
    @Override
    @Transactional
    public void delete(Integer id) {
        Category category = categories.findById(id)
                .orElseThrow(() ->
                        new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Category"));

        // DELETE được thiết kế idempotent:
        // gọi lại với danh mục đã inactive vẫn thành công.
        if (Boolean.FALSE.equals(category.getActive())) {
            return;
        }

        if (products.existsByCategory_Id(id)) {
            throw AppException.withCustomMessage(
                    ErrorCode.VALIDATION_ERROR,
                    "Cannot delete category because products still reference it"
            );
        }

        if (categories.existsByParent_IdAndActiveTrue(id)) {
            throw AppException.withCustomMessage(
                    ErrorCode.VALIDATION_ERROR,
                    "Cannot delete category because it has active child categories"
            );
        }

        category.setActive(false);
        categories.save(category);
    }

    private Category findValidParent(Integer categoryId, Integer parentId) {
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

        // Không cho category làm parent của chính nó hoặc hậu duệ của nó.
        Category current = parent;

        while (current != null) {
            if (current.getId().equals(categoryId)) {
                throw AppException.withCustomMessage(
                        ErrorCode.VALIDATION_ERROR,
                        "Category cannot be its own ancestor"
                );
            }

            current = current.getParent();
        }

        return parent;
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
