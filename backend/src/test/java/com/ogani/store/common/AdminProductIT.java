package com.ogani.store.common;

import com.ogani.store.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seed dùng trong test:
 *  id=1 'Vegetable''s Package' slug=vegetables-package sku=VEG-001 category=2 price=50 (ACTIVE, có order_items)
 *  id=2 sku=VEG-002 | id=4 'Raisin''n''nuts' category=3 price=36 salePrice=30 | id=6 'Fresh Beef Steak' sku=MEA-001
 */
class AdminProductIT extends AbstractIntegrationTest {

    @Autowired
    EntityManager em;
    @Autowired
    JdbcTemplate jdbc;

    // =====================================================================
    // CREATE
    // =====================================================================
    @Nested
    @DisplayName("POST /api/admin/products")
    class Create {

        @Test
        @DisplayName("Hợp lệ, bỏ trống slug/status/featured -> 201, slug tự sinh, mặc định ACTIVE + featured=false")
        void valid_autoSlugAndDefaults() throws Exception {
            post("/api/admin/products", ADMIN, productJson("Test Product", "TST-001"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.id").isNumber())
                    .andExpect(jsonPath("$.data.slug").value("test-product"))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.data.featured").value(false));
        }

        @Test
        @DisplayName("Tên tiếng Việt có dấu -> slug không dấu")
        void vietnameseNameGivesAsciiSlug() throws Exception {
            post("/api/admin/products", ADMIN, productJson("Thịt Bò Tươi", "TST-VN"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.slug").value("thit-bo-tuoi"));
        }

        @Test
        @DisplayName("Tự điền slug thì slug được chuẩn hoá")
        void explicitSlugIsNormalised() throws Exception {
            post("/api/admin/products", ADMIN, productJson("Any Name", "My Custom Slug", "TST-002", "10.00", null))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.slug").value("my-custom-slug"));
        }

        @Test
        @DisplayName("SKU trùng -> 409")
        void duplicateSku_returns409() throws Exception {
            post("/api/admin/products", ADMIN, productJson("Another Name", "VEG-001"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("SKU already exists"));
        }

        @Test
        @DisplayName("Slug (sinh từ tên) trùng -> 409")
        void duplicateSlug_returns409() throws Exception {
            // 'Fresh Beef Steak' đã có slug fresh-beef-steak
            post("/api/admin/products", ADMIN, productJson("Fresh Beef Steak", "TST-003"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Slug already exists"));
        }

        @Test
        @DisplayName("salePrice > price -> 400 INVALID_SALE_PRICE")
        void salePriceGreaterThanPrice_returns400() throws Exception {
            post("/api/admin/products", ADMIN, productJson("Bad Sale", null, "TST-004", "10.00", "10.01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Sale price must not exceed the regular price"));
        }

        @Test
        @DisplayName("salePrice == price -> chấp nhận (code chỉ chặn khi LỚN HƠN)")
        void salePriceEqualsPrice_isAccepted() throws Exception {
            post("/api/admin/products", ADMIN, productJson("Equal Sale", null, "TST-005", "10.00", "10.00"))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("categoryId không tồn tại -> 404")
        void unknownCategory_returns404() throws Exception {
            String body = productJson("No Category", "TST-006").replace("\"categoryId\":2", "\"categoryId\":9999");
            post("/api/admin/products", ADMIN, body)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Category not found"));
        }

        @Test
        @DisplayName("name rỗng + thiếu sku -> 400, details chỉ ra từng field")
        void blankNameAndMissingSku_returns400WithFieldDetails() throws Exception {
            String body = "{\"name\":\"\",\"categoryId\":2,\"price\":10,\"stockQuantity\":1}";
            post("/api/admin/products", ADMIN, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[?(@.field=='name')]").exists())
                    .andExpect(jsonPath("$.details[?(@.field=='sku')]").exists());
        }

        @Test
        @DisplayName("price âm -> 400")
        void negativePrice_returns400() throws Exception {
            post("/api/admin/products", ADMIN, productJson("Neg Price", null, "TST-007", "-1", null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[?(@.field=='price')]").exists());
        }

        @Test
        @DisplayName("stockQuantity âm -> 400")
        void negativeStock_returns400() throws Exception {
            String body = productJson("Neg Stock", "TST-008").replace("\"stockQuantity\":10", "\"stockQuantity\":-5");
            post("/api/admin/products", ADMIN, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[?(@.field=='stockQuantity')]").exists());
        }

        @Test
        @DisplayName("JSON sai cú pháp -> 400")
        void malformedJson_returns400() throws Exception {
            post("/api/admin/products", ADMIN, "{ this is not json")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));
        }
    }

    // =====================================================================
    // UPDATE
    // =====================================================================
    @Nested
    @DisplayName("PUT /api/admin/products/{id}")
    class Update {

        @Test
        @DisplayName("Gửi lại chính SKU của nó -> 200 (không tự báo trùng với mình)")
        void sameSkuOfItself_isOk() throws Exception {
            put("/api/admin/products/1", ADMIN, productJson("Renamed Package", "VEG-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("Renamed Package"));
        }

        @Test
        @DisplayName("Đổi sang SKU của sản phẩm khác -> 409")
        void skuOfAnotherProduct_returns409() throws Exception {
            put("/api/admin/products/1", ADMIN, productJson("Vegetable's Package", "VEG-002"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("SKU already exists"));
        }

        @Test
        @DisplayName("Bỏ trống slug -> giữ slug cũ dù đổi tên")
        void blankSlug_keepsOldSlug() throws Exception {
            put("/api/admin/products/1", ADMIN, productJson("Completely New Name", "VEG-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.slug").value("vegetables-package"));
        }

        @Test
        @DisplayName("Đổi slug sang slug của sản phẩm khác -> 409")
        void slugOfAnotherProduct_returns409() throws Exception {
            put("/api/admin/products/1", ADMIN,
                    productJson("Vegetable's Package", "fresh-beef-steak", "VEG-001", "50.00", null))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Slug already exists"));
        }

        @Test
        @DisplayName("Không gửi salePrice -> xoá giảm giá")
        void missingSalePrice_clearsDiscount() throws Exception {
            // id=4 đang giảm 36 -> 30; category=3
            String body = productJson("Raisin'n'nuts", null, "NUT-001", "36.00", null)
                    .replace("\"categoryId\":2", "\"categoryId\":3");
            put("/api/admin/products/4", ADMIN, body)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.salePrice").doesNotExist())
                    .andExpect(jsonPath("$.data.onSale").value(false));
        }

        @Test
        @DisplayName("Đổi category -> category mới được áp dụng")
        void changeCategory() throws Exception {
            String body = productJson("Vegetable's Package", "VEG-001").replace("\"categoryId\":2", "\"categoryId\":1");
            put("/api/admin/products/1", ADMIN, body)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.categoryId").value(1));
        }

        @Test
        @DisplayName("Không gửi status -> giữ nguyên status cũ")
        void missingStatus_keepsCurrentStatus() throws Exception {
            String hidden = productJson("Vegetable's Package", "VEG-001").replace("\"stockQuantity\":10",
                    "\"stockQuantity\":10,\"status\":\"HIDDEN\"");
            put("/api/admin/products/1", ADMIN, hidden).andExpect(jsonPath("$.data.status").value("HIDDEN"));

            put("/api/admin/products/1", ADMIN, productJson("Vegetable's Package", "VEG-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("HIDDEN"));
        }

        @Test
        @DisplayName("id không tồn tại -> 404")
        void unknownId_returns404() throws Exception {
            put("/api/admin/products/999999", ADMIN, productJson("X", "TST-404"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Product not found"));
        }

        @Test
        @DisplayName("id không phải số -> 400")
        void nonNumericId_returns400() throws Exception {
            put("/api/admin/products/abc", ADMIN, productJson("X", "TST-400"))
                    .andExpect(status().isBadRequest());
        }
    }

    // =====================================================================
    // SOFT DELETE
    // =====================================================================
    @Nested
    @DisplayName("DELETE /api/admin/products/{id} (soft delete)")
    class SoftDelete {

        @Test
        @DisplayName("Xoá -> admin vẫn thấy (HIDDEN), public thì 404 và biến khỏi danh sách")
        void hidesFromPublic_butKeepsForAdmin() throws Exception {
            delete("/api/admin/products/1", ADMIN).andExpect(status().isOk());

            get("/api/admin/products/1", ADMIN)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("HIDDEN"));

            get("/api/products/vegetables-package", ANONYMOUS).andExpect(status().isNotFound());

            get("/api/products?size=100", ANONYMOUS)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[?(@.id==1)]").isEmpty());
        }

        @Test
        @DisplayName("Xoá KHÔNG làm mất order_items cũ (lý do chuyển sang soft delete)")
        void doesNotTouchOrderItems() throws Exception {
            Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM order_items WHERE product_id = 1", Integer.class);
            org.junit.jupiter.api.Assertions.assertTrue(before != null && before > 0, "seed phải có order_items của sản phẩm 1");

            delete("/api/admin/products/1", ADMIN).andExpect(status().isOk());
            em.flush(); // đẩy UPDATE (hoặc DELETE nếu ai đó lỡ đổi lại thành hard delete) xuống DB trước khi đếm

            Integer after = jdbc.queryForObject("SELECT COUNT(*) FROM order_items WHERE product_id = 1", Integer.class);
            org.junit.jupiter.api.Assertions.assertEquals(before, after);
        }

        @Test
        @DisplayName("Xoá 2 lần -> cả 2 đều 200 (idempotent)")
        void deleteTwice_isIdempotent() throws Exception {
            delete("/api/admin/products/1", ADMIN).andExpect(status().isOk());
            delete("/api/admin/products/1", ADMIN).andExpect(status().isOk());
        }

        @Test
        @DisplayName("Xoá id không tồn tại -> 404")
        void unknownId_returns404() throws Exception {
            delete("/api/admin/products/999999", ADMIN).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Khôi phục bằng PUT status=ACTIVE -> public thấy lại")
        void restoreWithPut() throws Exception {
            delete("/api/admin/products/1", ADMIN).andExpect(status().isOk());

            String restore = productJson("Vegetable's Package", "VEG-001").replace("\"stockQuantity\":10",
                    "\"stockQuantity\":10,\"status\":\"ACTIVE\"");
            put("/api/admin/products/1", ADMIN, restore).andExpect(status().isOk());

            get("/api/products/vegetables-package", ANONYMOUS).andExpect(status().isOk());
        }
    }

    // =====================================================================
    // LIST / FILTER
    // =====================================================================
    @Nested
    @DisplayName("GET danh sách / lọc / phân trang")
    class ListAndFilter {

        @Test
        @DisplayName("size > 100 -> bị chặn ở 100")
        void sizeIsCappedAt100() throws Exception {
            get("/api/admin/products?size=1000", ADMIN)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.size").value(100));
        }

        @Test
        @DisplayName("page âm -> coi như trang 0")
        void negativePage_treatedAsZero() throws Exception {
            get("/api/admin/products?page=-1", ADMIN)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.page").value(0));
        }

        @Test
        @DisplayName("minPrice > maxPrice -> rỗng, không lỗi")
        void minGreaterThanMax_returnsEmpty() throws Exception {
            get("/api/admin/products?minPrice=100&maxPrice=1", ADMIN)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(0)));
        }

        @Test
        @DisplayName("sort sai giá trị -> rơi về mặc định, không 500")
        void invalidSort_fallsBack() throws Exception {
            get("/api/admin/products?sort=hack_me", ADMIN).andExpect(status().isOk());
        }

        @Test
        @DisplayName("keyword lọc theo tên (không phân biệt hoa thường)")
        void keywordFilter() throws Exception {
            get("/api/admin/products?keyword=BEEF", ADMIN)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[?(@.sku=='MEA-001')]").exists());
        }

        @Test
        @DisplayName("sort=price_asc -> giá tăng dần")
        void sortPriceAsc_isAscending() throws Exception {
            String json = get("/api/products?sort=price_asc&size=100", ANONYMOUS)
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            java.util.List<Number> prices = com.jayway.jsonpath.JsonPath.read(json, "$.data.content[*].price");
            org.junit.jupiter.api.Assertions.assertTrue(prices.size() > 1);
            for (int i = 1; i < prices.size(); i++) {
                org.junit.jupiter.api.Assertions.assertTrue(
                        prices.get(i - 1).doubleValue() <= prices.get(i).doubleValue(),
                        "giá phải tăng dần tại vị trí " + i);
            }
        }

        @Test
        @DisplayName("Admin lọc status=HIDDEN thấy sản phẩm đã ẩn; public list thì không")
        void adminSeesHidden_publicDoesNot() throws Exception {
            delete("/api/admin/products/1", ADMIN).andExpect(status().isOk());

            get("/api/admin/products?status=HIDDEN", ADMIN)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[?(@.id==1)]").exists());

            get("/api/products?size=100", ANONYMOUS)
                    .andExpect(jsonPath("$.data.content[?(@.id==1)]").isEmpty());
        }

        @Test
        @DisplayName("status không hợp lệ -> 400")
        void invalidStatus_returns400() throws Exception {
            get("/api/admin/products?status=NOPE", ADMIN)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Invalid input data"))
                    .andExpect(jsonPath("$.details[?(@.field=='status')]").exists());
        }

        @Test
        @DisplayName("PUT thiếu field tuỳ chọn -> KHÔNG làm mất dữ liệu cũ")
        void missingOptionalFields_keepExistingValues() throws Exception {
            put("/api/admin/products/1", ADMIN, productJson("Vegetable's Package", "VEG-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.featured").value(true))
                    .andExpect(jsonPath("$.data.weightUnit").isNotEmpty())
                    .andExpect(jsonPath("$.data.shortDescription")
                            .value("Mixed seasonal vegetables, washed and packed."));
        }
    }
}
