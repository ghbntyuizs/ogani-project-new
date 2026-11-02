package com.ogani.store.catalog;

import com.ogani.store.common.AbstractIntegrationTest;
import com.ogani.store.catalog.service.impl.ManagedUploadService;
import com.ogani.store.catalog.service.impl.ImageStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import com.ogani.store.catalog.repository.UploadedImageRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UploadMaintenanceIT extends AbstractIntegrationTest {
    private static final Path DIRECTORY = Path.of(System.getProperty("java.io.tmpdir"), "ogani-managed-test-" + UUID.randomUUID());
    @DynamicPropertySource static void directory(DynamicPropertyRegistry registry) {
        registry.add("app.upload.directory", DIRECTORY::toString);
    }
    @Autowired ManagedUploadService uploads;
    @Autowired ImageStorage storage;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired UploadedImageRepository imageRecords;

    private String upload() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return uploads.store(new MockMultipartFile("file", "test.png", "image/png", bytes.toByteArray()));
    }
    private void old(String name) {
        jdbc.update("update uploaded_images set created_at=? where filename=?", LocalDateTime.now().minusDays(2), name);
    }
    @Test void onlyOldNeverClaimedUploadsAreRemoved() throws Exception {
        String unused = upload(), used = upload(), recent = upload();
        try {
            uploads.claim("/api/images/" + used); old(unused); old(used);
            uploads.cleanupUnclaimed(LocalDateTime.now().minusDays(1), 100);
            assertFalse(Files.exists(DIRECTORY.resolve(unused)));
            assertFalse(Files.exists(DIRECTORY.resolve(unused.replace(".png", "-thumb.png"))));
            assertTrue(Files.exists(storage.read(used))); assertTrue(Files.exists(storage.read(recent)));
            assertThrows(RuntimeException.class, () -> uploads.claim("/api/images/" + unused));
        } finally { storage.delete(used); storage.delete(recent); }
    }
    @Test void successfulProductWriteClaimsTheUpload() throws Exception {
        String name = upload();
        try {
            String body = productJson("Managed upload", "MANAGED-UPLOAD");
            body = body.substring(0, body.length() - 1) + ",\"thumbnailUrl\":\"/api/images/" + name + "\"}";
            post("/api/admin/products", ADMIN, body).andExpect(status().isCreated());
            old(name); uploads.cleanupUnclaimed(LocalDateTime.now().minusDays(1), 100);
            assertTrue(Files.exists(storage.read(name)));
        } finally { storage.delete(name); }
    }

    @Test void externalImagePathIsNotRequiredToExistInLocalStorage() throws Exception {
        String body = productJson("External managed-looking image", "EXTERNAL-IMAGE");
        body = body.substring(0, body.length() - 1)
                + ",\"thumbnailUrl\":\"https://cdn.example.com/api/images/01234567-0123-0123-0123-012345678901.png\"}";
        post("/api/admin/products", ADMIN, body).andExpect(status().isCreated());
    }

    @Test void absoluteLocalImageReferencesAreClaimedAndRetained() throws Exception {
        String name = upload();
        try {
            uploads.claim("http://127.0.0.1:8080/api/images/" + name);
            old(name); uploads.cleanupUnclaimed(LocalDateTime.now().minusDays(1), 100);
            assertTrue(Files.exists(storage.read(name)));
        } finally { storage.delete(name); }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cleanupCannotDeleteAnUploadWhileItIsBeingClaimed() throws Exception {
        String name = upload(); old(name);
        var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        var transaction = new TransactionTemplate(transactions);
        var claiming = executor.submit(() -> transaction.execute(status -> {
            uploads.claim("/api/images/" + name); held.countDown();
            try {
                if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Claim test timed out");
            } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
            return null;
        }));
        try {
            assertTrue(held.await(10, TimeUnit.SECONDS));
            uploads.cleanupUnclaimed(LocalDateTime.now().minusDays(1), 100);
            assertTrue(Files.exists(storage.read(name)), "Cleanup must skip the locked upload");
            release.countDown(); claiming.get(15, TimeUnit.SECONDS);
            uploads.cleanupUnclaimed(LocalDateTime.now().minusDays(1), 100);
            assertTrue(Files.exists(storage.read(name)), "A committed claim is permanently retained");
        } finally {
            release.countDown(); executor.shutdownNow(); executor.awaitTermination(15, TimeUnit.SECONDS);
            imageRecords.deleteById(name); storage.delete(name);
        }
    }
}
