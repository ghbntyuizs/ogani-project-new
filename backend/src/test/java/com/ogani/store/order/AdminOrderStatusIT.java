package com.ogani.store.order;

import com.ogani.store.common.AbstractIntegrationTest;
import com.ogani.store.order.entity.OrderStatus;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminOrderStatusIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired UserRepository users;
    private long id;
    private static final Set<String> ALLOWED=Set.of("PENDING:CONFIRMED","PENDING:CANCELLED","CONFIRMED:SHIPPING","CONFIRMED:CANCELLED","SHIPPING:COMPLETED");

    @BeforeEach void fixture() {
        jdbc.update("update products set stock_quantity=10,sold_count=7,status='ACTIVE' where id=1");
        id=jdbc.queryForObject("insert into orders(order_code,user_id,first_name,last_name,email,phone,ship_country,ship_address,ship_city,ship_recipient_name,subtotal,total,payment_status) "
                +"values(?,3,'Snapshot','Buyer','buyer@example.test','0901234567','Vietnam','12 Test','HCM','Snapshot Buyer',40,40,'PAID') returning id",
                Long.class,"STA-"+UUID.randomUUID().toString().replace("-","").substring(0,16));
        jdbc.update("insert into order_items(order_id,product_id,product_name,unit_price,quantity,line_total) values(?,1,'Snapshot item',20,2,40)",id);
    }

    static Stream<Arguments> transitions() {
        return Arrays.stream(OrderStatus.values()).flatMap(from -> Arrays.stream(OrderStatus.values()).map(to -> Arguments.of(from.name(),to.name())));
    }

    @ParameterizedTest @MethodSource("transitions")
    void enforcesFullTransitionMatrixAndExactlyOnceEffects(String from,String to) throws Exception {
        jdbc.update("update orders set order_status=?,payment_status=? where id=?",from,to.equals("CANCELLED")?"UNPAID":"PAID",id);
        boolean same=from.equals(to),allowed=same || ALLOWED.contains(from+":"+to);
        patch(to,0,ADMIN).andExpect(allowed?status().isOk():status().isConflict());
        assertEquals(allowed?to:from,jdbc.queryForObject("select order_status from orders where id=?",String.class,id));
        assertEquals(allowed && !same?1:0,historyCount());
        assertEquals(allowed && !same?1L:0L,jdbc.queryForObject("select version from orders where id=?",Long.class,id));
        assertEquals(allowed && !same && to.equals("CANCELLED")?12:10,stock());
        assertEquals(allowed && !same && to.equals("COMPLETED")?9:7,sold());
        if(allowed && !same) {
            var event=jdbc.queryForMap("select from_status,to_status,changed_by,note from order_status_history where order_id=?",id);
            assertEquals(from,event.get("from_status"));assertEquals(to,event.get("to_status"));assertEquals(1L,((Number)event.get("changed_by")).longValue());
        }
    }

    @Test void staffCanConfirmAndShipUnpaidCodWithoutChangingPaymentOrStock() throws Exception {
        jdbc.update("update orders set payment_status='UNPAID' where id=?",id);
        patch("CONFIRMED",0,STAFF).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));
        patch("SHIPPING",1,STAFF).andExpect(status().isOk()).andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"));
        patch("COMPLETED",2,STAFF).andExpect(status().isConflict());
        assertEquals(10,stock());assertEquals(7,sold());assertEquals(2,historyCount());
        assertEquals(2L,jdbc.queryForObject("select changed_by from order_status_history where order_id=? order by id desc limit 1",Long.class,id));
    }

    @Test void staleDifferentTargetFailsButSameTargetRetriesDoNothing() throws Exception {
        patch("CONFIRMED",0,ADMIN).andExpect(status().isOk());
        var timestamp=jdbc.queryForObject("select updated_at from orders where id=?",Object.class,id);
        patch("SHIPPING",0,STAFF).andExpect(status().isConflict());
        patch("CONFIRMED",0,STAFF).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));
        assertEquals(timestamp,jdbc.queryForObject("select updated_at from orders where id=?",Object.class,id));assertEquals(1,historyCount());
    }

    @ParameterizedTest @ValueSource(strings={"BANK_TRANSFER","PAYPAL"})
    void unpaidOnlineCannotAdvanceButPaidCan(String method) throws Exception {
        jdbc.update("update orders set payment_method=?,payment_status='UNPAID' where id=?",method,id);
        patch("CONFIRMED",0,ADMIN).andExpect(status().isConflict());
        jdbc.update("update orders set payment_status='PAID' where id=?",id);em.clear();
        patch("CONFIRMED",0,ADMIN).andExpect(status().isOk());
    }

    @Test void paidCannotCancelAndRefundedCanCancelWithoutIssuingRefund() throws Exception {
        patch("CANCELLED",0,ADMIN).andExpect(status().isConflict());
        jdbc.update("update orders set payment_status='REFUNDED',payment_method='PAYPAL' where id=?",id);em.clear();
        patch("CONFIRMED",0,ADMIN).andExpect(status().isConflict());
        patch("CANCELLED",0,ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.data.paymentStatus").value("REFUNDED"));
        patch("CANCELLED",0,STAFF).andExpect(status().isOk());assertEquals(12,stock());assertEquals(1,historyCount());
    }

    @Test void completionCountsDuplicateHiddenProductsOnceAndSkipsDeletedOnes() throws Exception {
        jdbc.update("update orders set order_status='SHIPPING' where id=?",id);
        jdbc.update("update products set status='HIDDEN' where id=1");
        jdbc.update("insert into order_items(order_id,product_id,product_name,unit_price,quantity,line_total) values(?,1,'Duplicate',1,3,3)",id);
        jdbc.update("insert into order_items(order_id,product_name,unit_price,quantity,line_total) values(?,'Deleted',1,1,1)",id);
        patch("COMPLETED",0,ADMIN).andExpect(status().isOk());patch("COMPLETED",0,STAFF).andExpect(status().isOk());
        assertEquals(12,sold());assertEquals(10,stock());assertEquals(1,historyCount());
        assertEquals("HIDDEN",jdbc.queryForObject("select status from products where id=1",String.class));
    }

    @Test void completionOverflowAndNegativeCountersRejectWithoutChanges() throws Exception {
        jdbc.update("update orders set order_status='SHIPPING' where id=?",id);
        for(int count:new int[]{Integer.MAX_VALUE,-1}) {
            jdbc.update("update products set sold_count=? where id=1",count);em.clear();
            patch("COMPLETED",0,ADMIN).andExpect(status().isConflict());assertEquals(count,sold());assertEquals(0,historyCount());
        }
        jdbc.update("update products set sold_count=2147483645 where id=1");em.clear();
        patch("COMPLETED",0,ADMIN).andExpect(status().isOk());assertEquals(Integer.MAX_VALUE,sold());
    }

    @Test void cancellationPreservesHiddenStockPolicyAndOrderSnapshots() throws Exception {
        jdbc.update("update orders set order_status='CONFIRMED',payment_status='UNPAID' where id=?",id);
        jdbc.update("update products set status='HIDDEN' where id=1");
        var snapshot=jdbc.queryForMap("select subtotal,total,first_name,email,ship_address,checkout_key from orders where id=?",id);
        patch("CANCELLED",0,STAFF).andExpect(status().isOk());assertEquals(12,stock());
        assertEquals("HIDDEN",jdbc.queryForObject("select status from products where id=1",String.class));
        assertEquals(snapshot,jdbc.queryForMap("select subtotal,total,first_name,email,ship_address,checkout_key from orders where id=?",id));
    }

    @Test void emptyOrInvalidItemQuantitiesCannotAdvance() throws Exception {
        jdbc.update("update order_items set quantity=0 where order_id=?",id);
        patch("CONFIRMED",0,ADMIN).andExpect(status().isConflict());
        jdbc.update("delete from order_items where order_id=?",id);em.clear();
        patch("CONFIRMED",0,ADMIN).andExpect(status().isConflict());assertEquals(0,historyCount());
    }

    @Test void guestOrderCanBeProcessedWithoutAnOwnerAccount() throws Exception {
        jdbc.update("update orders set user_id=null where id=?",id);
        patch("CONFIRMED",0,ADMIN).andExpect(status().isOk());
    }

    @Test void roleAndActiveAccountAreChecked() throws Exception {
        patch("CONFIRMED",0,ANONYMOUS).andExpect(status().isUnauthorized());patch("CONFIRMED",0,CUSTOMER).andExpect(status().isForbidden());
        var principal=UserPrincipal.from(users.findByEmail(STAFF).orElseThrow());
        jdbc.update("update users set status='BLOCKED' where id=?",principal.getId());em.clear();
        mvc.perform(MockMvcRequestBuilders.patch(path()).with(user(principal)).contentType("application/json").content(body("CONFIRMED",0))).andExpect(status().isUnauthorized());
        jdbc.update("update users set status='ACTIVE',role_id=3 where id=?",principal.getId());em.clear();
        mvc.perform(MockMvcRequestBuilders.patch(path()).with(user(principal)).contentType("application/json").content(body("CONFIRMED",0))).andExpect(status().isForbidden());
    }

    @ParameterizedTest @ValueSource(strings={"{}","{\"status\":\"CONFIRMED\"}","{\"status\":\"UNKNOWN\",\"version\":0}",
            "{\"status\":1,\"version\":0}","{\"status\":null,\"version\":0}","{\"status\":\"confirmed\",\"version\":0}",
            "{\"status\":\"CONFIRMED\",\"version\":null}","{\"status\":\"CONFIRMED\",\"version\":-1}",
            "{\"status\":\"CONFIRMED\",\"version\":0.5}","{\"status\":\"CONFIRMED\",\"version\":\"0\"}",
            "{\"status\":\"CONFIRMED\",\"version\":9223372036854775808}","{\"status\":\"CONFIRMED\",\"version\":0,\"note\":123}","[]","broken",""})
    void invalidBodiesAre400(String json) throws Exception {
        raw(json,ADMIN).andExpect(status().isBadRequest());assertEquals(0,historyCount());
    }

    @Test void maximumNoteFitsAndLongerNoteIsRejected() throws Exception {
        raw("{\"status\":\"CONFIRMED\",\"version\":0,\"note\":\""+"x".repeat(201)+"\"}",ADMIN).andExpect(status().isBadRequest());
        raw("{\"status\":\"CONFIRMED\",\"version\":0,\"note\":\""+"x".repeat(200)+"\"}",ADMIN).andExpect(status().isOk());
        assertTrue(jdbc.queryForObject("select note from order_status_history where order_id=?",String.class,id).contains("x".repeat(200)));
    }

    @ParameterizedTest @ValueSource(strings={"0","-1","1.5","oops","9223372036854775808"})
    void invalidIdsAre400(String value) throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/admin/orders/"+value+"/status").with(user(UserPrincipal.from(users.findByEmail(ADMIN).orElseThrow())))
                .contentType("application/json").content(body("CONFIRMED",0))).andExpect(status().isBadRequest());
    }

    @Test void missingOrderIs404() throws Exception {
        mvc.perform(MockMvcRequestBuilders.patch("/api/admin/orders/9223372036854775807/status").with(user(UserPrincipal.from(users.findByEmail(ADMIN).orElseThrow())))
                .contentType("application/json").content(body("CONFIRMED",0))).andExpect(status().isNotFound());
    }

    private String path() { return "/api/admin/orders/"+id+"/status"; }
    private String body(String state,long version) { return "{\"status\":\""+state+"\",\"version\":"+version+",\"note\":\"Staff checked\"}"; }
    private ResultActions patch(String state,long version,String as) throws Exception { return raw(body(state,version),as); }
    private ResultActions raw(String body,String as) throws Exception {
        var request=MockMvcRequestBuilders.patch(path()).contentType("application/json").content(body);
        if(as!=null) request.with(user(UserPrincipal.from(users.findByEmail(as).orElseThrow())));
        return mvc.perform(request);
    }
    private int stock() { return jdbc.queryForObject("select stock_quantity from products where id=1",Integer.class); }
    private int sold() { return jdbc.queryForObject("select sold_count from products where id=1",Integer.class); }
    private int historyCount() { return jdbc.queryForObject("select count(*) from order_status_history where order_id=?",Integer.class,id); }
}
