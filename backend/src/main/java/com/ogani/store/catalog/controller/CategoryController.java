package com.ogani.store.catalog.controller;

import com.ogani.store.catalog.dto.CategoryResponse;
import com.ogani.store.catalog.repository.CategoryRepository;
import com.ogani.store.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** Menu "All departments" + carousel danh mục của Ogani. */
@RestController
@RequestMapping("/api/categories")
@Tag(name = "Categories (public)")
public class CategoryController {
    private final CategoryRepository categories;

    public CategoryController(CategoryRepository categories) { this.categories = categories; }

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<List<CategoryResponse>> list() {
        return ApiResponse.ok(categories.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(c -> new CategoryResponse(c.getId(), c.getName(), c.getSlug(), c.getImageUrl(),
                        c.getParent() == null ? null : c.getParent().getId()))
                .toList());
    }
}
