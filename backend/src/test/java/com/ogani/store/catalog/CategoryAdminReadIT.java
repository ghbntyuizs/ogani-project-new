package com.ogani.store.catalog;

import com.ogani.store.catalog.entity.Category;
import com.ogani.store.catalog.repository.CategoryRepository;
import com.ogani.store.common.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/admin/categories và /{id}: admin thấy cả category inactive, public thì không. */
class CategoryAdminReadIT extends AbstractIntegrationTest {

    private static final String URL = "/api/admin/categories";

    @Autowired
    private CategoryRepository categories;

    private Category newCategory(String slug, int sortOrder, boolean active) {
        Category category = new Category();
        category.setName(slug);
        category.setSlug(slug);
        category.setSortOrder(sortOrder);
        category.setActive(active);
        return categories.saveAndFlush(category);
    }

    @Test
    @DisplayName("Admin list thấy cả category inactive, kèm sortOrder và active")
    void adminList_includesInactiveCategories() throws Exception {
        newCategory("read-active", 4, true);
        newCategory("read-inactive", 5, false);

        get(URL, ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.slug=='read-active' && @.active==true && @.sortOrder==4)]").exists())
                .andExpect(jsonPath("$.data[?(@.slug=='read-inactive' && @.active==false && @.sortOrder==5)]").exists());
    }

    @Test
    @DisplayName("Public list chỉ trả category active")
    void publicList_excludesInactiveCategories() throws Exception {
        newCategory("public-active", 0, true);
        newCategory("public-inactive", 0, false);

        get("/api/categories", ANONYMOUS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.slug=='public-active')]").exists())
                .andExpect(jsonPath("$.data[?(@.slug=='public-inactive')]").doesNotExist());
    }

    @Test
    @DisplayName("GET theo id -> trả đủ trường, kể cả category inactive")
    void getById_returnsCategoryEvenWhenInactive() throws Exception {
        Category category = newCategory("get-by-id-inactive", 9, false);

        get(URL + "/" + category.getId(), ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(category.getId()))
                .andExpect(jsonPath("$.data.slug").value("get-by-id-inactive"))
                .andExpect(jsonPath("$.data.sortOrder").value(9))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    @DisplayName("GET id không tồn tại -> 404")
    void getById_unknownId_returns404() throws Exception {
        get(URL + "/999999999", ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Category not found"));
    }

    @Test
    @DisplayName("STAFF được đọc list và theo id (chỉ ghi mới bị chặn)")
    void staffCanRead() throws Exception {
        Category category = newCategory("staff-read", 0, true);

        get(URL, STAFF).andExpect(status().isOk());
        get(URL + "/" + category.getId(), STAFF).andExpect(status().isOk());
    }

    @Test
    @DisplayName("CUSTOMER -> 403, chưa đăng nhập -> 401")
    void nonStaffCannotRead() throws Exception {
        get(URL, CUSTOMER).andExpect(status().isForbidden());
        get(URL + "/1", CUSTOMER).andExpect(status().isForbidden());
        get(URL, ANONYMOUS).andExpect(status().isUnauthorized());
        get(URL + "/1", ANONYMOUS).andExpect(status().isUnauthorized());
    }
}
