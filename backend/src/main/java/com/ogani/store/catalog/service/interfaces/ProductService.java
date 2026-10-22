package com.ogani.store.catalog.service.interfaces;

import com.ogani.store.catalog.dto.ProductFilter;
import com.ogani.store.catalog.dto.ProductSort;
import com.ogani.store.catalog.dto.request.ProductRequest;
import com.ogani.store.catalog.dto.response.ProductResponse;
import com.ogani.store.common.dto.PageResponse;

public interface ProductService {

    PageResponse<ProductResponse> search(
            ProductFilter filter,
            ProductSort sort,
            int page,
            int size,
            boolean publicView
    );

    ProductResponse getBySlug(String slug);

    ProductResponse getById(Long id);

    ProductResponse create(ProductRequest request);

    ProductResponse update(Long id, ProductRequest request);

    void delete(Long id);
}

