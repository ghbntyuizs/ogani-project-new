package com.ogani.store.catalog.service.interfaces;

import com.ogani.store.catalog.dto.response.CategoryResponse;

import java.util.List;

public interface CategoryService {

    List<CategoryResponse> list();
}