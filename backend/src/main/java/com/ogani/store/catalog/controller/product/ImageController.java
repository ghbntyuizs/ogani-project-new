package com.ogani.store.catalog.controller.product;

import com.ogani.store.catalog.service.impl.ImageStorage;
import com.ogani.store.catalog.service.impl.ManagedUploadService;
import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import java.util.concurrent.TimeUnit;
import com.ogani.store.common.dto.common.ApiResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
public class ImageController {
    private final ImageStorage storage;
    private final ManagedUploadService uploads;
    public ImageController(ImageStorage storage, ManagedUploadService uploads) {
        this.storage = storage; this.uploads = uploads;
    }
    public record UploadResponse(String imageUrl) {}

    @PostMapping(value = "/api/admin/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UploadResponse> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.ok(new UploadResponse("/api/images/" + uploads.store(file)));
    }

    @GetMapping("/api/images/{name}")
    public ResponseEntity<FileSystemResource> image(@PathVariable String name,
            @RequestParam(defaultValue = "original") String size) throws IOException {
        if (!size.equals("original") && !size.equals("thumb"))
            throw AppException.withCustomMessage(ErrorCode.VALIDATION_ERROR, "Unknown image size");
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(size.equals("thumb") ? storage.readThumbnail(name) : storage.read(name)));
    }
}
