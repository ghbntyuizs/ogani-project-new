package com.ogani.store.catalog;

import com.ogani.store.common.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductConcurrencyIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;

    private String updateJson(String name, long version) {
        var json = productJson(name, null, "VEG-001", "50", null);
        return json.substring(0, json.length() - 1) + ",\"version\":" + version + "}";
    }

    @Test void staleFormCannotOverwriteAnotherAdminsSave() throws Exception {
        long version = jdbc.queryForObject("select version from products where id=1", Long.class);
        put("/api/admin/products/1", ADMIN, updateJson("First admin saved", version))
                .andExpect(status().isOk());
        put("/api/admin/products/1", STAFF, updateJson("Stale second admin", version))
                .andExpect(status().isConflict());
        get("/api/admin/products/1", ADMIN).andExpect(jsonPath("$.data.name").value("First admin saved"));
    }

    @Test void detailExposesCurrentVersionAndUpdateReturnsNextVersion() throws Exception {
        long version = jdbc.queryForObject("select version from products where id=1", Long.class);
        get("/api/admin/products/1", ADMIN).andExpect(jsonPath("$.data.version").value(version));
        put("/api/admin/products/1", ADMIN, updateJson("Changed for version", version))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(version + 1));
    }

    @Test void missingVersionIsRejected() throws Exception {
        // Use the raw request path so shared read-then-edit test helpers cannot supply a version.
        mvc.perform(MockMvcRequestBuilders.put("/api/admin/products/1")
                .with(user("admin").roles("ADMIN"))
                .contentType("application/json").content(productJson("Missing version", "VEG-001")))
                .andExpect(status().isBadRequest());
    }
}
