package com.ogani.store.catalog;

import com.ogani.store.common.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test cho GET /api/products/{slug}/images.
 * LƯU Ý: class này chỉ pass sau khi bạn thêm endpoint public vào ProductController (xem hướng dẫn).
 */
class PublicProductImageIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("Khách xem gallery theo slug, không cần token, đúng thứ tự")
    void anonymous_canSeeGallery() throws Exception {
        get("/api/products/vegetables-package/images", ANONYMOUS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].sortOrder").value(1));
    }

    @Test
    @DisplayName("Sản phẩm không có ảnh -> 200, mảng rỗng")
    void productWithoutImages_returnsEmptyList() throws Exception {
        get("/api/products/fresh-garden-vegetable/images", ANONYMOUS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("Sản phẩm HIDDEN -> 404")
    void hiddenProduct_returns404() throws Exception {
        delete("/api/admin/products/1", ADMIN).andExpect(status().isOk());
        get("/api/products/vegetables-package/images", ANONYMOUS).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Slug không tồn tại -> 404")
    void unknownSlug_returns404() throws Exception {
        get("/api/products/no-such-product/images", ANONYMOUS).andExpect(status().isNotFound());
    }
}
