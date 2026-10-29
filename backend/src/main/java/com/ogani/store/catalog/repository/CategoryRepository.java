package com.ogani.store.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import com.ogani.store.user.entity.*;
import com.ogani.store.catalog.entity.*;

public interface CategoryRepository extends JpaRepository<Category, Integer> {
    List<Category> findByActiveTrueOrderBySortOrderAsc();

    boolean existsBySlug(String slug);

    Optional<Category> findBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Integer id);

}
