package com.ogani.store.catalog.service.impl;

import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageStorage {
    private final Path directory;
    public ImageStorage(@Value("${app.upload.directory:./uploads}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public String store(MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) {
            throw invalid("Image must be between 1 byte and 5 MB");
        }
        // Decode and rewrite raster pixels: discard filenames, metadata and embedded content.
        try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid("Only PNG and JPEG images are supported");
            var reader = readers.next();
            try {
                var format = reader.getFormatName();
                if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg"))
                    throw invalid("Only PNG and JPEG images are supported");
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 8_000_000)
                    throw invalid("Image must contain at most 8 million pixels");
                var image = reader.read(0);
                Files.createDirectories(directory);
                String name = UUID.randomUUID() + ".png";
                var target = directory.resolve(name);
                try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                    if (!ImageIO.write(image, "png", output)) throw new IOException("PNG encoder unavailable");
                } catch (IOException ex) {
                    Files.deleteIfExists(target);
                    throw ex;
                }
                try { writeThumbnail(image, name); }
                catch (IOException ex) { delete(name); throw ex; }
                return name;
            } finally { reader.dispose(); }
        } catch (IIOException ex) {
            throw invalid("Image cannot be decoded");
        }
    }

    public Path read(String name) {
        if (!name.matches("[a-f0-9-]{36}\\.png"))
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Image");
        var path = directory.resolve(name);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Image");
        return path;
    }

    public Path readThumbnail(String name) throws IOException {
        Path original = read(name);
        Path thumbnail = directory.resolve(thumbnailName(name));
        if (!Files.isRegularFile(thumbnail, LinkOption.NOFOLLOW_LINKS)) {
            BufferedImage image = ImageIO.read(original.toFile());
            if (image == null) throw invalid("Image cannot be decoded");
            writeThumbnail(image, name);
        }
        if (!Files.isRegularFile(thumbnail, LinkOption.NOFOLLOW_LINKS))
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Image");
        return thumbnail;
    }

    private void writeThumbnail(BufferedImage image, String name) throws IOException {
        double scale = Math.min(1.0, 320.0 / Math.max(image.getWidth(), image.getHeight()));
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage thumbnail = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = thumbnail.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally { graphics.dispose(); }
        Path temporary = Files.createTempFile(directory, "preview-", ".tmp");
        try {
            ImageIO.write(thumbnail, "png", temporary.toFile());
            try { Files.move(temporary, directory.resolve(thumbnailName(name))); }
            catch (FileAlreadyExistsException ignored) { /* Another reader generated it first. */ }
        } finally { Files.deleteIfExists(temporary); }
    }

    public void delete(String name) throws IOException {
        if (!name.matches("[a-f0-9-]{36}\\.png")) throw invalid("Invalid managed image name");
        Files.deleteIfExists(directory.resolve(name));
        Files.deleteIfExists(directory.resolve(thumbnailName(name)));
    }

    private String thumbnailName(String name) { return name.substring(0, name.length() - 4) + "-thumb.png"; }

    private AppException invalid(String message) {
        return AppException.withCustomMessage(ErrorCode.VALIDATION_ERROR, message);
    }
}
