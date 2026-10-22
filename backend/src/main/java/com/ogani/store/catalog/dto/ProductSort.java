package com.ogani.store.catalog.dto;

import org.springframework.data.domain.Sort;

/** Whitelist cột sort: tránh client truyền sort=bất_kỳ_field gây lỗi / lộ cấu trúc entity. */
public enum ProductSort {
    LATEST(Sort.by(Sort.Direction.DESC, "createdAt")),
    PRICE_ASC(Sort.by(Sort.Direction.ASC, "price")),
    PRICE_DESC(Sort.by(Sort.Direction.DESC, "price")),
    RATING(Sort.by(Sort.Direction.DESC, "avgRating")),
    POPULAR(Sort.by(Sort.Direction.DESC, "soldCount"));

    private final Sort sort;
    ProductSort(Sort sort) { this.sort = sort; }
    public Sort sort() { return sort; }

    public static ProductSort from(String value) {
        if (value == null || value.isBlank()) return LATEST;
        try { return valueOf(value.trim().toUpperCase()); } catch (IllegalArgumentException e) { return LATEST; }
    }
}
