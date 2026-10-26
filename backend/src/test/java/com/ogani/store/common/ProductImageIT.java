package com.ogani.store.catalog;

import com.ogani.store.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seed: sản phẩm id=1 có 3 ảnh (sortOrder 1,2,3); sản phẩm id=2 chưa có ảnh nào.
 */
class ProductImageIT extends AbstractIntegrationTest {

    private static final String WITH_IMAGES = "/api/admin/products/1/images";
    private static final String NO_IMAGES = "/api/admin/products/2/images";

    private static String image(String url) {
        return "{\"imageUrl\":\"" + url + "\"}";
    }

    private static String image(String url, int sortOrder) {
        return "{\"imageUrl\":\"" + url + "\",\"sortOrder\":" + sortOrder + "}";
    }

    private static String order(Long... ids) {
        StringBuilder sb = new StringBuilder("{\"imageIds\":[");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(ids[i]);
        }
        return sb.append("]}").toString();
    }

    // =====================================================================
    @Nested
    @DisplayName("GET danh sách ảnh")
    class ListImages {

        @Test
        @DisplayName("Trả đủ 3 ảnh seed theo thứ tự sortOrder")
        void returnsSeedImagesInOrder() throws Exception {
            get(WITH_IMAGES, ADMIN)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(3)))
                    .andExpect(jsonPath("$.data[0].sortOrder").value(1))
                    .andExpect(jsonPath("$.data[1].sortOrder").value(2))
                    .andExpect(jsonPath("$.data[2].sortOrder").value(3))
                    .andExpect(jsonPath("$.data[0].productId").value(1));
        }

        @Test
        @DisplayName("Sản phẩm chưa có ảnh -> mảng rỗng (không phải 404)")
        void productWithoutImages_returnsEmptyList() throws Exception {
            get(NO_IMAGES, ADMIN)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(0)));
        }

        @Test
        @DisplayName("Sản phẩm không tồn tại -> 404")
        void unknownProduct_returns404() throws Exception {
            get("/api/admin/products/999999/images", ADMIN)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Product not found"));
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("POST thêm ảnh")
    class AddImage {

        @Test
        @DisplayName("Bỏ trống sortOrder -> xếp xuống cuối (max+1 = 4)")
        void withoutSortOrder_appendsAtEnd() throws Exception {
            post(WITH_IMAGES, ADMIN, image("img/new.jpg"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.sortOrder").value(4))
                    .andExpect(jsonPath("$.data.imageUrl").value("img/new.jpg"))
                    .andExpect(jsonPath("$.data.productId").value(1));
        }

        @Test
        @DisplayName("Ảnh đầu tiên của sản phẩm -> sortOrder = 1")
        void firstImage_getsSortOrder1() throws Exception {
            post(NO_IMAGES, ADMIN, image("img/first.jpg"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.sortOrder").value(1));
        }

        @Test
        @DisplayName("Có sortOrder -> giữ nguyên giá trị đó")
        void withSortOrder_keepsValue() throws Exception {
            post(WITH_IMAGES, ADMIN, image("img/custom.jpg", 10))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.sortOrder").value(10));
        }

        @Test
        @DisplayName("imageUrl có khoảng trắng 2 đầu -> được trim")
        void urlIsTrimmed() throws Exception {
            post(WITH_IMAGES, ADMIN, image("  img/trim.jpg  "))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.imageUrl").value("img/trim.jpg"));
        }

        @Test
        @DisplayName("imageUrl rỗng -> 400, details chỉ ra imageUrl")
        void blankUrl_returns400() throws Exception {
            post(WITH_IMAGES, ADMIN, image(""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[?(@.field=='imageUrl')]").exists());
        }

        @Test
        @DisplayName("imageUrl > 255 ký tự -> 400")
        void tooLongUrl_returns400() throws Exception {
            post(WITH_IMAGES, ADMIN, image("a".repeat(256)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[?(@.field=='imageUrl')]").exists());
        }

        @Test
        @DisplayName("sortOrder âm -> 400")
        void negativeSortOrder_returns400() throws Exception {
            post(WITH_IMAGES, ADMIN, image("img/x.jpg", -1))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[?(@.field=='sortOrder')]").exists());
        }

        @Test
        @DisplayName("Sản phẩm không tồn tại -> 404")
        void unknownProduct_returns404() throws Exception {
            post("/api/admin/products/999999/images", ADMIN, image("img/x.jpg"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Sản phẩm HIDDEN: admin vẫn thêm ảnh được")
        void hiddenProduct_adminCanStillAddImage() throws Exception {
            delete("/api/admin/products/1", ADMIN).andExpect(status().isOk());
            post(WITH_IMAGES, ADMIN, image("img/hidden-ok.jpg")).andExpect(status().isCreated());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("PUT sửa ảnh")
    class UpdateImage {

        @Test
        @DisplayName("Sửa url + sortOrder")
        void updatesUrlAndSortOrder() throws Exception {
            Long imageId = imageIdsOf(1).get(0);
            put(WITH_IMAGES + "/" + imageId, ADMIN, image("img/changed.jpg", 7))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.imageUrl").value("img/changed.jpg"))
                    .andExpect(jsonPath("$.data.sortOrder").value(7));
        }

        @Test
        @DisplayName("Không gửi sortOrder -> chỉ đổi url, giữ thứ tự")
        void withoutSortOrder_keepsOrder() throws Exception {
            Long imageId = imageIdsOf(1).get(1); // ảnh có sortOrder = 2
            put(WITH_IMAGES + "/" + imageId, ADMIN, image("img/only-url.jpg"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.imageUrl").value("img/only-url.jpg"))
                    .andExpect(jsonPath("$.data.sortOrder").value(2));
        }

        @Test
        @DisplayName("Ảnh của sản phẩm KHÁC trong URL -> 404 (không sửa nhầm)")
        void imageOfAnotherProduct_returns404() throws Exception {
            Long imageOfProduct1 = imageIdsOf(1).get(0);
            put(NO_IMAGES + "/" + imageOfProduct1, ADMIN, image("img/hack.jpg"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Product image not found"));

            // ảnh gốc không bị đổi
            get(WITH_IMAGES, ADMIN).andExpect(jsonPath("$.data[0].imageUrl").value("img/product/details/product-details-1.jpg"));
        }

        @Test
        @DisplayName("imageId không tồn tại -> 404")
        void unknownImage_returns404() throws Exception {
            put(WITH_IMAGES + "/999999", ADMIN, image("img/x.jpg")).andExpect(status().isNotFound());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("DELETE xoá ảnh")
    class DeleteImage {

        @Test
        @DisplayName("Xoá lần 1 -> 200, lần 2 -> 404; danh sách còn 2 ảnh")
        void deleteTwice() throws Exception {
            Long imageId = imageIdsOf(1).get(0);
            delete(WITH_IMAGES + "/" + imageId, ADMIN).andExpect(status().isOk());
            delete(WITH_IMAGES + "/" + imageId, ADMIN).andExpect(status().isNotFound());
            get(WITH_IMAGES, ADMIN).andExpect(jsonPath("$.data", hasSize(2)));
        }

        @Test
        @DisplayName("Ảnh của sản phẩm khác -> 404 và ảnh không bị xoá")
        void imageOfAnotherProduct_returns404() throws Exception {
            Long imageOfProduct1 = imageIdsOf(1).get(0);
            delete(NO_IMAGES + "/" + imageOfProduct1, ADMIN).andExpect(status().isNotFound());
            get(WITH_IMAGES, ADMIN).andExpect(jsonPath("$.data", hasSize(3)));
        }

        @Test
        @DisplayName("Xoá ảnh trùng thumbnailUrl -> thumbnail của sản phẩm KHÔNG đổi")
        void deletingImage_doesNotChangeProductThumbnail() throws Exception {
            String thumbnailBefore = com.jayway.jsonpath.JsonPath.read(
                    get("/api/admin/products/1", ADMIN).andReturn().getResponse().getContentAsString(),
                    "$.data.thumbnailUrl");
            Long imageId = imageIdsOf(1).get(0);
            delete(WITH_IMAGES + "/" + imageId, ADMIN).andExpect(status().isOk());
            get("/api/admin/products/1", ADMIN).andExpect(jsonPath("$.data.thumbnailUrl").value(thumbnailBefore));
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("PUT /order - sắp xếp lại gallery")
    class Reorder {

        @Test
        @DisplayName("Đảo thứ tự hợp lệ -> GET lại đúng thứ tự mới, sortOrder = 1,2,3")
        void validOrder_isApplied() throws Exception {
            List<Long> ids = imageIdsOf(1);
            Long a = ids.get(0), b = ids.get(1), c = ids.get(2);

            put(WITH_IMAGES + "/order", ADMIN, order(c, a, b))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].id").value(c))
                    .andExpect(jsonPath("$.data[0].sortOrder").value(1))
                    .andExpect(jsonPath("$.data[2].sortOrder").value(3));

            assertEquals(List.of(c, a, b), imageIdsOf(1));
        }

        @Test
        @DisplayName("Thiếu 1 id -> 400")
        void missingId_returns400() throws Exception {
            List<Long> ids = imageIdsOf(1);
            put(WITH_IMAGES + "/order", ADMIN, order(ids.get(0), ids.get(1)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Thừa id không thuộc sản phẩm -> 400")
        void extraId_returns400() throws Exception {
            List<Long> ids = imageIdsOf(1);
            put(WITH_IMAGES + "/order", ADMIN, order(ids.get(0), ids.get(1), ids.get(2), 999999L))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Id lặp -> 400")
        void duplicateId_returns400() throws Exception {
            List<Long> ids = imageIdsOf(1);
            put(WITH_IMAGES + "/order", ADMIN, order(ids.get(0), ids.get(0), ids.get(1)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Mảng rỗng -> 400")
        void emptyList_returns400() throws Exception {
            put(WITH_IMAGES + "/order", ADMIN, order())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[?(@.field=='imageIds')]").exists());
        }

        @Test
        @DisplayName("Dùng id ảnh của sản phẩm khác -> 400, thứ tự cũ không đổi")
        void imageIdsOfAnotherProduct_returns400() throws Exception {
            List<Long> before = imageIdsOf(1);
            post(NO_IMAGES, ADMIN, image("img/p2.jpg")).andExpect(status().isCreated());
            Long foreign = imageIdsOf(2).get(0);

            put(WITH_IMAGES + "/order", ADMIN, order(before.get(0), before.get(1), foreign))
                    .andExpect(status().isBadRequest());

            assertEquals(before, imageIdsOf(1));
        }

        @Test
        @DisplayName("Sản phẩm không tồn tại -> 404")
        void unknownProduct_returns404() throws Exception {
            put("/api/admin/products/999999/images/order", ADMIN, order(1L)).andExpect(status().isNotFound());
        }
    }
}
