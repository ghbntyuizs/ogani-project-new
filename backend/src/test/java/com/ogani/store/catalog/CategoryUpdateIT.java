package com.ogani.store.catalog;

import com.ogani.store.catalog.entity.Category;
import com.ogani.store.catalog.repository.CategoryRepository;
import com.ogani.store.support.AbstractIntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryUpdateIT extends AbstractIntegrationTest {

    private static final String URL = "/api/admin/categories";

    @Autowired
    private CategoryRepository categories;

    private Category newCategory(String name, String slug, Category parent) {
        Category category = new Category();
        category.setName(name);
        category.setSlug(slug);
        category.setParent(parent);
        category.setSortOrder(0);
        category.setActive(true);
        return categories.saveAndFlush(category);
    }

    private String body(String name, String slug, Integer parentId, Integer sortOrder) {
        return """
                {
                  "name": "%s",
                  "slug": %s,
                  "parentId": %s,
                  "sortOrder": %s
                }
                """.formatted(
                name,
                slug == null ? "null" : "\"" + slug + "\"",
                parentId == null ? "null" : parentId,
                sortOrder == null ? "null" : sortOrder
        );
    }

    @Test
    @DisplayName("Cập nhật thành công -> 200 và lưu thay đổi")
    void updateCategory_validRequest_returnsUpdatedCategory() throws Exception {
        Category category = newCategory("Old Category", "old-category", null);

        put(URL + "/" + category.getId(), ADMIN,
                body("New Category", "New Category Slug", null, 5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(category.getId()))
                .andExpect(jsonPath("$.data.name").value("New Category"))
                .andExpect(jsonPath("$.data.slug").value("new-category-slug"))
                .andExpect(jsonPath("$.data.parentId").doesNotExist());

        Category updated = categories.findById(category.getId()).orElseThrow();
        assertEquals("New Category", updated.getName());
        assertEquals("new-category-slug", updated.getSlug());
        assertEquals(5, updated.getSortOrder());
    }

    @Test
    @DisplayName("Không gửi slug -> giữ slug cũ")
    void updateCategory_missingSlug_keepsExistingSlug() throws Exception {
        Category category = newCategory("Original", "original-slug", null);

        put(URL + "/" + category.getId(), ADMIN,
                body("Renamed Category", null, null, 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("original-slug"));
    }

    @Test
    @DisplayName("Giữ nguyên slug của chính category -> không báo trùng")
    void updateCategory_sameSlug_isAllowed() throws Exception {
        Category category = newCategory("Original", "original-slug", null);

        put(URL + "/" + category.getId(), ADMIN,
                body("Renamed Category", "ORIGINAL SLUG", null, 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("original-slug"));
    }

    @Test
    @DisplayName("Slug thuộc category khác -> 409")
    void updateCategory_slugBelongsToAnotherCategory_returns409() throws Exception {
        Category first = newCategory("First", "first-category", null);
        newCategory("Second", "second-category", null);

        put(URL + "/" + first.getId(), ADMIN,
                body("First Updated", "SECOND CATEGORY", null, 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Category slug already exists"));
    }

    @Test
    @DisplayName("ID không tồn tại -> 404")
    void updateCategory_unknownId_returns404() throws Exception {
        put(URL + "/999999", ADMIN,
                body("New Category", null, null, 0))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Tên rỗng -> 400")
    void updateCategory_blankName_returns400() throws Exception {
        Category category = newCategory("Original", "original-slug", null);

        put(URL + "/" + category.getId(), ADMIN,
                """
                {
                  "name": " ",
                  "sortOrder": 0
                }
                """)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Sort order âm -> 400")
    void updateCategory_negativeSortOrder_returns400() throws Exception {
        Category category = newCategory("Original", "original-slug", null);

        put(URL + "/" + category.getId(), ADMIN,
                body("Updated", null, null, -1))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Chuyển category sang parent hợp lệ")
    void updateCategory_changeParent_succeeds() throws Exception {
        Category parent = newCategory("Parent", "parent-category", null);
        Category child = newCategory("Child", "child-category", null);

        put(URL + "/" + child.getId(), ADMIN,
                body("Child Updated", null, parent.getId(), 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentId").value(parent.getId()));

        Category updated = categories.findById(child.getId()).orElseThrow();
        assertEquals(parent.getId(), updated.getParent().getId());
    }

    @Test
    @DisplayName("Parent không tồn tại -> 404")
    void updateCategory_unknownParent_returns404() throws Exception {
        Category category = newCategory("Original", "original-slug", null);

        put(URL + "/" + category.getId(), ADMIN,
                body("Updated", null, 999999, 0))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Không cho category làm parent của chính nó")
    void updateCategory_selfAsParent_returns400() throws Exception {
        Category category = newCategory("Original", "original-slug", null);

        put(URL + "/" + category.getId(), ADMIN,
                body("Updated", null, category.getId(), 0))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Không cho chọn hậu duệ làm parent")
    void updateCategory_descendantAsParent_returns400() throws Exception {
        Category ancestor = newCategory("Ancestor", "ancestor-category", null);
        Category child = newCategory("Child", "child-category", ancestor);
        Category grandchild = newCategory("Grandchild", "grandchild-category", child);

        put(URL + "/" + ancestor.getId(), ADMIN,
                body("Ancestor Updated", null, grandchild.getId(), 0))
                .andExpect(status().isBadRequest());

        Category unchanged = categories.findById(ancestor.getId()).orElseThrow();
        assertNull(unchanged.getParent());
    }

    @Test
    @DisplayName("Parent inactive -> 400")
    void updateCategory_inactiveParent_returns400() throws Exception {
        Category inactiveParent = newCategory("Inactive", "inactive-parent", null);
        inactiveParent.setActive(false);
        categories.saveAndFlush(inactiveParent);

        Category child = newCategory("Child", "child-category", null);

        put(URL + "/" + child.getId(), ADMIN,
                body("Child Updated", null, inactiveParent.getId(), 0))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Update category inactive không tự kích hoạt lại")
    void updateCategory_inactiveCategory_remainsInactive() throws Exception {
        Category category = newCategory("Inactive", "inactive-category", null);
        category.setActive(false);
        categories.saveAndFlush(category);

        put(URL + "/" + category.getId(), ADMIN,
                body("Renamed Inactive", null, null, 0))
                .andExpect(status().isOk());

        Category updated = categories.findById(category.getId()).orElseThrow();
        assertFalse(updated.getActive());
    }

    @Test
    @DisplayName("STAFF không được update category")
    void updateCategory_staffForbidden() throws Exception {
        Category category = newCategory("Original", "original-slug", null);

        put(URL + "/" + category.getId(), STAFF,
                body("Updated", null, null, 0))
                .andExpect(status().isForbidden());
    }
}