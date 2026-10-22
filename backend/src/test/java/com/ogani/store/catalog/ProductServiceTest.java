package com.ogani.store.catalog;

import com.ogani.store.catalog.dto.ProductRequest;
import com.ogani.store.catalog.dto.ProductResponse;
import com.ogani.store.catalog.entity.Category;
import com.ogani.store.catalog.entity.Product;
import com.ogani.store.catalog.entity.ProductStatus;
import com.ogani.store.catalog.mapper.ProductMapperImpl;
import com.ogani.store.catalog.repository.CategoryRepository;
import com.ogani.store.catalog.repository.ProductRepository;
import com.ogani.store.catalog.service.ProductService;
import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock ProductRepository products;
    @Mock CategoryRepository categories;
    ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(products, categories, new ProductMapperImpl());
    }

    private ProductRequest request(String price, String salePrice) {
        return new ProductRequest("Organic Kale", null, "KAL-001", 2, "short", "desc", "info",
                new BigDecimal(price), salePrice == null ? null : new BigDecimal(salePrice),
                new BigDecimal("0.5"), null, 10, null, null, null, null);
    }

    @Test
    void createGeneratesSlugAndComputesDiscount() {
        Category cat = new Category();
        cat.setName("Vegetables");
        when(products.existsBySku("KAL-001")).thenReturn(false);
        when(products.existsBySlug("organic-kale")).thenReturn(false);
        when(categories.findById(2)).thenReturn(Optional.of(cat));
        when(products.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

        ProductResponse res = service.create(request("50.00", "40.00"));

        assertEquals("organic-kale", res.slug());
        assertTrue(res.onSale());
        assertEquals(20, res.discountPercent());
        assertEquals(ProductStatus.ACTIVE, res.status());      // default khi request không gửi status
    }

    @Test
    void createRejectsSalePriceAbovePrice() {
        AppException ex = assertThrows(AppException.class, () -> service.create(request("10.00", "12.00")));
        assertEquals(ErrorCode.INVALID_SALE_PRICE, ex.getErrorCode());
        assertEquals(400, ex.getHttpStatus().value());
        verify(products, never()).save(any());
    }

    @Test
    void createRejectsDuplicateSku() {
        when(products.existsBySku("KAL-001")).thenReturn(true);
        AppException ex = assertThrows(AppException.class, () -> service.create(request("10.00", null)));
        assertEquals(ErrorCode.RESOURCE_ALREADY_EXISTS, ex.getErrorCode());
        assertEquals(409, ex.getHttpStatus().value());
        assertEquals("SKU already exists", ex.getFormattedMessage());
    }

    @Test
    void getBySlugThrowsWhenMissingOrHidden() {
        when(products.findBySlug("nope")).thenReturn(Optional.empty());
        AppException missing = assertThrows(AppException.class, () -> service.getBySlug("nope"));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, missing.getErrorCode());
        assertEquals("Product not found", missing.getFormattedMessage());

        Product hidden = new Product();
        hidden.setStatus(ProductStatus.HIDDEN);
        when(products.findBySlug("secret")).thenReturn(Optional.of(hidden));
        AppException hiddenEx = assertThrows(AppException.class, () -> service.getBySlug("secret"));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, hiddenEx.getErrorCode());
    }
}