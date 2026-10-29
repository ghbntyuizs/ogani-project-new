package com.ogani.store.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;
import com.ogani.store.catalog.entity.*;
import com.ogani.store.catalog.entity.ProductStatus;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySku(String sku);

    boolean existsByCategory_IdAndStatusNot(
            Integer categoryId,
            ProductStatus status
    );
}

