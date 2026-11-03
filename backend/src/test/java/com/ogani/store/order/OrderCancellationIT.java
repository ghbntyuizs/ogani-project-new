package com.ogani.store.order;

import com.ogani.store.common.AbstractIntegrationTest;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderCancellationIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired UserRepository users;
    private long id;

    @BeforeEach void fixture() {
        jdbc.update("update products set stock_quantity=0,status='OUT_OF_STOCK' where id=1");
        id=jdbc.queryForObject("insert into orders(order_code,user_id,first_name,last_name,email,phone,ship_country,ship_address,ship_city,ship_recipient_name,subtotal,total) "
                +"values(?,3,'Cancel','Buyer','cancel@example.test','0901234567','Vietnam','12 Test','HCM','Cancel Buyer',30,35) returning id",
                Long.class,"CNL-"+UUID.randomUUID().toString().replace("-","").substring(0,16));
        jdbc.update("insert into order_items(order_id,product_id,product_name,unit_price,quantity,line_total) values(?,1,'Snapshot name',15,2,30)",id);
    }

    @Test void cancelsOnceRestoresStockAndPreservesSnapshots() throws Exception {
        var before=jdbc.queryForMap("select first_name,last_name,email,ship_address,subtotal,total,payment_status,checkout_key from orders where id=?",id);
        long productVersion=jdbc.queryForObject("select version from products where id=1",Long.class);
        int sold=jdbc.queryForObject("select sold_count from products where id=1",Integer.class);
        cancel("{\"reason\":\" Changed my mind \"}",CUSTOMER).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.data.items[0].productName").value("Snapshot name"));
        assertEquals(2,stock());
        assertEquals("ACTIVE",jdbc.queryForObject("select status from products where id=1",String.class));
        assertEquals(productVersion+1,jdbc.queryForObject("select version from products where id=1",Long.class));
        assertEquals(sold,jdbc.queryForObject("select sold_count from products where id=1",Integer.class));
        assertEquals(before,jdbc.queryForMap("select first_name,last_name,email,ship_address,subtotal,total,payment_status,checkout_key from orders where id=?",id));
        var event=jdbc.queryForMap("select from_status,to_status,changed_by,note from order_status_history where order_id=?",id);
        assertEquals("PENDING",event.get("from_status"));assertEquals("CANCELLED",event.get("to_status"));
        assertEquals(3L,((Number)event.get("changed_by")).longValue());
        assertTrue(event.get("note").toString().contains("Changed my mind"));
        var timestamp=jdbc.queryForObject("select updated_at from orders where id=?",Object.class,id);
        cancel("{\"reason\":\"Another reason\"}",CUSTOMER).andExpect(status().isOk());
        assertEquals(2,stock());assertEquals(1,historyCount());
        assertEquals(timestamp,jdbc.queryForObject("select updated_at from orders where id=?",Object.class,id));
    }

    @ParameterizedTest @ValueSource(strings={"", "{}", "{\"reason\":null}", "{\"reason\":\"   \"}"})
    void acceptsOptionalReason(String body) throws Exception { cancel(body,CUSTOMER).andExpect(status().isOk()); }

    @ParameterizedTest @ValueSource(strings={"CONFIRMED","SHIPPING","COMPLETED"})
    void rejectsNonPendingStates(String state) throws Exception {
        jdbc.update("update orders set order_status=? where id=?",state,id);
        cancel("{}",CUSTOMER).andExpect(status().isConflict());assertEquals(0,stock());assertEquals(0,historyCount());
    }

    @ParameterizedTest @ValueSource(strings={"PAID","REFUNDED"})
    void refusesAutomaticRefunds(String payment) throws Exception {
        jdbc.update("update orders set payment_status=? where id=?",payment,id);
        cancel("{}",CUSTOMER).andExpect(status().isConflict());assertEquals(0,stock());assertEquals(0,historyCount());
    }

    @ParameterizedTest @ValueSource(strings={"PAYPAL","BANK_TRANSFER"})
    void rejectsUnsupportedPaymentMethods(String method) throws Exception {
        jdbc.update("update orders set payment_method=? where id=?",method,id);
        cancel("{}",CUSTOMER).andExpect(status().isConflict());assertEquals(0,stock());
    }

    @Test void alreadyCancelledDoesNotInventARestockOrHistory() throws Exception {
        jdbc.update("update orders set order_status='CANCELLED' where id=?",id);
        cancel("{}",CUSTOMER).andExpect(status().isOk());assertEquals(0,stock());assertEquals(0,historyCount());
    }

    @Test void foreignNullOwnerAndMissingOrderAre404() throws Exception {
        cancel("{}",STAFF).andExpect(status().isNotFound());
        cancel("{}",ADMIN).andExpect(status().isNotFound());
        post("/api/orders/9223372036854775807/cancel",CUSTOMER,"{}").andExpect(status().isNotFound());
        jdbc.update("update orders set user_id=null where id=?",id);
        cancel("{}",CUSTOMER).andExpect(status().isNotFound());assertEquals(0,stock());
    }

    @Test void otherAuthenticatedRolesCanOnlyCancelTheirOwnOrders() throws Exception {
        jdbc.update("update orders set user_id=1 where id=?",id);
        cancel("{}",ADMIN).andExpect(status().isOk());assertEquals(2,stock());
    }

    @Test void anonymousAndStaleBlockedPrincipalAreRejected() throws Exception {
        cancel("{}",ANONYMOUS).andExpect(status().isUnauthorized());
        UserPrincipal principal=UserPrincipal.from(users.findById(3L).orElseThrow());
        jdbc.update("update users set status='BLOCKED' where id=3");em.clear();
        mvc.perform(MockMvcRequestBuilders.post(path()).with(user(principal)).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());assertEquals(0,stock());
    }

    @ParameterizedTest @ValueSource(strings={"0","-1","1.5","oops","9223372036854775808"})
    void invalidIdsAre400(String value) throws Exception {
        post("/api/orders/"+value+"/cancel",CUSTOMER,"{}").andExpect(status().isBadRequest());
    }

    @ParameterizedTest @ValueSource(strings={"broken","[]","{\"reason\":123}","{\"reason\":true}","{\"reason\":[]}","{\"reason\":{}}"})
    void invalidBodiesAre400(String body) throws Exception { cancel(body,CUSTOMER).andExpect(status().isBadRequest()); }

    @Test void oversizedReasonIsRejected() throws Exception {
        cancel("{\"reason\":\""+"x".repeat(201)+"\"}",CUSTOMER).andExpect(status().isBadRequest());assertEquals(0,stock());
    }

    @Test void hiddenProductsStayHiddenAndDuplicateLinesAreAggregated() throws Exception {
        jdbc.update("update products set status='HIDDEN' where id=1");
        jdbc.update("insert into order_items(order_id,product_id,product_name,unit_price,quantity,line_total) values(?,1,'Duplicate',15,3,45)",id);
        cancel("{}",CUSTOMER).andExpect(status().isOk());assertEquals(5,stock());
        assertEquals("HIDDEN",jdbc.queryForObject("select status from products where id=1",String.class));
    }

    @Test void deletedProductsAreNotRecreated() throws Exception {
        jdbc.update("update order_items set product_id=null where order_id=?",id);
        cancel("{}",CUSTOMER).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].productId").doesNotExist());
        assertEquals(0,stock());assertEquals(1,historyCount());
    }

    @Test void explicitOutOfStockWithPositiveInventoryIsPreserved() throws Exception {
        jdbc.update("update products set stock_quantity=3,status='OUT_OF_STOCK' where id=1");
        cancel("{}",CUSTOMER).andExpect(status().isOk());assertEquals(5,stock());
        assertEquals("OUT_OF_STOCK",jdbc.queryForObject("select status from products where id=1",String.class));
    }

    @Test void overflowRollsBackTheEntireCancellation() throws Exception {
        jdbc.update("update products set stock_quantity=2147483647 where id=2");
        jdbc.update("insert into order_items(order_id,product_id,product_name,unit_price,quantity,line_total) values(?,2,'Later overflow',1,1,1)",id);
        cancel("{}",CUSTOMER).andExpect(status().isConflict());assertEquals(0,stock());assertEquals(0,historyCount());
        assertEquals("PENDING",jdbc.queryForObject("select order_status from orders where id=?",String.class,id));
    }

    @ParameterizedTest @ValueSource(ints={0,-1})
    void invalidLegacyQuantitiesDoNotRestock(int quantity) throws Exception {
        jdbc.update("update order_items set quantity=? where order_id=?",quantity,id);
        cancel("{}",CUSTOMER).andExpect(status().isConflict());assertEquals(0,stock());
    }

    @Test void emptyOrderCannotBeNewlyCancelled() throws Exception {
        jdbc.update("delete from order_items where order_id=?",id);
        cancel("{}",CUSTOMER).andExpect(status().isConflict());assertEquals(0,historyCount());
    }

    @Test void longestReasonWithManyDeletedLinesStillCancels() throws Exception {
        jdbc.update("insert into order_items(order_id,product_name,unit_price,quantity,line_total) select ?,'Deleted product',1,1,1 from generate_series(1,10000)",id);
        String reason="x".repeat(200);
        cancel("{\"reason\":\""+reason+"\"}",CUSTOMER).andExpect(status().isOk());
        String note=jdbc.queryForObject("select note from order_status_history where order_id=?",String.class,id);
        assertTrue(note.contains(reason));assertTrue(note.contains("10000"));assertTrue(note.length()<=255);
        assertEquals(2,stock());assertEquals(1,historyCount());
    }

    @Test void exactMaximumRestoredStockIsValid() throws Exception {
        jdbc.update("update products set stock_quantity=2147483645 where id=1");
        cancel("{}",CUSTOMER).andExpect(status().isOk());assertEquals(Integer.MAX_VALUE,stock());
    }

    @Test void aggregateQuantityOverflowIsRejectedBeforeRestock() throws Exception {
        jdbc.update("insert into order_items(order_id,product_id,product_name,unit_price,quantity,line_total) values(?,1,'Duplicate maximum',1,2147483647,1)",id);
        cancel("{}",CUSTOMER).andExpect(status().isConflict());assertEquals(0,stock());assertEquals(0,historyCount());
    }

    @Test void negativeInventoryDoesNotGetSilentlyRepaired() throws Exception {
        jdbc.update("update products set stock_quantity=-1 where id=1");
        cancel("{}",CUSTOMER).andExpect(status().isConflict());assertEquals(-1,stock());assertEquals(0,historyCount());
    }

    private String path() { return "/api/orders/"+id+"/cancel"; }
    private ResultActions cancel(String body,String as) throws Exception { return post(path(),as,body); }
    private int stock() { return jdbc.queryForObject("select stock_quantity from products where id=1",Integer.class); }
    private int historyCount() { return jdbc.queryForObject("select count(*) from order_status_history where order_id=?",Integer.class,id); }
}
