package com.ogani.store.catalog;

import com.ogani.store.common.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductSlugValidationIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @ParameterizedTest @ValueSource(strings={"\"name\":\"!!!\"","\"name\":\"Valid name\",\"slug\":\"!!!\""})
    void creatingAnEmptyNormalizedSlugFailsWithoutSaving(String fields) throws Exception {
        long count=jdbc.queryForObject("select count(*) from products",Long.class);
        post("/api/admin/products",ADMIN,"{"+fields+",\"sku\":\"SLUG-INVALID\",\"categoryId\":2,\"price\":10,\"stockQuantity\":1}")
                .andExpect(status().isBadRequest());
        assertEquals(count,jdbc.queryForObject("select count(*) from products",Long.class));
    }

    @Test void updatingToAnEmptyNormalizedSlugPreservesOriginalProduct() throws Exception {
        var before=jdbc.queryForMap("select slug,sku,name,version from products where id=1");
        put("/api/admin/products/1",ADMIN,"{\"name\":\"Changed\",\"slug\":\"!!!\",\"sku\":\"SLUG-INVALID\",\"categoryId\":2,\"price\":10,\"stockQuantity\":1}")
                .andExpect(status().isBadRequest());
        assertEquals(before,jdbc.queryForMap("select slug,sku,name,version from products where id=1"));
    }
}
