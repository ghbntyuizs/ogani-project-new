package com.ogani.store.catalog.service.impl;

import com.ogani.store.catalog.dto.request.product.ProductImageOrderRequest;
import com.ogani.store.catalog.dto.request.product.ProductImageRequest;
import com.ogani.store.catalog.dto.response.product.ProductImageResponse;
import com.ogani.store.catalog.dto.response.product.GalleryResponse;
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
import java.time.LocalDateTime;
import java.util.ArrayList;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductImageServiceImpl implements ProductImageService {

    private final ProductImageRepository images;
    private final ProductRepository products;
    private final ProductImageMapper mapper;
    private final ManagedUploadService uploads;

    @Override
    @Transactional
    public GalleryResponse state(Long productId) {
        return galleryState(lockProduct(productId));
    }

    @Override
    @Transactional
    public GalleryResponse addToGallery(Long productId, ProductImageRequest request, Long version, boolean thumbnail) {
        Product product = checkVersion(productId, version);
        ProductImageResponse added = addImage(productId, request);
        if (thumbnail) moveFirst(productId, added.id());
        return completeGallery(product);
    }

    @Override
    @Transactional
    public GalleryResponse removeFromGallery(Long productId, Long imageId, Long version) {
        Product product = checkVersion(productId, version);
        deleteImage(productId, imageId);
        return completeGallery(product);
    }

    @Override
    @Transactional
    public GalleryResponse reorderGallery(Long productId, ProductImageOrderRequest request, Long version) {
        Product product = checkVersion(productId, version);
        reorderImages(productId, request);
        return completeGallery(product);
    }

    @Override
    @Transactional
    public GalleryResponse selectThumbnail(Long productId, Long imageId, Long version) {
        Product product = checkVersion(productId, version);
        findImage(productId, imageId);
        moveFirst(productId, imageId);
        return completeGallery(product);
    }

    private void moveFirst(Long productId, Long imageId) {
        List<Long> order = new ArrayList<>();
        order.add(imageId);
        images.findByProductIdOrderBySortOrderAscIdAsc(productId).stream()
                .map(ProductImage::getId).filter(id -> !id.equals(imageId)).forEach(order::add);
        reorderImages(productId, new ProductImageOrderRequest(order));
    }

    private Product checkVersion(Long productId, Long version) {
        Product product = lockProduct(productId);
        if (version == null) throw AppException.withCustomMessage(ErrorCode.VALIDATION_ERROR, "Product version is required");
        if (!version.equals(product.getVersion())) throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        return product;
    }

    private Product lockProduct(Long productId) {
        return products.findByIdForUpdate(productId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product"));
    }

    private GalleryResponse completeGallery(Product product) {
        advanceVersion(product);
        return galleryState(product);
    }

    private void advanceVersion(Product product) {
        // Every gallery mutation invalidates stale forms, even if the first image did not change.
        product.setUpdatedAt(LocalDateTime.now());
        products.flush();
    }

    @Override
    @Transactional
    public ProductImageResponse add(Long productId, ProductImageRequest request, Long version) {
        Product product = checkVersion(productId, version);
        ProductImageResponse result = addImage(productId, request);
        advanceVersion(product); return result;
    }

    @Override
    @Transactional
    public ProductImageResponse update(Long productId, Long imageId, ProductImageRequest request, Long version) {
        Product product = checkVersion(productId, version);
        ProductImageResponse result = updateImage(productId, imageId, request);
        advanceVersion(product); return result;
    }

    @Override
    @Transactional
    public List<ProductImageResponse> reorder(Long productId, ProductImageOrderRequest request, Long version) {
        Product product = checkVersion(productId, version);
        List<ProductImageResponse> result = reorderImages(productId, request);
        advanceVersion(product); return result;
    }

    @Override
    @Transactional
    public void delete(Long productId, Long imageId, Long version) {
        Product product = checkVersion(productId, version);
        deleteImage(productId, imageId); advanceVersion(product);
    }

    private GalleryResponse galleryState(Product product) {
        return new GalleryResponse(product.getId(), product.getVersion(), product.getThumbnailUrl(),
                toResponses(images.findByProductIdOrderBySortOrderAscIdAsc(product.getId())));
    }

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

    private ProductImageResponse addImage(
            Long productId,
            ProductImageRequest request
    ) {
        Product product = products.getReferenceById(productId);

        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setImageUrl(request.imageUrl().trim());
        uploads.claim(image.getImageUrl());

        image.setSortOrder(
                request.sortOrder() != null
                        ? request.sortOrder()
                        : images.findMaxSortOrder(productId) + 1
        );

        ProductImage saved = images.save(image);

        syncThumbnail(product);

        return mapper.toResponse(saved);
    }

    private ProductImageResponse updateImage(
            Long productId,
            Long imageId,
            ProductImageRequest request
    ) {
        ProductImage image = findImage(productId, imageId);

        image.setImageUrl(request.imageUrl().trim());
        uploads.claim(image.getImageUrl());

        if (request.sortOrder() != null) {
            image.setSortOrder(request.sortOrder());
        }

        syncThumbnail(image.getProduct());

        return mapper.toResponse(image);
    }

    private List<ProductImageResponse> reorderImages(
            Long productId,
            ProductImageOrderRequest request
    ) {
        Product product = products.getReferenceById(productId);

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

    private void deleteImage(Long productId, Long imageId) {
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
