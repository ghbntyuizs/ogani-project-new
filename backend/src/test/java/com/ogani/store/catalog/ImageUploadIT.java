package com.ogani.store.catalog;

import com.jayway.jsonpath.JsonPath;
import com.ogani.store.catalog.service.impl.ImageStorage;
import com.ogani.store.common.AbstractIntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ImageUploadIT extends AbstractIntegrationTest {
    private static final Path DIRECTORY = Path.of(System.getProperty("java.io.tmpdir"), "ogani-upload-tests-" + UUID.randomUUID());
    @DynamicPropertySource static void uploads(DynamicPropertyRegistry registry) {
        registry.add("app.upload.directory", DIRECTORY::toString);
    }
    @Autowired ImageStorage storage;

    private MockMultipartFile png() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return new MockMultipartFile("file", "test.png", "image/png", bytes.toByteArray());
    }
    @Test void adminUploadsAndPublicCanReadImage() throws Exception {
        var result = mvc.perform(multipart("/api/admin/uploads").file(png()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isCreated()).andReturn();
        String url = JsonPath.read(result.getResponse().getContentAsString(), "$.data.imageUrl");
        try {
            get(url, ANONYMOUS).andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        } finally { Files.deleteIfExists(storage.read(url.substring(url.lastIndexOf('/') + 1))); }
    }
    @Test void customersAndAnonymousCannotUpload() throws Exception {
        mvc.perform(multipart("/api/admin/uploads").file(png())).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/admin/uploads").file(png()).with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/admin/uploads").file(new MockMultipartFile("file", "x.png", "image/png", "bad".getBytes()))
                .with(user("staff").roles("STAFF"))).andExpect(status().isBadRequest());
    }
}
