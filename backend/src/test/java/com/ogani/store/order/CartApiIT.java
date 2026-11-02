package com.ogani.store.order;

import com.ogani.store.common.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CartApiIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Test void cartUsesSuccessEnvelopeWithoutCreatingRows() throws Exception {
        var before = jdbc.queryForObject("select count(*) from carts where user_id=2", Long.class);
        get("/api/cart", STAFF).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.items").isArray());
        assertEquals(before, jdbc.queryForObject("select count(*) from carts where user_id=2", Long.class));
        get("/api/cart", ANONYMOUS).andExpect(status().isUnauthorized());
    }
}
