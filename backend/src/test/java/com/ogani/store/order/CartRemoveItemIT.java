package com.ogani.store.order;

import com.jayway.jsonpath.JsonPath;
import com.ogani.store.common.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CartRemoveItemIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    private long itemId;
    private long cartId;

    @BeforeEach void prepare() throws Exception {
        jdbc.update("delete from carts where user_id in (select id from users where email in (?,?))", CUSTOMER, STAFF);
        jdbc.update("update products set status='ACTIVE',stock_quantity=10,price=20,sale_price=15 where id=1");
        jdbc.update("update categories set is_active=true where id=(select category_id from products where id=1)");
        String json = post("/api/cart/items", CUSTOMER, "{\"productId\":1,\"quantity\":4}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        itemId = ((Number) JsonPath.read(json, "$.data.items[0].itemId")).longValue();
        cartId = ((Number) JsonPath.read(json, "$.data.cartId")).longValue();
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void removesLastItemAndReturnsEmptyCartWithoutChangingCatalog(boolean useDelete) throws Exception {
        var product = jdbc.queryForMap("select stock_quantity,version,sold_count,status from products where id=1");
        LocalDateTime old = LocalDateTime.of(2000,1,1,0,0);
        jdbc.update("update carts set updated_at=? where id=?", old, cartId);
        entityManager.clear();
        remove(useDelete, CUSTOMER, itemId).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cartId").value(cartId))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.subtotal").value(0))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.shippingFee").value(0))
                .andExpect(jsonPath("$.data.discountAmount").value(0));
        assertEquals(0, jdbc.queryForObject("select count(*) from cart_items where cart_id=?", Integer.class, cartId));
        assertTrue(jdbc.queryForObject("select updated_at from carts where id=?", LocalDateTime.class, cartId).isAfter(old));
        assertEquals(product, jdbc.queryForMap("select stock_quantity,version,sold_count,status from products where id=1"));
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void removesUnavailableItemsRegardlessOfStockOrCategory(boolean useDelete) throws Exception {
        jdbc.update("update products set status='HIDDEN',stock_quantity=0 where id=1");
        jdbc.update("update categories set is_active=false where id=(select category_id from products where id=1)");
        entityManager.clear();
        remove(useDelete, CUSTOMER, itemId).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        assertEquals("HIDDEN", jdbc.queryForObject("select status from products where id=1", String.class));
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void removesOutOfStockItems(boolean useDelete) throws Exception {
        jdbc.update("update products set status='OUT_OF_STOCK' where id=1");
        entityManager.clear();
        remove(useDelete, CUSTOMER, itemId).andExpect(status().isOk());
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void preservesOtherItemsAndRecalculatesTotal(boolean useDelete) throws Exception {
        jdbc.update("update products set status='ACTIVE',stock_quantity=10,price=7,sale_price=null where id=2");
        post("/api/cart/items", CUSTOMER, "{\"productId\":2,\"quantity\":2}").andExpect(status().isOk());
        remove(useDelete, CUSTOMER, itemId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(2))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.total").value(14));
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void foreignAndMissingItemsReturn404WithoutCreatingCart(boolean useDelete) throws Exception {
        remove(useDelete, STAFF, itemId).andExpect(status().isNotFound());
        remove(useDelete, CUSTOMER, Long.MAX_VALUE).andExpect(status().isNotFound());
        assertEquals(4, jdbc.queryForObject("select quantity from cart_items where id=?", Integer.class, itemId));
        assertEquals(0, jdbc.queryForObject("select count(*) from carts where user_id=(select id from users where email=?)", Integer.class, STAFF));
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void anonymousAndBlockedAccountsCannotRemove(boolean useDelete) throws Exception {
        remove(useDelete, ANONYMOUS, itemId).andExpect(status().isUnauthorized());
        jdbc.update("update users set status='BLOCKED' where email=?", CUSTOMER);
        entityManager.clear();
        remove(useDelete, CUSTOMER, itemId).andExpect(status().isUnauthorized());
        assertEquals(4, jdbc.queryForObject("select quantity from cart_items where id=?", Integer.class, itemId));
    }

    @ParameterizedTest @ValueSource(strings={"0","-1","1.5","oops","9223372036854775808"})
    void deleteRejectsInvalidItemIds(String id) throws Exception {
        delete("/api/cart/items/"+id, CUSTOMER).andExpect(status().isBadRequest());
        assertEquals(4, jdbc.queryForObject("select quantity from cart_items where id=?", Integer.class, itemId));
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void repeatRemovalDoesNotDeleteANewlyAddedReplacement(boolean useDelete) throws Exception {
        remove(useDelete, CUSTOMER, itemId).andExpect(status().isOk());
        remove(useDelete, CUSTOMER, itemId).andExpect(status().isNotFound());
        post("/api/cart/items", CUSTOMER, "{\"productId\":1,\"quantity\":2}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].quantity").value(2));
        remove(useDelete, CUSTOMER, itemId).andExpect(status().isNotFound());
        get("/api/cart", CUSTOMER).andExpect(jsonPath("$.data.items[0].quantity").value(2));
    }

    private ResultActions remove(boolean useDelete, String as, long id) throws Exception {
        return useDelete ? delete("/api/cart/items/"+id, as)
                : put("/api/cart/items/"+id, as, "{\"quantity\":0}");
    }
}
