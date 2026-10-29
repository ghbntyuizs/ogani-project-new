package com.ogani.store.catalog;

import com.ogani.store.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ma trận phân quyền: ai được gọi gì. Test cái này TRƯỚC các nghiệp vụ khác. */
class AdminProductSecurityIT extends AbstractIntegrationTest {

    private static final String NEW_PRODUCT = productJson("Security Test Product", "SEC-001");
    private static final String NEW_IMAGE = "{\"imageUrl\":\"img/security.jpg\"}";

    // ---------- 401: chưa đăng nhập / token sai ----------

    @Test
    @DisplayName("Không token -> 401 ở API admin")
    void admin_withoutToken_returns401() throws Exception {
        get("/api/admin/products", ANONYMOUS).andExpect(status().isUnauthorized());
        post("/api/admin/products", ANONYMOUS, NEW_PRODUCT).andExpect(status().isUnauthorized());
        get("/api/admin/products/1/images", ANONYMOUS).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token rác -> 401")
    void admin_withGarbageToken_returns401() throws Exception {
        getWithRawAuthHeader("/api/admin/products", "Bearer not-a-real-jwt")
                .andExpect(status().isUnauthorized());
    }

    // ---------- 403: đăng nhập rồi nhưng sai vai trò ----------

    @Test
    @DisplayName("CUSTOMER -> 403 ở mọi API admin")
    void admin_asCustomer_returns403() throws Exception {
        get("/api/admin/products", CUSTOMER).andExpect(status().isForbidden());
        post("/api/admin/products", CUSTOMER, NEW_PRODUCT).andExpect(status().isForbidden());
        delete("/api/admin/products/1", CUSTOMER).andExpect(status().isForbidden());
        get("/api/admin/products/1/images", CUSTOMER).andExpect(status().isForbidden());
        post("/api/admin/products/1/images", CUSTOMER, NEW_IMAGE).andExpect(status().isForbidden());
    }

    // ---------- STAFF ----------

    @Test
    @DisplayName("STAFF xem/tạo/sửa product được")
    void staff_canReadCreateAndUpdate() throws Exception {
        get("/api/admin/products", STAFF).andExpect(status().isOk());
        post("/api/admin/products", STAFF, NEW_PRODUCT).andExpect(status().isCreated());
        put("/api/admin/products/1", STAFF, productJson("Vegetable's Package", "VEG-001"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("STAFF quản lý ảnh được")
    void staff_canManageImages() throws Exception {
        post("/api/admin/products/1/images", STAFF, NEW_IMAGE).andExpect(status().isCreated());
        get("/api/admin/products/1/images", STAFF).andExpect(status().isOk());
    }

    @Test
    @DisplayName("STAFF xoá product -> 403 (chỉ ADMIN)")
    void staff_cannotDeleteProduct() throws Exception {
        delete("/api/admin/products/1", STAFF).andExpect(status().isForbidden());
        // và sản phẩm vẫn còn hiển thị ở phía public
        get("/api/products/vegetables-package", ANONYMOUS).andExpect(status().isOk());
    }

    // ---------- ADMIN ----------

    @Test
    @DisplayName("ADMIN xoá product được")
    void admin_canDeleteProduct() throws Exception {
        delete("/api/admin/products/1", ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ---------- Public ----------

    @Test
    @DisplayName("API public không cần token")
    void publicEndpoints_noTokenNeeded() throws Exception {
        get("/api/products", ANONYMOUS).andExpect(status().isOk());
        get("/api/products/vegetables-package", ANONYMOUS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sku").value("VEG-001"));
    }
}
