package com.ogani.store.catalog.controller.adminController;

import com.ogani.store.catalog.dto.request.category.CategoryRequest;
import com.ogani.store.catalog.dto.response.category.CategoryResponse;
import com.ogani.store.catalog.service.interfaces.CategoryService;
import com.ogani.store.common.dto.ApiResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/categories")
@Tag(name = "Categories (admin)")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CategoryResponse> create(
            @Valid @RequestBody CategoryRequest request
    ) {
        return ApiResponse.ok(
                "Category created",
                categoryService.create(request)
        );
    }
}
