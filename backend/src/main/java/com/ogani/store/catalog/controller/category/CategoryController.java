package com.ogani.store.catalog.controller.category;

import com.ogani.store.catalog.dto.response.category.CategoryResponse;
import com.ogani.store.catalog.service.interfaces.CategoryService;
import com.ogani.store.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** Menu "All departments" + carousel danh mục của Ogani. */
@RestController
@RequestMapping("/api/categories")
@Tag(name = "Categories (public)")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ApiResponse<List<CategoryResponse>> list() {
        return ApiResponse.ok(categoryService.list());
    }
}