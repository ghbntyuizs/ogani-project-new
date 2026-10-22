package com.ogani.store.catalog.dto;

import com.ogani.store.catalog.entity.ProductStatus;
import java.math.BigDecimal;

/** Các tham số lọc của trang Shop (Ogani: Department, Price range, Sale Off, search...). */
public record ProductFilter(String keyword, Integer categoryId, BigDecimal minPrice, BigDecimal maxPrice,
                            Boolean onSale, Boolean featured, ProductStatus status) {}
