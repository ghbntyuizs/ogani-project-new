package com.ogani.store.catalog;

import com.jayway.jsonpath.JsonPath;
import com.ogani.store.catalog.entity.Category;
import com.ogani.store.catalog.repository.CategoryRepository;
import com.ogani.store.common.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Các quy tắc vòng đời category: sortOrder khi PUT, xóa khi chỉ còn sản phẩm ẩn, dùng lại slug sau khi xóa mềm. */
class CategoryLifecycleIT extends AbstractIntegrationTest {

    private static final String URL = "/api/admin/categories";

    @Autowired
    private CategoryRepository categories;

    private Category newCategory(String slug, int sortOrder) {
        Category category = new Category();
        category.setName(slug);
        category.setSlug(slug);
        category.setSortOrder(sortOrder);
        category.setActive(true);
        return categories.saveAndFlush(category);
    }

    private static String body(String name, String slug, Integer sortOrder) {
        return "{\"name\":\"" + name + "\""
                + (slug == null ? "" : ",\"slug\":\"" + slug + "\"")
                + (sortOrder == null ? "" : ",\"sortOrder\":" + sortOrder)
                + "}";
    }

    @Test
    @DisplayName("PUT không gửi sortOrder -> giữ nguyên thứ tự cũ")
    void update_withoutSortOrder_keepsExistingValue() throws Exception {
        Category category = newCategory("keep-sort-order", 7);

        put(URL + "/" + category.getId(), ADMIN, body("Keep Sort Order", null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sortOrder").value(7));

        assertThat(categories.findById(category.getId()).orElseThrow().getSortOrder()).isEqualTo(7);
    }

    @Test
    @DisplayName("PUT có sortOrder -> đổi sang giá trị mới (kể cả 0)")
    void update_withSortOrder_changesValue() throws Exception {
        Category category = newCategory("change-sort-order", 7);

        put(URL + "/" + category.getId(), ADMIN, body("Change Sort Order", null, 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sortOrder").value(3));

        put(URL + "/" + category.getId(), ADMIN, body("Change Sort Order", null, 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sortOrder").value(0));
    }

    @Test
    @DisplayName("Chỉ còn sản phẩm HIDDEN -> xóa category thành công")
    void delete_categoryWithOnlyHiddenProducts_succeeds() throws Exception {
        Category category = newCategory("only-hidden-products", 0);

        String created = post("/api/admin/products", ADMIN,
                productJson("Hidden Soon", null, "HID-001", "10.00", null)
                        .replace("\"categoryId\":2", "\"categoryId\":" + category.getId()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number productId = JsonPath.read(created, "$.data.id");

        // Còn sản phẩm ACTIVE -> bị chặn.
        delete(URL + "/" + category.getId(), ADMIN).andExpect(status().isConflict());

        // Ẩn sản phẩm (soft delete) -> category xóa được.
        delete("/api/admin/products/" + productId, ADMIN).andExpect(status().isOk());
        delete(URL + "/" + category.getId(), ADMIN).andExpect(status().isOk());

        assertThat(categories.findById(category.getId()).orElseThrow().getActive()).isFalse();
    }

    @Test
    @DisplayName("Slug của category đã xóa mềm được dùng lại")
    void create_reusesSlugOfSoftDeletedCategory() throws Exception {
        Category category = newCategory("reusable-slug", 0);

        delete(URL + "/" + category.getId(), ADMIN).andExpect(status().isOk());

        post(URL, ADMIN, body("Reusable Again", "reusable-slug", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.slug").value("reusable-slug"));
    }

    @Test
    @DisplayName("Slug của category đang active vẫn bị chặn -> 409")
    void create_duplicateActiveSlug_returns409() throws Exception {
        newCategory("active-slug", 0);

        post(URL, ADMIN, body("Another", "active-slug", null))
                .andExpect(status().isConflict());
    }
}
