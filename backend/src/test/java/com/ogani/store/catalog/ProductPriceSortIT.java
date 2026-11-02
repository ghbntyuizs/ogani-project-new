package com.ogani.store.catalog;

import com.ogani.store.common.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductPriceSortIT extends AbstractIntegrationTest {
    @Test void sortsBySalePriceRatherThanRegularPrice() throws Exception {
        post("/api/admin/products", ADMIN, productJson("PriceSort discounted", "price-sort-discounted", "SORT-D", "100", "1"))
                .andExpect(status().isCreated());
        post("/api/admin/products", ADMIN, productJson("PriceSort regular", "price-sort-regular", "SORT-R", "20", null))
                .andExpect(status().isCreated());
        get("/api/products?keyword=PriceSort&sort=price_asc", ANONYMOUS)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].sku").value("SORT-D"));
        get("/api/products?keyword=PriceSort&sort=price_desc", ANONYMOUS)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].sku").value("SORT-R"));
    }
}
