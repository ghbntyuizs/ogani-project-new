package com.ogani.store.catalog;

import com.ogani.store.catalog.service.impl.ImageStorage;
import com.ogani.store.common.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ImageStorageTest {
    @TempDir Path directory;
    @Test void storesDecodedImageUsingGeneratedName() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        var storage = new ImageStorage(directory.toString());
        var name = storage.store(new MockMultipartFile("file", "../../evil.html", "image/png", bytes.toByteArray()));
        assertTrue(name.matches("[a-f0-9-]{36}\\.png"));
        assertNotNull(ImageIO.read(storage.read(name).toFile()));
    }
    @Test void rejectsSpoofedImagesAndTraversal() {
        var storage = new ImageStorage(directory.toString());
        assertThrows(AppException.class, () -> storage.store(new MockMultipartFile("file", "x.png", "image/png", "<script>bad</script>".getBytes())));
        assertThrows(AppException.class, () -> storage.read("../secret"));
    }
    @Test void rejectsEmptyAndOversizedFiles() {
        var storage = new ImageStorage(directory.toString());
        assertThrows(AppException.class, () -> storage.store(new MockMultipartFile("file", new byte[0])));
        assertThrows(AppException.class, () -> storage.store(new MockMultipartFile("file", new byte[5 * 1024 * 1024 + 1])));
    }

    @Test void createsSmallAspectPreservingVariant() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1200, 600, BufferedImage.TYPE_INT_RGB), "png", bytes);
        var storage = new ImageStorage(directory.toString());
        String name = storage.store(new MockMultipartFile("file", "large.png", "image/png", bytes.toByteArray()));
        var preview = ImageIO.read(storage.readThumbnail(name).toFile());
        assertEquals(320, preview.getWidth()); assertEquals(160, preview.getHeight());
        assertEquals(1200, ImageIO.read(storage.read(name).toFile()).getWidth());
    }
}
