package com.ogani.store.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import com.ogani.store.catalog.entity.*;

public interface CategoryRepository extends JpaRepository<Category, Integer> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from Category c where c.id=:id")
    Optional<Category> findByIdForCheckout(Integer id);
    List<Category> findByActiveTrueOrderBySortOrderAsc();
    List<Category> findAllByOrderBySortOrderAscIdAsc();

    Optional<Category> findBySlug(String slug);

    boolean existsByParent_IdAndActiveTrue(Integer parentId);

    boolean existsBySlugAndActiveTrue(String slug);

    boolean existsBySlugAndActiveTrueAndIdNot(String slug, Integer id
    );
}
