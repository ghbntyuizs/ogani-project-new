package com.ogani.store.support;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nền chung cho integration test (chạy bằng `mvn verify` hoặc bấm chạy trong IntelliJ).
 *
 * - Chạy trên Postgres LOCAL, database riêng `ogani_store_test` (cấu hình ở src/test/resources/application-test.yml),
 *   tách khỏi database dev `ogani_store` nên không đụng dữ liệu bạn đang dùng. Không cần Docker.
 * - Flyway tự chạy V1..V3 ở lần đầu nên có sẵn seed data (users, 12 sản phẩm, 3 ảnh của sản phẩm id=1...).
 * - @Transactional: mỗi test tự rollback -> seed data luôn nguyên vẹn giữa các test, không cần dọn dẹp.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Tag("integration")
public abstract class AbstractIntegrationTest {

    // Tài khoản seed (mật khẩu chung 123456 - xem README)
    protected static final String ADMIN = "admin@ogani.local";
    protected static final String STAFF = "staff@ogani.local";
    protected static final String CUSTOMER = "eleanor@example.com";
    protected static final String ANONYMOUS = null;

    private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();

    @Autowired
    protected MockMvc mvc;

    // ---------- helper gửi request: truyền email để đăng nhập, hoặc ANONYMOUS để không gửi token ----------

    protected ResultActions get(String url, String as) throws Exception {
        return send(MockMvcRequestBuilders.get(url), as);
    }

    protected ResultActions post(String url, String as, String body) throws Exception {
        return send(MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(body), as);
    }

    protected ResultActions put(String url, String as, String body) throws Exception {
        return send(MockMvcRequestBuilders.put(url).contentType(MediaType.APPLICATION_JSON).content(body), as);
    }

    protected ResultActions delete(String url, String as) throws Exception {
        return send(MockMvcRequestBuilders.delete(url), as);
    }

    /** Gửi với token KHÔNG hợp lệ (để test 401). */
    protected ResultActions getWithRawAuthHeader(String url, String authorizationHeader) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(url).header(HttpHeaders.AUTHORIZATION, authorizationHeader));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String as) throws Exception {
        if (as != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOf(as));
        }
        return mvc.perform(request);
    }

    private String tokenOf(String email) throws Exception {
        String cached = TOKENS.get(email);
        if (cached != null) {
            return cached;
        }
        String body = "{\"email\":\"" + email + "\",\"password\":\"123456\"}";
        String response = mvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(response, "$.data.accessToken");
        TOKENS.put(email, token);
        return token;
    }

    // ---------- helper dựng JSON / đọc JSON ----------

    /** salePrice = null -> không gửi field. slug = null -> không gửi field (để tự sinh). categoryId mặc định 2 (Vegetables). */
    protected static String productJson(String name, String slug, String sku, String price, String salePrice) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"name\":\"").append(name).append("\",");
        if (slug != null) sb.append("\"slug\":\"").append(slug).append("\",");
        sb.append("\"sku\":\"").append(sku).append("\",");
        sb.append("\"categoryId\":2,");
        sb.append("\"price\":").append(price).append(",");
        if (salePrice != null) sb.append("\"salePrice\":").append(salePrice).append(",");
        sb.append("\"stockQuantity\":10}");
        return sb.toString();
    }

    protected static String productJson(String name, String sku) {
        return productJson(name, null, sku, "10.00", null);
    }

    /** Lấy danh sách id ảnh (đã sắp theo sortOrder) của 1 sản phẩm qua API admin. */
    protected List<Long> imageIdsOf(long productId) throws Exception {
        String json = get("/api/admin/products/" + productId + "/images", ADMIN)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(json, "$.data[*].id");
        return ids.stream().map(Number::longValue).toList();
    }
}
