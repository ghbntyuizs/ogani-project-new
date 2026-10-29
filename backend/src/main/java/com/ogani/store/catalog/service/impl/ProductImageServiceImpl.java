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
            throw new AppException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "Product"
            );
        }

        return toResponses(
                images.findByProductIdOrderBySortOrderAscIdAsc(productId)
        );
    }

    @Override
    public List<ProductImageResponse> listBySlug(String slug) {
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

        return toResponses(
                images.findByProductIdOrderBySortOrderAscIdAsc(
                        product.getId()
                )
        );
    }

    @Override
    @Transactional
    public ProductImageResponse add(
            Long productId,
            ProductImageRequest request
    ) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new AppException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Product"
                ));

        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setImageUrl(request.imageUrl().trim());

        image.setSortOrder(
                request.sortOrder() != null
                        ? request.sortOrder()
                        : images.findMaxSortOrder(productId) + 1
        );

        ProductImage saved = images.save(image);

        syncThumbnail(product);

        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ProductImageResponse update(
            Long productId,
            Long imageId,
            ProductImageRequest request
    ) {
        ProductImage image = findImage(productId, imageId);

        image.setImageUrl(request.imageUrl().trim());

        if (request.sortOrder() != null) {
            image.setSortOrder(request.sortOrder());
        }

        syncThumbnail(image.getProduct());

        return mapper.toResponse(image);
    }

    @Override
    @Transactional
    public List<ProductImageResponse> reorder(
            Long productId,
            ProductImageOrderRequest request
    ) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new AppException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Product"
                ));

        List<ProductImage> current =
                images.findByProductIdOrderBySortOrderAscIdAsc(productId);

        List<Long> requested = request.imageIds();

        Set<Long> currentIds = current.stream()
                .map(ProductImage::getId)
                .collect(Collectors.toSet());

        // Phải chứa đầy đủ ID hiện tại, không thiếu,
        // không thừa và không lặp ID.
        if (requested.size() != currentIds.size()
                || !currentIds.equals(new HashSet<>(requested))) {

            throw AppException.withCustomMessage(
                    ErrorCode.VALIDATION_ERROR,
                    "imageIds must list every image of this product exactly once"
            );
        }

        Map<Long, ProductImage> byId = current.stream()
                .collect(Collectors.toMap(
                        ProductImage::getId,
                        Function.identity()
                ));

        // Chuẩn hóa thứ tự thành 1, 2, 3...
        for (int i = 0; i < requested.size(); i++) {
            byId.get(requested.get(i)).setSortOrder(i + 1);
        }

        syncThumbnail(product);

        return requested.stream()
                .map(byId::get)
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void delete(Long productId, Long imageId) {
        ProductImage image = findImage(productId, imageId);
        Product product = image.getProduct();

        images.delete(image);

        syncThumbnail(product);
    }

    /**
     * Quy tắc:
     * - Gallery còn ảnh: thumbnail = imageUrl của ảnh đầu tiên.
     * - Gallery rỗng: giữ nguyên thumbnail hiện tại.
     * Product đang được Hibernate quản lý trong transaction,
     * nên thay đổi thumbnail được lưu bằng dirty checking.
     */
    private void syncThumbnail(Product product) {
        /*
         * Đẩy các thay đổi đang chờ xuống database trước khi
         * truy vấn ảnh đầu tiên.
         *
         * Điều này quan trọng sau add, update, reorder và delete,
         * đặc biệt là delete vì truy vấn phải không còn thấy ảnh
         * vừa bị xóa.
         */
        images.flush();

        images.findFirstByProductIdOrderBySortOrderAscIdAsc(
                product.getId()
        ).ifPresent(first ->
                product.setThumbnailUrl(first.getImageUrl())
        );
    }

    private ProductImage findImage(
            Long productId,
            Long imageId
    ) {
        return images.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new AppException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Product image"
                ));
    }

    private List<ProductImageResponse> toResponses(
            List<ProductImage> list
    ) {
        return list.stream()
                .map(mapper::toResponse)
                .toList();
    }
}