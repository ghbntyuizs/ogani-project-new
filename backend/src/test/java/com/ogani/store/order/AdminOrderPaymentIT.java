package com.ogani.store.order;

import com.ogani.store.common.AbstractIntegrationTest;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminOrderPaymentIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    long id;
    String body="{\"paymentStatus\":\"PAID\",\"version\":0}";
    @BeforeEach void fixture() {
        id=jdbc.queryForObject("insert into orders(order_code,user_id,first_name,last_name,email,phone,ship_country,ship_address,ship_city,ship_recipient_name,subtotal,total,order_status) values(?,3,'Test','Buyer','b@example.test','0901234567','Vietnam','Test','HCM','Test Buyer',40,40,'SHIPPING') returning id",Long.class,"PAY-"+UUID.randomUUID().toString().substring(0,15));
    }
    ResultActions pay(String input,String actor) throws Exception {
        var req=MockMvcRequestBuilders.patch("/api/admin/orders/{id}/payment-status",id).contentType("application/json").content(input);
        if(actor!=null) req.with(user(UserPrincipal.from(users.findByEmail(actor).orElseThrow())));
        return mvc.perform(req);
    }
    @Test void confirmsAndPreservesAuditOnRetryAfterCompletion() throws Exception {
        pay(body,STAFF).andExpect(status().isOk()).andExpect(jsonPath("$.data.paymentStatus").value("PAID")).andExpect(jsonPath("$.data.version").value(1)).andExpect(jsonPath("$.data.confirmedBy").value(2)).andExpect(jsonPath("$.data.paidAt").isNotEmpty());
        var before=jdbc.queryForMap("select paid_at,payment_confirmed_by,updated_at,version from orders where id=?",id);
        jdbc.update("update orders set order_status='COMPLETED' where id=?",id);
        pay(body,ADMIN).andExpect(status().isOk());
        assertEquals(before,jdbc.queryForMap("select paid_at,payment_confirmed_by,updated_at,version from orders where id=?",id));
        get("/api/admin/orders/"+id,ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.data.payment.confirmedBy").value(2)).andExpect(jsonPath("$.data.payment.paidAt").isNotEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"PENDING","CONFIRMED","CANCELLED","COMPLETED"})
    void rejectsOutsideShipping(String state) throws Exception {
        jdbc.update("update orders set order_status=? where id=?",state,id);
        pay(body,ADMIN).andExpect(status().isConflict());
        assertEquals("UNPAID",jdbc.queryForObject("select payment_status from orders where id=?",String.class,id));
    }
    @ParameterizedTest @ValueSource(strings={"BANK_TRANSFER","PAYPAL"})
    void rejectsOtherMethodsEvenIfPaid(String method) throws Exception {
        jdbc.update("update orders set payment_method=?,payment_status='PAID' where id=?",method,id);
        pay(body,ADMIN).andExpect(status().isConflict());
    }
    @Test void rejectsRefundedAndStale() throws Exception {
        jdbc.update("update orders set payment_status='REFUNDED' where id=?",id);pay(body,ADMIN).andExpect(status().isConflict());
        jdbc.update("update orders set payment_status='UNPAID',version=1 where id=?",id);pay(body,ADMIN).andExpect(status().isConflict());
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"paymentStatus\":\"UNPAID\",\"version\":0}","{\"paymentStatus\":\"REFUNDED\",\"version\":0}","{\"paymentStatus\":1,\"version\":0}","{\"paymentStatus\":\"PAID\",\"version\":-1}","{\"paymentStatus\":\"PAID\",\"version\":0.5}","{\"paymentStatus\":\"PAID\",\"version\":\"0\"}"})
    void validatesInput(String input) throws Exception { pay(input,ADMIN).andExpect(status().isBadRequest()); }
    @Test void checksAccessAndMissingOrder() throws Exception {
        pay(body,null).andExpect(status().isUnauthorized());pay(body,CUSTOMER).andExpect(status().isForbidden());
        id=Long.MAX_VALUE;pay(body,ADMIN).andExpect(status().isNotFound());id=0;pay(body,ADMIN).andExpect(status().isBadRequest());
    }

    @Test void confirmsGuestOrderWithoutTouchingInventoryOrStatusHistory() throws Exception {
        jdbc.update("update orders set user_id=null where id=?",id);
        var inventory=jdbc.queryForList("select id,stock_quantity,sold_count,version from products order by id");
        pay(body,ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.data.orderStatus").value("SHIPPING"));
        assertEquals(inventory,jdbc.queryForList("select id,stock_quantity,sold_count,version from products order by id"));
        assertEquals(0,jdbc.queryForObject("select count(*) from order_status_history where order_id=?",Integer.class,id));
    }

    @Test void legacyPaidOrderRetryDoesNotInventAudit() throws Exception {
        jdbc.update("update orders set payment_status='PAID' where id=?",id);
        pay(body,ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(0));
        assertNull(jdbc.queryForObject("select paid_at from orders where id=?",Object.class,id));
        assertNull(jdbc.queryForObject("select payment_confirmed_by from orders where id=?",Long.class,id));
    }

    @Test void blockedOrDemotedStaffCannotUseStalePrincipal() throws Exception {
        var principal=UserPrincipal.from(users.findByEmail(STAFF).orElseThrow());
        jdbc.update("update users set status='BLOCKED' where id=2");em.clear();
        mvc.perform(MockMvcRequestBuilders.patch("/api/admin/orders/{id}/payment-status",id).with(user(principal)).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        jdbc.update("update users set status='ACTIVE',role_id=3 where id=2");em.clear();
        mvc.perform(MockMvcRequestBuilders.patch("/api/admin/orders/{id}/payment-status",id).with(user(principal)).contentType("application/json").content(body)).andExpect(status().isForbidden());
    }
}
