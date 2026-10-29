package com.ogani.store.catalog.service.impl;

import com.ogani.store.catalog.dto.ProductFilter;
import com.ogani.store.catalog.dto.ProductSort;
import com.ogani.store.catalog.dto.request.product.ProductRequest;
import com.ogani.store.catalog.dto.response.product.ProductResponse;
import com.ogani.store.catalog.entity.Category;
import com.ogani.store.catalog.entity.Product;
import com.ogani.store.catalog.entity.ProductImage;
import com.ogani.store.catalog.entity.ProductStatus;
import com.ogani.store.catalog.mapper.ProductMapper;
import com.ogani.store.catalog.repository.CategoryRepository;
import com.ogani.store.catalog.repository.ProductImageRepository;
import com.ogani.store.catalog.repository.ProductRepository;
import com.ogani.store.catalog.repository.ProductSpecifications;
import com.ogani.store.catalog.service.interfaces.ProductService;
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
public class ProductServiceImpl implements ProductService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ProductImageRepository images;
    private final ProductMapper mapper;

    public ProductServiceImpl(
            ProductRepository products,
            CategoryRepository categories,
            ProductImageRepository images,
            ProductMapper mapper
    ) {
        this.products = products;
        this.categories = categories;
        this.images = images;
        this.mapper = mapper;
    }

    @Override
    public PageResponse<ProductResponse> search(
            ProductFilter filter,
            ProductSort sort,
            int page,
            int size,
            boolean publicView
    ) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                sort.sort()
        );

        Page<Product> result = products.findAll(
                ProductSpecifications.from(filter, publicView),
                pageable
        );

        return PageResponse.of(result.map(mapper::toResponse));
    }

    @Override
    public ProductResponse getBySlug(String slug) {
        Product product = products.findBySlug(slug)
                .orElseThrow(() -> new AppException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Product"
                ));

        if (product.getStatus() == ProductStatus.HIDDEN) {
            throw new AppException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "Product"
            );
        }

        return mapper.toResponse(product);
    }

    @Override
    public ProductResponse getById(Long id) {
        return mapper.toResponse(find(id));
    }

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        validate(request);

        String slug = (request.slug() == null
                || request.slug().isBlank())
                ? SlugUtil.slugify(request.name())
                : SlugUtil.slugify(request.slug());

        if (products.existsBySku(request.sku())) {
            throw new AppException(
                    ErrorCode.RESOURCE_ALREADY_EXISTS,
                    "SKU"
            );
        }

        if (products.existsBySlug(slug)) {
            throw new AppException(
                    ErrorCode.RESOURCE_ALREADY_EXISTS,
                    "Slug"
            );
        }

        Product product = mapper.toEntity(request);
        product.setSlug(slug);
        product.setSalePrice(request.salePrice());
        product.setCategory(findActiveCategory(request.categoryId()));

        return mapper.toResponse(products.save(product));
    }

    @Override
    @Transactional
    public ProductResponse update(
            Long id,
            ProductRequest request
    ) {
        validate(request);

        Product product = find(id);

        if (!product.getSku().equals(request.sku())
                && products.existsBySku(request.sku())) {

            throw new AppException(
                    ErrorCode.RESOURCE_ALREADY_EXISTS,
                    "SKU"
            );
        }

        String slug = (request.slug() == null
                || request.slug().isBlank())
                ? product.getSlug()
                : SlugUtil.slugify(request.slug());

        if (!product.getSlug().equals(slug)
                && products.existsBySlug(slug)) {

            throw new AppException(
                    ErrorCode.RESOURCE_ALREADY_EXISTS,
                    "Slug"
            );
        }

        mapper.update(request, product);

        product.setSlug(slug);
        product.setSalePrice(request.salePrice());
        product.setWeightValue(request.weightValue());

        if (!product.getCategory().getId()
                .equals(request.categoryId())) {

            product.setCategory(
                    findActiveCategory(request.categoryId())
            );
        }

        /*
         * Nếu gallery có ảnh, gallery là nguồn dữ liệu chuẩn
         * cho thumbnail.
         *
         * ProductMapper có thể đã copy thumbnailUrl từ request
         * vào product ở mapper.update(...), nên phải đồng bộ
         * lại sau khi mapper cập nhật.
         *
         * Nếu gallery rỗng, giữ hành vi hiện tại:
         * admin vẫn có thể thay đổi thumbnailUrl qua ProductRequest.
         */
        images.findFirstByProductIdOrderBySortOrderAscIdAsc(id)
                .map(ProductImage::getImageUrl)
                .ifPresent(product::setThumbnailUrl);

        return mapper.toResponse(product);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Product product = find(id);
        product.setStatus(ProductStatus.HIDDEN);
    }

    private void validate(ProductRequest request) {
        if (request.salePrice() != null
                && request.salePrice().compareTo(request.price()) > 0) {

            throw new AppException(
                    ErrorCode.INVALID_SALE_PRICE
            );
        }
    }

    private Product find(Long id) {
        return products.findById(id)
                .orElseThrow(() -> new AppException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Product"
                ));
    }

    /** Sản phẩm chỉ được gán vào category đang active (category đã xóa mềm thì không). */
    private Category findActiveCategory(Integer id) {
        Category category = findCategory(id);

        if (!Boolean.TRUE.equals(category.getActive())) {
            throw AppException.withCustomMessage(
                    ErrorCode.VALIDATION_ERROR,
                    "Category must be active"
            );
        }

        return category;
    }

    private Category findCategory(Integer id) {
        return categories.findById(id)
                .orElseThrow(() -> new AppException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Category"
                ));
    }
}