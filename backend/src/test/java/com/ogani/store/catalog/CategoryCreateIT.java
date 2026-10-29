package com.ogani.store.catalog;

import com.ogani.store.catalog.entity.Category;
import com.ogani.store.catalog.repository.CategoryRepository;
import com.ogani.store.common.AbstractIntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryCreateIT extends AbstractIntegrationTest {

    private static final String CREATE_URL = "/api/admin/categories";

    @Autowired
    private CategoryRepository categories;

    @Test
    @DisplayName("Tạo category thành công -> tự sinh slug và dùng giá trị mặc định")
    void createCategory_generatesSlugAndDefaults() throws Exception {
        String body = """
                {
                  "name": "  Rau Củ Mới  "
                }
                """;

        post(CREATE_URL, ADMIN, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Rau Củ Mới"))
                .andExpect(jsonPath("$.data.slug").value("rau-cu-moi"))
                .andExpect(jsonPath("$.data.parentId").doesNotExist());

        Category saved = categories.findBySlug("rau-cu-moi")
                .orElseThrow();

        assertEquals("Rau Củ Mới", saved.getName());
        assertEquals(0, saved.getSortOrder());
        assertTrue(saved.getActive());
        assertNull(saved.getParent());
        assertNull(saved.getImageUrl());
    }

    @Test
    @DisplayName("Tạo category với slug, ảnh, parent và sortOrder được cung cấp")
    void createCategory_withOptionalFields() throws Exception {
        String body = """
                {
                  "name": "Organic Herbs",
                  "slug": "  Organic Herbs Special  ",
                  "imageUrl": " img/category/herbs.jpg ",
                  "parentId": 2,
                  "sortOrder": 12
                }
                """;

        post(CREATE_URL, ADMIN, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Organic Herbs"))
                .andExpect(jsonPath("$.data.slug").value("organic-herbs-special"))
                .andExpect(jsonPath("$.data.parentId").value(2));

        Category saved = categories.findBySlug("organic-herbs-special")
                .orElseThrow();

        assertEquals("img/category/herbs.jpg", saved.getImageUrl());
        assertEquals(12, saved.getSortOrder());
        assertTrue(saved.getActive());
        assertNotNull(saved.getParent());
        assertEquals(2, saved.getParent().getId());
    }

    @Test
    @DisplayName("Slug đã tồn tại -> trả 409 và không tạo category mới")
    void createCategory_duplicateSlug_returnsConflict() throws Exception {
        String body = """
                {
                  "name": "Another Vegetables",
                  "slug": "VEGETABLES"
                }
                """;

        post(CREATE_URL, ADMIN, body)
                .andExpect(status().isConflict());

        assertEquals(1, categories.findAll().stream()
                .filter(c -> "vegetables".equals(c.getSlug()))
                .count());
    }

    @Test
    @DisplayName("Tên rỗng -> trả 400")
    void createCategory_blankName_returnsBadRequest() throws Exception {
        String body = """
                {
                  "name": "   "
                }
                """;

        post(CREATE_URL, ADMIN, body)
                .andExpect(status().isBadRequest());

        assertFalse(categories.existsBySlugAndActiveTrue(""));
    }

    @Test
    @DisplayName("Tên quá dài -> trả 400")
    void createCategory_nameTooLong_returnsBadRequest() throws Exception {
        String longName = "a".repeat(101);
        String body = """
                {"name":"%s"}
                """.formatted(longName);

        post(CREATE_URL, ADMIN, body)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Tên không thể tạo slug hợp lệ -> trả 400")
    void createCategory_slugCannotBeGenerated_returnsBadRequest() throws Exception {
        String body = """
                {
                  "name": "🔥🔥🔥"
                }
                """;

        post(CREATE_URL, ADMIN, body)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Sort order âm -> trả 400")
    void createCategory_negativeSortOrder_returnsBadRequest() throws Exception {
        String body = """
                {
                  "name": "Invalid Sort Category",
                  "sortOrder": -1
                }
                """;

        post(CREATE_URL, ADMIN, body)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Parent category không tồn tại -> trả 404")
    void createCategory_parentDoesNotExist_returnsNotFound() throws Exception {
        String body = """
                {
                  "name": "Child Category",
                  "parentId": 999999
                }
                """;

        post(CREATE_URL, ADMIN, body)
                .andExpect(status().isNotFound());

        assertFalse(categories.existsBySlugAndActiveTrue("child-category"));
    }

    @Test
    @DisplayName("Parent category inactive -> không cho tạo category con")
    void createCategory_inactiveParent_returnsBadRequest() throws Exception {
        Category inactiveParent = new Category();
        inactiveParent.setName("Inactive Parent");
        inactiveParent.setSlug("inactive-parent");
        inactiveParent.setActive(false);
        inactiveParent.setSortOrder(0);

        Category savedParent = categories.saveAndFlush(inactiveParent);

        String body = """
                {
                  "name": "Child Of Inactive",
                  "parentId": %d
                }
                """.formatted(savedParent.getId());

        post(CREATE_URL, ADMIN, body)
                .andExpect(status().isBadRequest());

        assertFalse(categories.existsBySlugAndActiveTrue("child-of-inactive"));
    }

    @Test
    @DisplayName("Chưa đăng nhập -> không được tạo category")
    void createCategory_unauthenticated_returnsUnauthorized() throws Exception {
        String body = """
                {
                  "name": "Unauthorized Category"
                }
                """;

        post(CREATE_URL, ANONYMOUS, body)
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("STAFF không có quyền tạo category")
    void createCategory_staffForbidden() throws Exception {
        String body = """
                {
                  "name": "Staff Category"
                }
                """;

        post(CREATE_URL, STAFF, body)
                .andExpect(status().isForbidden());

        assertFalse(categories.existsBySlugAndActiveTrue("staff-category"));
    }
}