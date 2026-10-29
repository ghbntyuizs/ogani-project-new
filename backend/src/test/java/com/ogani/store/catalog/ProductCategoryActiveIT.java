package com.ogani.store.catalog;

import com.ogani.store.catalog.entity.Category;
import com.ogani.store.catalog.repository.CategoryRepository;
import com.ogani.store.common.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sản phẩm không được gán vào category đã xóa mềm.
 * Seed: sản phẩm id=1 thuộc category 2; sản phẩm id=4 (NUT-001) thuộc category 3.
 */
class ProductCategoryActiveIT extends AbstractIntegrationTest {

    @Autowired
    private CategoryRepository categories;

    private Category newInactiveCategory(String slug) {
        Category category = new Category();
        category.setName(slug);
        category.setSlug(slug);
        category.setSortOrder(0);
        category.setActive(false);
        return categories.saveAndFlush(category);
    }

    private static String withCategory(String json, int categoryId) {
        return json.replace("\"categoryId\":2", "\"categoryId\":" + categoryId);
    }

    @Test
    @DisplayName("Tạo sản phẩm vào category inactive -> 400")
    void create_inactiveCategory_returns400() throws Exception {
        Category inactive = newInactiveCategory("inactive-for-create");

        post("/api/admin/products", ADMIN,
                withCategory(productJson("Orphan", "ORP-001"), inactive.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Category must be active"));
    }

    @Test
    @DisplayName("Đổi sản phẩm sang category inactive -> 400, category cũ giữ nguyên")
    void update_moveToInactiveCategory_returns400() throws Exception {
        Category inactive = newInactiveCategory("inactive-for-move");

        put("/api/admin/products/1", ADMIN,
                withCategory(productJson("Vegetable's Package", "VEG-001"), inactive.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Category must be active"));

        get("/api/admin/products/1", ADMIN)
                .andExpect(jsonPath("$.data.categoryId").value(2));
    }

    @Test
    @DisplayName("Category hiện tại của sản phẩm bị xóa mềm -> vẫn sửa được sản phẩm nếu không đổi category")
    void update_keepingCurrentInactiveCategory_succeeds() throws Exception {
        Category current = categories.findById(3).orElseThrow();
        current.setActive(false);
        categories.saveAndFlush(current);

        put("/api/admin/products/4", ADMIN,
                withCategory(productJson("Raisin Updated", "NUT-001"), 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryId").value(3));
    }

    @Test
    @DisplayName("Đổi sang category active vẫn bình thường")
    void update_moveToActiveCategory_succeeds() throws Exception {
        put("/api/admin/products/1", ADMIN,
                withCategory(productJson("Vegetable's Package", "VEG-001"), 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryId").value(3));
    }
}
