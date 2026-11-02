package com.ogani.store.catalog.repository;

import com.ogani.store.catalog.entity.ProductStatus;
import com.ogani.store.catalog.entity.WeightUnit;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Closed projection: do not fetch product description/information columns for lists. */
public interface ProductListView {
    Long getId();
    String getName();
    String getSlug();
    String getSku();
    CategoryView getCategory();
    BigDecimal getPrice();
    BigDecimal getSalePrice();
    BigDecimal getWeightValue();
    WeightUnit getWeightUnit();
    Integer getStockQuantity();
    String getThumbnailUrl();
    ProductStatus getStatus();
    Boolean getFeatured();
    BigDecimal getAvgRating();
    Integer getReviewCount();
    Integer getSoldCount();
    LocalDateTime getCreatedAt();

    interface CategoryView {
        Integer getId();
        String getName();
    }
}
