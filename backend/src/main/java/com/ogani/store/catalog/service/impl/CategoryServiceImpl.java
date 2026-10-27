package com.ogani.store.catalog.service.impl;

import com.ogani.store.catalog.dto.response.CategoryResponse;
import com.ogani.store.catalog.mapper.CategoryMapper;
import com.ogani.store.catalog.repository.CategoryRepository;
import com.ogani.store.catalog.service.interfaces.CategoryService;
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
}
