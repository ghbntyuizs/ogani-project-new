package com.ogani.store.catalog;

import com.jayway.jsonpath.JsonPath;
import com.ogani.store.common.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GalleryStateIT extends AbstractIntegrationTest {
    private String state() throws Exception {
        return get("/api/admin/products/1/images/state", ADMIN).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images").isArray()).andReturn().getResponse().getContentAsString();
    }

    @Test void selectingThumbnailMovesOwnedImageFirstAndReturnsState() throws Exception {
        String before = state();
        Number version = JsonPath.read(before, "$.data.version");
        Number imageId = JsonPath.read(before, "$.data.images[1].id");
        String imageUrl = JsonPath.read(before, "$.data.images[1].imageUrl");
        mvc.perform(MockMvcRequestBuilders.put("/api/admin/products/1/images/state/" + imageId + "/thumbnail")
                .header("If-Match", version).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.thumbnailUrl").value(imageUrl))
                .andExpect(jsonPath("$.data.images[0].id").value(imageId.intValue()))
                .andExpect(jsonPath("$.data.version").isNumber());
        mvc.perform(MockMvcRequestBuilders.put("/api/admin/products/1/images/state/" + imageId + "/thumbnail")
                .header("If-Match", version).with(user("admin").roles("ADMIN")))
                .andExpect(status().isConflict());
    }

    @Test void directThumbnailUploadReferenceBecomesFirstGalleryImage() throws Exception {
        Number version = JsonPath.read(state(), "$.data.version");
        mvc.perform(MockMvcRequestBuilders.post("/api/admin/products/1/images/state?thumbnail=true")
                .header("If-Match", version).contentType("application/json")
                .content("{\"imageUrl\":\"img/new-thumbnail.jpg\"}").with(user("staff").roles("STAFF")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.thumbnailUrl").value("img/new-thumbnail.jpg"))
                .andExpect(jsonPath("$.data.images[0].imageUrl").value("img/new-thumbnail.jpg"));
    }

    @Test void cannotSelectImageFromAnotherProductOrWriteWithoutVersion() throws Exception {
        Number version = JsonPath.read(state(), "$.data.version");
        mvc.perform(MockMvcRequestBuilders.put("/api/admin/products/1/images/state/999999/thumbnail")
                .header("If-Match", version).with(user("admin").roles("ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(MockMvcRequestBuilders.post("/api/admin/products/1/images/state")
                .contentType("application/json").content("{\"imageUrl\":\"img/x.jpg\"}")
                .with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
    }

    @Test void legacySecondaryImageEditsAlsoInvalidateOldForms() throws Exception {
        String before = state();
        Number version = JsonPath.read(before, "$.data.version");
        Number imageId = JsonPath.read(before, "$.data.images[1].id");
        mvc.perform(MockMvcRequestBuilders.put("/api/admin/products/1/images/" + imageId)
                .header("If-Match", version).contentType("application/json")
                .content("{\"imageUrl\":\"img/secondary-edited.jpg\"}").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        Number after = JsonPath.read(state(), "$.data.version");
        assertTrue(after.longValue() > version.longValue());
        mvc.perform(MockMvcRequestBuilders.put("/api/admin/products/1/images/state/" + imageId + "/thumbnail")
                .header("If-Match", version).with(user("admin").roles("ADMIN"))).andExpect(status().isConflict());
    }

    @Test void legacyWritesCannotOmitVersionHeader() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/admin/products/1/images")
                .contentType("application/json").content("{\"imageUrl\":\"img/unversioned.jpg\"}")
                .with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
    }
}
