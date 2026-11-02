package com.ogani.store.catalog.service.impl;

import com.ogani.store.catalog.entity.UploadedImage;
import com.ogani.store.catalog.repository.UploadedImageRepository;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;

@Service
@Slf4j
public class ManagedUploadService {
    private final ImageStorage storage;
    private final UploadedImageRepository images;
    private final URI publicOrigin;

    public ManagedUploadService(ImageStorage storage, UploadedImageRepository images,
            @Value("${app.upload.public-origin:http://localhost:8080}") String publicOrigin) {
        this.storage = storage; this.images = images; this.publicOrigin = URI.create(publicOrigin);
        if (this.publicOrigin.getHost() == null || !("http".equalsIgnoreCase(this.publicOrigin.getScheme())
                || "https".equalsIgnoreCase(this.publicOrigin.getScheme())))
            throw new IllegalArgumentException("app.upload.public-origin must be an HTTP(S) origin");
    }

    @Transactional
    public String store(MultipartFile file) throws IOException {
        String filename = storage.store(file);
        try { images.saveAndFlush(new UploadedImage(filename)); }
        catch (RuntimeException ex) {
            try { storage.delete(filename); } catch (IOException cleanup) { ex.addSuppressed(cleanup); }
            throw ex;
        }
        return filename;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void claim(String url) {
        if (url == null || url.isBlank()) return;
        URI parsed;
        try { parsed = URI.create(url); } catch (IllegalArgumentException ignored) { return; }
        if ((parsed.isAbsolute() || parsed.getAuthority() != null) && !sameOrigin(parsed)) return;
        String path = parsed.getPath();
        if (path == null || !path.matches("/api/images/[a-f0-9-]{36}\\.png")) return;
        String filename = path.substring(path.lastIndexOf('/') + 1);
        var registered = images.findForUpdate(filename);
        storage.read(filename); // A cleanup that won the lock makes a stale attachment fail safely.
        registered.ifPresent(image -> image.setClaimed(true));
    }

    private boolean sameOrigin(URI uri) {
        boolean loopback = isLoopback(uri.getHost()) && isLoopback(publicOrigin.getHost());
        return uri.getHost() != null && (loopback || uri.getHost().equalsIgnoreCase(publicOrigin.getHost()))
                && effectivePort(uri) == effectivePort(publicOrigin)
                && (uri.getScheme() == null || uri.getScheme().equalsIgnoreCase(publicOrigin.getScheme()));
    }

    private boolean isLoopback(String host) { return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host); }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        String scheme = uri.getScheme() == null ? publicOrigin.getScheme() : uri.getScheme();
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    @Transactional
    public int cleanupUnclaimed(LocalDateTime cutoff, int batchSize) {
        int deleted = 0;
        for (var image : images.findUnclaimedForUpdate(cutoff, Math.min(Math.max(batchSize, 1), 100))) {
            try {
                storage.delete(image.getFilename());
                images.delete(image); deleted++;
            } catch (IOException ex) { log.warn("Unable to remove unused upload {}", image.getFilename(), ex); }
        }
        return deleted;
    }
}
