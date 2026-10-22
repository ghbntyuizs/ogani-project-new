package com.ogani.store.catalog.service;

import com.ogani.store.catalog.dto.*;
import com.ogani.store.catalog.entity.*;
import com.ogani.store.catalog.mapper.ProductMapper;
import com.ogani.store.catalog.repository.CategoryRepository;
import com.ogani.store.catalog.repository.ProductRepository;
import com.ogani.store.catalog.repository.ProductSpecifications;
import com.ogani.store.common.dto.PageResponse;
import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import com.ogani.store.common.util.SlugUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService {
    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ProductMapper mapper;

    public ProductService(ProductRepository products, CategoryRepository categories, ProductMapper mapper) {
        this.products = products; this.categories = categories; this.mapper = mapper;
    }

    public PageResponse<ProductResponse> search(ProductFilter filter, ProductSort sort, int page, int size, boolean publicView) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort.sort());
        Page<Product> result = products.findAll(ProductSpecifications.from(filter, publicView), pr);
        return PageResponse.of(result.map(mapper::toResponse));
    }

    public ProductResponse getBySlug(String slug) {
        Product p = products.findBySlug(slug).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product"));
        if (p.getStatus() == ProductStatus.HIDDEN) throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product");
        return mapper.toResponse(p);
    }

    public ProductResponse getById(Long id) {
        return mapper.toResponse(find(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest r) {
        validate(r);
        String slug = (r.slug() == null || r.slug().isBlank()) ? SlugUtil.slugify(r.name()) : SlugUtil.slugify(r.slug());
        if (products.existsBySku(r.sku())) throw new AppException(ErrorCode.RESOURCE_ALREADY_EXISTS, "SKU");
        if (products.existsBySlug(slug)) throw new AppException(ErrorCode.RESOURCE_ALREADY_EXISTS, "Slug");

        Product p = mapper.toEntity(r);
        p.setSlug(slug);
        p.setSalePrice(r.salePrice());
        p.setCategory(findCategory(r.categoryId()));
        return mapper.toResponse(products.save(p));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest r) {
        validate(r);
        Product p = find(id);
        if (!p.getSku().equals(r.sku()) && products.existsBySku(r.sku()))
            throw new AppException(ErrorCode.RESOURCE_ALREADY_EXISTS, "SKU");
        String slug = (r.slug() == null || r.slug().isBlank()) ? p.getSlug() : SlugUtil.slugify(r.slug());
        if (!p.getSlug().equals(slug) && products.existsBySlug(slug))
            throw new AppException(ErrorCode.RESOURCE_ALREADY_EXISTS, "Slug");

        mapper.update(r, p);
        p.setSlug(slug);
        p.setSalePrice(r.salePrice());           // PUT = thay thế toàn bộ: cho phép xoá giá sale bằng null
        p.setWeightValue(r.weightValue());
        if (!p.getCategory().getId().equals(r.categoryId())) p.setCategory(findCategory(r.categoryId()));
        return mapper.toResponse(p);              // @Version tự tăng khi flush -> chống ghi đè đồng thời
    }

    @Transactional
    public void delete(Long id) {
        products.delete(find(id));
    }

    private void validate(ProductRequest r) {
        if (r.salePrice() != null && r.salePrice().compareTo(r.price()) > 0)
            throw new AppException(ErrorCode.INVALID_SALE_PRICE);
    }

    private Product find(Long id) {
        return products.findById(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product"));
    }

    private Category findCategory(Integer id) {
        return categories.findById(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Category"));
    }
}
