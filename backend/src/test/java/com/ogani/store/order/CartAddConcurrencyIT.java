package com.ogani.store.order;

import com.jayway.jsonpath.JsonPath;
import com.ogani.store.common.AbstractIntegrationTest;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CartAddConcurrencyIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactionManager;
    private Long userId;
    private Long productId;
    private Integer categoryId;
    private UserPrincipal principal;

    @BeforeEach void fixtures() {
        assertTrue(jdbc.queryForObject("select current_database()", String.class).endsWith("_test"),
                "Committed concurrency tests require a dedicated *_test database");
        String suffix = UUID.randomUUID().toString();
        userId = jdbc.queryForObject("insert into users(role_id,email,password_hash,first_name,last_name) "
                + "select role_id,?,password_hash,'Cart','Concurrency' from users where email=? returning id",
                Long.class, "cart-" + suffix + "@example.test", CUSTOMER);
        principal = UserPrincipal.from(users.findById(userId).orElseThrow());
        categoryId = jdbc.queryForObject("insert into categories(name,slug) values('Cart test',?) returning id", Integer.class, suffix);
        productId = jdbc.queryForObject("insert into products(category_id,name,slug,sku,price,stock_quantity) "
                + "values(?,'Cart concurrency',?,?,20,10) returning id", Long.class, categoryId, suffix, suffix);
    }

    @AfterEach void cleanup() {
        if (userId != null) jdbc.update("delete from users where id=?", userId);
        if (productId != null) jdbc.update("delete from products where id=?", productId);
        if (categoryId != null) jdbc.update("delete from categories where id=?", categoryId);
    }

    @Test void concurrentFirstAddsCreateOneCartAndAccumulate() throws Exception {
        assertEquals(List.of(200, 200), concurrentAdds(2, 3).stream().sorted().toList());
        assertStoredQuantity(5);
        assertEquals(1, jdbc.queryForObject("select count(*) from carts where user_id=?", Integer.class, userId));
        assertEquals(1, jdbc.queryForObject("select count(*) from cart_items where cart_id=(select id from carts where user_id=?)", Integer.class, userId));
        assertEquals(10, jdbc.queryForObject("select stock_quantity from products where id=?", Integer.class, productId));
        assertEquals(0L, jdbc.queryForObject("select version from products where id=?", Long.class, productId));
    }

    @Test void concurrentExistingAddsDoNotLoseIncrements() throws Exception {
        assertEquals(200, add(1));
        assertEquals(List.of(200, 200), concurrentAdds(2, 3).stream().sorted().toList());
        assertStoredQuantity(6);
    }

    @Test void competingAddsCannotExceedStock() throws Exception {
        assertEquals(List.of(200, 409), concurrentAdds(6, 6).stream().sorted().toList());
        assertStoredQuantity(6);
    }

    @Test void failureRollsBackAndNextValidRequestCanCreateCart() throws Exception {
        assertEquals(409, add(11));
        assertEquals(0, jdbc.queryForObject("select count(*) from carts where user_id=?", Integer.class, userId));
        assertEquals(200, add(2));
        assertStoredQuantity(2);
    }

    @Test void addingAnExistingItemUpdatesCartTimestamp() throws Exception {
        assertEquals(200, add(1));
        LocalDateTime old = LocalDateTime.of(2000, 1, 1, 0, 0);
        jdbc.update("update carts set updated_at=? where user_id=?", old, userId);
        assertEquals(200, add(1));
        assertTrue(jdbc.queryForObject("select updated_at from carts where user_id=?", LocalDateTime.class, userId).isAfter(old));
    }

    @Test void databaseRejectsNonPositiveQuantity() throws Exception {
        assertEquals(200, add(1));
        for (int invalid : new int[]{0, -1}) {
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                    "update cart_items set quantity=? where cart_id=(select id from carts where user_id=?)", invalid, userId));
        }
        assertStoredQuantity(1);
    }

    @ParameterizedTest @ValueSource(strings={"stock", "hidden"})
    void addWaitsForCatalogWriteAndValidatesCommittedState(String change) throws Exception {
        var pool = Executors.newSingleThreadExecutor();
        var requests = new ArrayList<Future<Integer>>();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                jdbc.update(change.equals("stock")
                        ? "update products set stock_quantity=1 where id=?"
                        : "update products set status='HIDDEN' where id=?", productId);
                requests.add(pool.submit(() -> add(2)));
                awaitBlockedQueries("products", 1);
                assertFalse(requests.get(0).isDone());
            });
            assertEquals(409, requests.get(0).get(15, TimeUnit.SECONDS));
            assertNoCart();
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void staleAuthenticatedPrincipalCannotAddAfterAccountIsBlocked() throws Exception {
        assertTrue(principal.isEnabled());
        jdbc.update("update users set status='BLOCKED' where id=?", userId);
        assertEquals(401, add(1));
        assertNoCart();
    }

    private int add(int quantity) throws Exception {
        var response = mvc.perform(MockMvcRequestBuilders.post("/api/cart/items").with(user(principal))
                .contentType("application/json")
                .content("{\"productId\":"+productId+",\"quantity\":"+quantity+"}"))
                .andReturn().getResponse();
        if (response.getStatus() == 200) {
            assertEquals(true, JsonPath.read(response.getContentAsString(), "$.success"));
        }
        return response.getStatus();
    }

    private List<Integer> concurrentAdds(int first, int second) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var requests = new ArrayList<Future<Integer>>();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                jdbc.queryForObject("select id from users where id=? for update", Long.class, userId);
                requests.add(pool.submit(() -> add(first)));
                requests.add(pool.submit(() -> add(second)));
                // Observe both requests blocked in PostgreSQL before releasing the user lock.
                awaitBlockedQueries("users", 2);
                assertFalse(requests.get(0).isDone());
                assertFalse(requests.get(1).isDone());
            });
            return List.of(requests.get(0).get(15, TimeUnit.SECONDS), requests.get(1).get(15, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private void awaitBlockedQueries(String table, int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            // PostgreSQL caches activity snapshots within this polling transaction.
            jdbc.execute("select pg_stat_clear_snapshot()");
            int waiting = jdbc.queryForObject("select count(*) from pg_stat_activity "
                    + "where datname=current_database() and wait_event_type='Lock' and query like ?",
                    Integer.class, "%from " + table + " %");
            if (waiting >= expected) return;
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted waiting for database locks", ex);
            }
        } while (System.nanoTime() < deadline);
        fail("Expected " + expected + " blocked queries on " + table);
    }

    private void assertNoCart() {
        assertEquals(0, jdbc.queryForObject("select count(*) from carts where user_id=?", Integer.class, userId));
    }

    private void assertStoredQuantity(int expected) {
        assertEquals(expected, jdbc.queryForObject("select ci.quantity from cart_items ci join carts c on c.id=ci.cart_id where c.user_id=? and ci.product_id=?",
                Integer.class, userId, productId));
    }
}
