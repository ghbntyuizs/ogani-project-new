package com.ogani.store.catalog.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "uploaded_images")
@Getter
@Setter
@NoArgsConstructor
public class UploadedImage {
    @Id
    @Column(length = 40)
    private String filename;
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(nullable = false)
    private boolean claimed;
    public UploadedImage(String filename) { this.filename = filename; }
}
