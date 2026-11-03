package com.ogani.store.order;

import com.ogani.store.common.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.springframework.test.web.servlet.ResultActions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CartAddItemIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void prepare() {
        jdbc.update("delete from carts where user_id in (select id from users where email in (?, ?))", CUSTOMER, STAFF);
        jdbc.update("update products set status='ACTIVE', stock_quantity=10, price=20, sale_price=15 where id=1");
        jdbc.update("update categories set is_active=true where id=(select category_id from products where id=1)");
    }

    @Test void addsAndAccumulatesUsingServerSalePriceWithoutChangingInventory() throws Exception {
        var version = jdbc.queryForObject("select version from products where id=1", Long.class);
        add(1, 2).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].productId").value(1))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(15))
                .andExpect(jsonPath("$.data.total").value(30));
        add(1, 8).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].quantity").value(10))
                .andExpect(jsonPath("$.data.subtotal").value(150))
                .andExpect(jsonPath("$.data.discountAmount").value(0))
                .andExpect(jsonPath("$.data.shippingFee").value(0));
        assertEquals(10, jdbc.queryForObject("select stock_quantity from products where id=1", Integer.class));
        assertEquals(version, jdbc.queryForObject("select version from products where id=1", Long.class));
    }

    @Test void excessiveCumulativeQuantityLeavesExistingItemUnchanged() throws Exception {
        add(1, 8).andExpect(status().isOk());
        add(1, 3).andExpect(status().isConflict());
        get("/api/cart", CUSTOMER).andExpect(jsonPath("$.data.items[0].quantity").value(8));
    }

    @Test void overflowCannotBecomeNegativeQuantity() throws Exception {
        jdbc.update("update products set stock_quantity=2147483647 where id=1");
        add(1, Integer.MAX_VALUE).andExpect(status().isOk());
        add(1, 1).andExpect(status().isConflict());
        get("/api/cart", CUSTOMER).andExpect(jsonPath("$.data.items[0].quantity").value(Integer.MAX_VALUE));
    }

    @Test void normalPriceAndMultipleProductsUseCorrectTotals() throws Exception {
        jdbc.update("update products set sale_price=null where id=1");
        jdbc.update("update products set status='ACTIVE', stock_quantity=10, price=7, sale_price=null where id=2");
        add(1, 2).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(40));
        add(2, 3).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.total").value(61));
    }

    @Test void ownershipComesFromPrincipalAndCartsAreIsolated() throws Exception {
        post("/api/cart/items", CUSTOMER, "{\"productId\":1,\"quantity\":2,\"userId\":2,\"cartId\":1,\"unitPrice\":0}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(30));
        get("/api/cart", STAFF).andExpect(jsonPath("$.data.items").isEmpty());
        post("/api/cart/items", STAFF, "{\"productId\":1,\"quantity\":3}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].quantity").value(3));
        get("/api/cart", CUSTOMER).andExpect(jsonPath("$.data.items[0].quantity").value(2));
    }

    @ParameterizedTest
    @ValueSource(strings={"{}", "{\"productId\":1}", "{\"productId\":null,\"quantity\":1}",
            "{\"productId\":1,\"quantity\":null}", "{\"productId\":0,\"quantity\":1}",
            "{\"productId\":-1,\"quantity\":1}", "{\"productId\":1,\"quantity\":0}",
            "{\"productId\":1,\"quantity\":-1}", "{\"productId\":1,\"quantity\":2147483648}",
            "{\"productId\":1,\"quantity\":1.5}", "{\"productId\":1.5,\"quantity\":1}",
            "{\"productId\":9223372036854775808,\"quantity\":1}",
            "{\"productId\":1,\"quantity\":true}", "{\"productId\":1,\"quantity\":\"2\"}",
            "{\"productId\":1,\"quantity\":\"oops\"}", "[1,2]", "broken", ""})
    void invalidRequestsAreRejectedWithoutCreatingCart(String body) throws Exception {
        post("/api/cart/items", CUSTOMER, body).andExpect(status().isBadRequest());
        assertNoCart();
    }

    @Test void cartReadUsesCurrentPriceAfterCatalogChanges() throws Exception {
        add(1, 2).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(30));
        jdbc.update("update products set sale_price=null, price=25 where id=1");
        entityManager.clear();
        get("/api/cart", CUSTOMER).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(25))
                .andExpect(jsonPath("$.data.total").value(50));
    }

    @ParameterizedTest @ValueSource(strings={"HIDDEN", "OUT_OF_STOCK"})
    void unavailableStatusIsRejected(String state) throws Exception {
        jdbc.update("update products set status=? where id=1", state);
        add(1, 1).andExpect(status().isConflict());
        assertNoCart();
    }

    @Test void inactiveCategoryIsRejected() throws Exception {
        jdbc.update("update categories set is_active=false where id=(select category_id from products where id=1)");
        add(1, 1).andExpect(status().isConflict());
        assertNoCart();
    }

    @Test void insufficientStockDoesNotCreateCart() throws Exception {
        add(1, 11).andExpect(status().isConflict());
        assertNoCart();
        jdbc.update("update products set stock_quantity=0 where id=1");
        entityManager.clear();
        add(1, 1).andExpect(status().isConflict());
        assertNoCart();
    }

    @Test void missingProductDoesNotCreateCart() throws Exception {
        add(Long.MAX_VALUE, 1).andExpect(status().isNotFound());
        assertNoCart();
    }

    @Test void unavailableExistingItemIsPreservedByReadAndRejectedByAdd() throws Exception {
        add(1, 2).andExpect(status().isOk());
        // Clear persistence context so the next simulated request sees the database update.
        jdbc.update("update products set status='HIDDEN', stock_quantity=0 where id=1");
        entityManager.clear();
        add(1, 1).andExpect(status().isConflict());
        get("/api/cart", CUSTOMER).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].quantity").value(2));
    }

    @Autowired EntityManager entityManager;

    @Test void anonymousAndBlockedAccountsCannotAdd() throws Exception {
        post("/api/cart/items", ANONYMOUS, "{\"productId\":1,\"quantity\":1}").andExpect(status().isUnauthorized());
        // Prime token before blocking so this exercises a previously-issued access token.
        get("/api/cart", CUSTOMER).andExpect(status().isOk());
        jdbc.update("update users set status='BLOCKED' where email=?", CUSTOMER);
        entityManager.clear();
        add(1, 1).andExpect(status().isUnauthorized());
        assertNoCart();
    }

    private ResultActions add(long id, int quantity) throws Exception {
        return post("/api/cart/items", CUSTOMER, "{\"productId\":"+id+",\"quantity\":"+quantity+"}");
    }

    private void assertNoCart() {
        assertEquals(0, jdbc.queryForObject("select count(*) from carts where user_id=(select id from users where email=?)", Integer.class, CUSTOMER));
    }
}
