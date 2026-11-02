package com.ogani.store.catalog.repository;

import com.ogani.store.catalog.dto.ProductFilter;
import com.ogani.store.catalog.dto.ProductSort;
import com.ogani.store.catalog.entity.Product;
import com.ogani.store.catalog.entity.ProductStatus;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class ProductSpecifications {
    private ProductSpecifications() {}

    /** @param publicView true = khách chỉ thấy ACTIVE + OUT_OF_STOCK (ẩn HIDDEN). */
    public static Specification<Product> from(ProductFilter f, boolean publicView) {
        return from(f, publicView, null);
    }

    public static Specification<Product> from(ProductFilter f, boolean publicView, ProductSort sort) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (publicView) p.add(root.get("status").in(ProductStatus.ACTIVE, ProductStatus.OUT_OF_STOCK));
            else if (f.status() != null) p.add(cb.equal(root.get("status"), f.status()));

            if (f.keyword() != null && !f.keyword().isBlank())
                p.add(cb.like(cb.lower(root.get("name")), "%" + f.keyword().trim().toLowerCase(Locale.ROOT) + "%"));
            if (f.categoryId() != null) p.add(cb.equal(root.get("category").get("id"), f.categoryId()));

            // Giá lọc theo giá bán thực tế: sale_price nếu có, ngược lại price
            Expression<BigDecimal> effective = cb.coalesce(root.<BigDecimal>get("salePrice"), root.<BigDecimal>get("price"));
            if (query != null && query.getResultType() != Long.class
                    && (sort == ProductSort.PRICE_ASC || sort == ProductSort.PRICE_DESC)) {
                query.orderBy(sort == ProductSort.PRICE_ASC ? cb.asc(effective) : cb.desc(effective), cb.asc(root.get("id")));
            }
            if (f.minPrice() != null) p.add(cb.greaterThanOrEqualTo(effective, f.minPrice()));
            if (f.maxPrice() != null) p.add(cb.lessThanOrEqualTo(effective, f.maxPrice()));

            if (Boolean.TRUE.equals(f.onSale())) p.add(cb.isNotNull(root.get("salePrice")));
            if (Boolean.TRUE.equals(f.featured())) p.add(cb.isTrue(root.get("featured")));
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
