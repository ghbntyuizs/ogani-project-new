package com.ogani.store.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import com.ogani.store.catalog.entity.*;

public interface CategoryRepository extends JpaRepository<Category, Integer> {
    List<Category> findByActiveTrueOrderBySortOrderAsc();

    Optional<Category> findBySlug(String slug);

    boolean existsByParent_IdAndActiveTrue(Integer parentId);

    boolean existsBySlugAndActiveTrue(String slug);

    boolean existsBySlugAndActiveTrueAndIdNot(String slug, Integer id
    );
}
