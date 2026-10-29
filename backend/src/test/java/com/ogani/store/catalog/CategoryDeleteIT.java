
package com.ogani.store.catalog;

import com.ogani.store.catalog.entity.Category;
import com.ogani.store.catalog.entity.Product;
import com.ogani.store.catalog.repository.CategoryRepository;
import com.ogani.store.catalog.repository.ProductRepository;
import com.ogani.store.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryDeleteIT extends AbstractIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    // ---------- Helpers ----------

    private Category newCategory(String name, String slug, Category parent) {
        Category category = new Category();
        category.setName(name);
        category.setSlug(slug);
        category.setParent(parent);
        category.setActive(true);
        category.setSortOrder(0);

        return categoryRepository.saveAndFlush(category);
    }

    private Category findCategory(Integer id) {
        return categoryRepository.findById(id).orElseThrow();
    }

    // ---------- Successful deletion ----------

    @Test
    void deleteCategoryWithoutReferences_shouldSoftDelete() throws Exception {
        Category category = newCategory(
                "Temporary Category",
                "temporary-category",
                null
        );

        delete("/api/admin/categories/" + category.getId(), ADMIN)
                .andExpect(status().isOk());

        Category updated = findCategory(category.getId());

        assertThat(updated.getActive()).isFalse();

        // Soft delete: record vẫn tồn tại trong database.
        assertThat(categoryRepository.existsById(category.getId())).isTrue();
    }

    @Test
    void deleteCategoryTwice_shouldRemainInactive() throws Exception {
        Category category = newCategory(
                "Repeated Delete",
                "repeated-delete",
                null
        );

        delete("/api/admin/categories/" + category.getId(), ADMIN)
                .andExpect(status().isOk());

        delete("/api/admin/categories/" + category.getId(), ADMIN)
                .andExpect(status().isOk());

        assertThat(findCategory(category.getId()).getActive()).isFalse();
    }

    // ---------- Not found ----------

    @Test
    void deleteNonExistingCategory_shouldReturn404() throws Exception {
        delete("/api/admin/categories/999999999", ADMIN)
                .andExpect(status().isNotFound());
    }

    // ---------- Product references ----------

    @Test
    void deleteCategoryReferencedByProduct_shouldBeRejected() throws Exception {
        // Product seed ID 1 thuộc category ID 2 theo dữ liệu test của dự án.
        Product product = productRepository.findById(1L).orElseThrow();
        Category category = product.getCategory();

        assertThat(category.getActive()).isTrue();

        delete("/api/admin/categories/" + category.getId(), ADMIN)
                .andExpect(status().isBadRequest());

        // Danh mục phải còn active sau khi request bị từ chối.
        assertThat(findCategory(category.getId()).getActive()).isTrue();
    }

    // ---------- Child category references ----------

    @Test
    void deleteCategoryWithActiveChild_shouldBeRejected() throws Exception {
        Category parent = newCategory(
                "Parent Category",
                "parent-category",
                null
        );

        newCategory(
                "Child Category",
                "child-category",
                parent
        );

        delete("/api/admin/categories/" + parent.getId(), ADMIN)
                .andExpect(status().isBadRequest());

        assertThat(findCategory(parent.getId()).getActive()).isTrue();
    }

    @Test
    void deleteCategoryWithOnlyInactiveChildren_shouldSucceed()
            throws Exception {
        Category parent = newCategory(
                "Parent With Inactive Child",
                "parent-with-inactive-child",
                null
        );

        Category child = newCategory(
                "Inactive Child",
                "inactive-child",
                parent
        );

        child.setActive(false);
        categoryRepository.saveAndFlush(child);

        delete("/api/admin/categories/" + parent.getId(), ADMIN)
                .andExpect(status().isOk());

        assertThat(findCategory(parent.getId()).getActive()).isFalse();
    }

    // ---------- Authorization ----------

    @Test
    void staffDeleteCategory_shouldReturn403() throws Exception {
        Category category = newCategory(
                "Protected Category",
                "protected-category",
                null
        );

        delete("/api/admin/categories/" + category.getId(), STAFF)
                .andExpect(status().isForbidden());

        assertThat(findCategory(category.getId()).getActive()).isTrue();
    }

    @Test
    void anonymousDeleteCategory_shouldReturn401() throws Exception {
        Category category = newCategory(
                "Anonymous Protected Category",
                "anonymous-protected-category",
                null
        );

        delete("/api/admin/categories/" + category.getId(), ANONYMOUS)
                .andExpect(status().isUnauthorized());

        assertThat(findCategory(category.getId()).getActive()).isTrue();
    }
}
