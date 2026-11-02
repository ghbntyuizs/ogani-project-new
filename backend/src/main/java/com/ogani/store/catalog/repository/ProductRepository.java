package com.ogani.store.catalog.repository;

import com.ogani.store.catalog.entity.*;
import com.ogani.store.catalog.entity.ProductStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(Long id);

    @Override
    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(
            Specification<Product> spec,
            Pageable pageable);

    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySku(String sku);

    boolean existsByCategory_IdAndStatusNot(
            Integer categoryId,
            ProductStatus status
    );
}

