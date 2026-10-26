package com.ogani.store.catalog.service.impl;

import com.ogani.store.catalog.dto.request.ProductImageOrderRequest;
import com.ogani.store.catalog.dto.request.ProductImageRequest;
import com.ogani.store.catalog.dto.response.ProductImageResponse;
import com.ogani.store.catalog.entity.Product;
import com.ogani.store.catalog.entity.ProductImage;
import com.ogani.store.catalog.entity.ProductStatus;
import com.ogani.store.catalog.mapper.ProductImageMapper;
import com.ogani.store.catalog.repository.ProductImageRepository;
import com.ogani.store.catalog.repository.ProductRepository;
import com.ogani.store.catalog.service.interfaces.ProductImageService;
import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductImageServiceImpl implements ProductImageService {

    private final ProductImageRepository images;
    private final ProductRepository products;
    private final ProductImageMapper mapper;

    @Override
    public List<ProductImageResponse> listByProductId(Long productId) {
        if (!products.existsById(productId)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product");
        }
        return toResponses(images.findByProductIdOrderBySortOrderAscIdAsc(productId));
    }

    @Override
    public List<ProductImageResponse> listBySlug(String slug) {
        Product product = products.findBySlug(slug)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product"));
        if (product.getStatus() == ProductStatus.HIDDEN) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product");
        }
        return toResponses(images.findByProductIdOrderBySortOrderAscIdAsc(product.getId()));
    }

    @Override
    @Transactional
    public ProductImageResponse add(Long productId, ProductImageRequest request) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product"));

        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setImageUrl(request.imageUrl().trim());
        image.setSortOrder(request.sortOrder() != null
                ? request.sortOrder()
                : images.findMaxSortOrder(productId) + 1);

        return mapper.toResponse(images.save(image));
    }

    @Override
    @Transactional
    public ProductImageResponse update(Long productId, Long imageId, ProductImageRequest request) {
        ProductImage image = findImage(productId, imageId);
        image.setImageUrl(request.imageUrl().trim());
        if (request.sortOrder() != null) {
            image.setSortOrder(request.sortOrder());
        }
        return mapper.toResponse(image);
    }

    @Override
    @Transactional
    public List<ProductImageResponse> reorder(Long productId, ProductImageOrderRequest request) {
        if (!products.existsById(productId)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product");
        }

        List<ProductImage> current = images.findByProductIdOrderBySortOrderAscIdAsc(productId);
        List<Long> requested = request.imageIds();

        Set<Long> currentIds = current.stream().map(ProductImage::getId).collect(Collectors.toSet());
        if (requested.size() != currentIds.size() || !currentIds.equals(new HashSet<>(requested))) {
            throw AppException.withCustomMessage(ErrorCode.VALIDATION_ERROR,
                    "imageIds must list every image of this product exactly once");
        }

        Map<Long, ProductImage> byId = current.stream()
                .collect(Collectors.toMap(ProductImage::getId, Function.identity()));

        // Seed data đánh sortOrder từ 1, giữ cùng quy ước.
        for (int i = 0; i < requested.size(); i++) {
            byId.get(requested.get(i)).setSortOrder(i + 1);
        }

        return requested.stream().map(id -> mapper.toResponse(byId.get(id))).toList();
    }

    @Override
    @Transactional
    public void delete(Long productId, Long imageId) {
        images.delete(findImage(productId, imageId));
    }

    private ProductImage findImage(Long productId, Long imageId) {
        return images.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product image"));
    }

    private List<ProductImageResponse> toResponses(List<ProductImage> list) {
        return list.stream().map(mapper::toResponse).toList();
    }
}