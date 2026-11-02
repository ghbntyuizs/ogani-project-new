package com.ogani.store.catalog.repository;

import com.ogani.store.catalog.entity.UploadedImage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

public interface UploadedImageRepository extends JpaRepository<UploadedImage, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from UploadedImage i where i.filename = :filename")
    Optional<UploadedImage> findForUpdate(String filename);

    @Query(value = "select * from uploaded_images where claimed=false and created_at < :cutoff "
            + "order by created_at, filename limit :batchSize for update skip locked", nativeQuery = true)
    List<UploadedImage> findUnclaimedForUpdate(LocalDateTime cutoff, int batchSize);
}
