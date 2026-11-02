package com.ogani.store.catalog;

import com.ogani.store.common.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.ogani.store.catalog.ProductListPayloadIT$SqlCapture")
class ProductListPayloadIT extends AbstractIntegrationTest {
    public static class SqlCapture implements StatementInspector {
        static final List<String> SQL = new CopyOnWriteArrayList<>();
        @Override public String inspect(String sql) { SQL.add(sql); return sql; }
    }
    @Autowired JdbcTemplate jdbc;
    @Test void listOmitsLongContentButDetailRetainsIt() throws Exception {
        String content = "Long description ".repeat(1000);
        jdbc.update("update products set description=?, information=? where id=1", content, content);
        for (var url : new String[]{"/api/products?keyword=Package", "/api/admin/products?keyword=Package"}) {
            SqlCapture.SQL.clear();
            get(url, url.contains("/admin/") ? ADMIN : ANONYMOUS).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].id").value(1))
                    .andExpect(jsonPath("$.data.content[0].price").isNumber())
                    .andExpect(jsonPath("$.data.content[0].description").doesNotExist())
                    .andExpect(jsonPath("$.data.content[0].information").doesNotExist());
            var queries = SqlCapture.SQL.stream().filter(sql -> sql.contains(" from products ")).toList();
            assertFalse(queries.isEmpty(), "List must issue a product projection query");
            assertTrue(queries.stream().noneMatch(sql -> sql.contains(".description") || sql.contains(".information")),
                    "List SQL must not select long product content columns");
        }
        get("/api/products/vegetables-package", ANONYMOUS).andExpect(jsonPath("$.data.description").value(content));
    }
}
