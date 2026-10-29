package com.ogani.store.catalog.service.interfaces;

import com.ogani.store.catalog.dto.request.category.CategoryRequest;
import com.ogani.store.catalog.dto.response.category.CategoryResponse;

import java.util.List;

public interface CategoryService {

    List<CategoryResponse> list();

    CategoryResponse create(CategoryRequest request);

    CategoryResponse update(Integer id, CategoryRequest request);

    void delete(Integer id);

    List<CategoryResponse> adminList();

    CategoryResponse getById(Integer id);
}