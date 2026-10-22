package com.ogani.store.catalog;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Chạy Flyway V1-V3 trên PostgreSQL thật rồi gọi API: kiểm chứng đồng thời schema, mapping entity và phân quyền.
 * Cần Docker. Chạy bằng: mvn verify
 * Boot 4: không dùng Jackson ở test (Jackson 3 đổi package) -> đọc token bằng JsonPath.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProductApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired MockMvc mvc;

    private String login(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private static final String NEW_PRODUCT = """
            {"name":"IT Test Kale","sku":"IT-001","categoryId":2,"price":9.50,"stockQuantity":5}""";

    @Test
    void publicListReturnsSeededProducts() throws Exception {
        mvc.perform(get("/api/products?size=50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(12)));
    }

    @Test
    void publicFilterOnSaleAndSort() throws Exception {
        mvc.perform(get("/api/products?onSale=true&sort=price_asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].onSale").value(true));
    }

    @Test
    void detailBySlug() throws Exception {
        mvc.perform(get("/api/products/vegetables-package"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sku").value("VEG-001"));
    }

    @Test
    void adminEndpointsRequireAuthentication() throws Exception {
        mvc.perform(post("/api/admin/products").contentType(MediaType.APPLICATION_JSON).content(NEW_PRODUCT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotCreateProduct() throws Exception {
        mvc.perform(post("/api/admin/products").header("Authorization", "Bearer " + login("eleanor@example.com"))
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_PRODUCT))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateThenStaffCannotDelete() throws Exception {
        String admin = login("admin@ogani.local");
        mvc.perform(post("/api/admin/products").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_PRODUCT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.slug").value("it-test-kale"));

        mvc.perform(delete("/api/admin/products/1").header("Authorization", "Bearer " + login("staff@ogani.local")))
                .andExpect(status().isForbidden());
    }

    @Test
    void validationErrorsAreReported() throws Exception {
        mvc.perform(post("/api/admin/products").header("Authorization", "Bearer " + login("admin@ogani.local"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }
}
