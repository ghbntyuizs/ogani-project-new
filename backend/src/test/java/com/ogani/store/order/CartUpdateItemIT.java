package com.ogani.store.order;

import com.jayway.jsonpath.JsonPath;
import com.ogani.store.common.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CartUpdateItemIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    private long itemId;

    @BeforeEach void prepare() throws Exception {
        jdbc.update("delete from carts where user_id in (select id from users where email in (?,?))", CUSTOMER, STAFF);
        jdbc.update("update products set status='ACTIVE', stock_quantity=10, price=20, sale_price=15 where id=1");
        jdbc.update("update categories set is_active=true where id=(select category_id from products where id=1)");
        String body = post("/api/cart/items", CUSTOMER, "{\"productId\":1,\"quantity\":4}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        itemId = ((Number) JsonPath.read(body, "$.data.items[0].itemId")).longValue();
    }

    @Test void replacesInsteadOfAddingAndCanReduceOrRepeat() throws Exception {
        for (int quantity : new int[]{7, 2, 2, 10}) {
            update(quantity).andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.items.length()").value(1))
                    .andExpect(jsonPath("$.data.items[0].quantity").value(quantity))
                    .andExpect(jsonPath("$.data.total").value(quantity * 15));
        }
    }

    @Test void excessiveQuantityDoesNotChangeItem() throws Exception {
        update(11).andExpect(status().isConflict());
        assertQuantity(4);
    }

    @Test void overStockItemCanBeReducedToAvailableStock() throws Exception {
        jdbc.update("update products set stock_quantity=2 where id=1");
        entityManager.clear();
        update(3).andExpect(status().isConflict());
        update(2).andExpect(status().isOk());
        assertQuantity(2);
    }

    @Test void responseUsesCurrentPriceAndPreservesProductAndOtherItems() throws Exception {
        var original = jdbc.queryForMap("select stock_quantity,version,sold_count from products where id=1");
        jdbc.update("update products set status='ACTIVE', stock_quantity=10, price=7, sale_price=null where id=2");
        post("/api/cart/items", CUSTOMER, "{\"productId\":2,\"quantity\":2}").andExpect(status().isOk());
        jdbc.update("update products set price=25,sale_price=null where id=1");
        entityManager.clear();
        update(3).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.subtotal").value(89))
                .andExpect(jsonPath("$.data.total").value(89))
                .andExpect(jsonPath("$.data.discountAmount").value(0))
                .andExpect(jsonPath("$.data.shippingFee").value(0));
        assertEquals(original, jdbc.queryForMap("select stock_quantity,version,sold_count from products where id=1"));
        assertEquals(2, jdbc.queryForObject("select quantity from cart_items where product_id=2 and cart_id=(select cart_id from cart_items where id=?)", Integer.class, itemId));
    }

    @Test void foreignItemIsNotFoundEvenWithSpoofedFields() throws Exception {
        put(path(), STAFF, "{\"quantity\":2,\"userId\":3,\"productId\":1}").andExpect(status().isNotFound());
        assertQuantity(4);
        assertEquals(0, jdbc.queryForObject("select count(*) from carts where user_id=(select id from users where email=?)", Integer.class, STAFF));
    }

    @Test void spoofedProductAndPriceCannotReassignAnOwnedItem() throws Exception {
        put(path(), CUSTOMER, "{\"quantity\":2,\"productId\":2,\"unitPrice\":0,\"cartId\":999}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].productId").value(1))
                .andExpect(jsonPath("$.data.total").value(30));
    }

    @Test void missingItemAndMissingCartDoNotCreateRecords() throws Exception {
        put("/api/cart/items/9223372036854775807", CUSTOMER, "{\"quantity\":2}").andExpect(status().isNotFound());
        jdbc.update("delete from carts where id=(select cart_id from cart_items where id=?)", itemId);
        entityManager.clear();
        update(2).andExpect(status().isNotFound());
        assertEquals(0, jdbc.queryForObject("select count(*) from carts where user_id=(select id from users where email=?)", Integer.class, CUSTOMER));
    }

    @ParameterizedTest @ValueSource(strings={"{}", "{\"quantity\":null}",
            "{\"quantity\":-1}", "{\"quantity\":2147483648}", "{\"quantity\":2.5}",
            "{\"quantity\":\"2\"}", "{\"quantity\":true}", "[]", "null", "broken", ""})
    void invalidBodyLeavesItemUnchanged(String body) throws Exception {
        put(path(), CUSTOMER, body).andExpect(status().isBadRequest());
        assertQuantity(4);
    }

    @ParameterizedTest @ValueSource(strings={"0", "-1", "1.5", "oops", "9223372036854775808"})
    void invalidItemIdIsRejected(String id) throws Exception {
        put("/api/cart/items/"+id, CUSTOMER, "{\"quantity\":2}").andExpect(status().isBadRequest());
        assertQuantity(4);
    }

    @ParameterizedTest @ValueSource(strings={"HIDDEN", "OUT_OF_STOCK", "ZERO_STOCK", "INACTIVE_CATEGORY"})
    void unavailableItemRejectsEvenSameQuantity(String state) throws Exception {
        if (state.equals("INACTIVE_CATEGORY")) {
            jdbc.update("update categories set is_active=false where id=(select category_id from products where id=1)");
        } else if (state.equals("ZERO_STOCK")) {
            jdbc.update("update products set stock_quantity=0 where id=1");
        } else jdbc.update("update products set status=? where id=1", state);
        entityManager.clear();
        update(4).andExpect(status().isConflict());
        assertQuantity(4);
        get("/api/cart", CUSTOMER).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].quantity").value(4));
    }

    @Test void anonymousAndBlockedAccountsCannotUpdate() throws Exception {
        put(path(), ANONYMOUS, "{\"quantity\":2}").andExpect(status().isUnauthorized());
        jdbc.update("update users set status='BLOCKED' where email=?", CUSTOMER);
        entityManager.clear();
        update(2).andExpect(status().isUnauthorized());
        assertQuantity(4);
    }

    @Test void addStillAccumulatesAfterAbsoluteUpdate() throws Exception {
        update(2).andExpect(status().isOk());
        post("/api/cart/items", CUSTOMER, "{\"productId\":1,\"quantity\":3}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].quantity").value(5));
    }

    private String path() { return "/api/cart/items/"+itemId; }
    private ResultActions update(int quantity) throws Exception {
        return put(path(), CUSTOMER, "{\"quantity\":"+quantity+"}");
    }
    private void assertQuantity(int expected) {
        assertEquals(expected, jdbc.queryForObject("select quantity from cart_items where id=?", Integer.class, itemId));
    }
}
