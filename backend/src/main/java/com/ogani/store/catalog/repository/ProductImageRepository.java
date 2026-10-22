package com.ogani.store.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import com.ogani.store.user.entity.*;
import com.ogani.store.catalog.entity.*;
import com.ogani.store.order.entity.*;
import com.ogani.store.content.entity.*;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
}
