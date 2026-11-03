package com.ogani.store.order;

import com.jayway.jsonpath.JsonPath;
import com.ogani.store.common.AbstractIntegrationTest;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.jpa.properties.hibernate.session_factory.statement_inspector=com.ogani.store.order.AdminOrderDetailIT$SqlCapture")
class AdminOrderDetailIT extends AbstractIntegrationTest {
    public static class SqlCapture implements StatementInspector {
        private static final ThreadLocal<List<String>> SQL=new ThreadLocal<>();
        private static final ThreadLocal<Runnable> BEFORE_ITEMS=new ThreadLocal<>();
        static void start() { SQL.set(new ArrayList<>()); }
        static List<String> finish() { var result=List.copyOf(SQL.get());SQL.remove();return result; }
        @Override public String inspect(String sql) {
            if(SQL.get()!=null) SQL.get().add(sql);
            if(sql.contains(" from order_items ") && BEFORE_ITEMS.get()!=null) {
                var action=BEFORE_ITEMS.get();BEFORE_ITEMS.remove();action.run();
            }
            return sql;
        }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactions;
    private Long customerId,productId,couponId,id;
    private Integer categoryId;
    private final List<Long> historyIds=new ArrayList<>();

    @BeforeEach void fixture() {
        assertTrue(jdbc.queryForObject("select current_database()",String.class).endsWith("_test"));
        String suffix=UUID.randomUUID().toString();
        customerId=jdbc.queryForObject("insert into users(role_id,email,password_hash,first_name,last_name) select 3,?,password_hash,'Current','Account' from users where email=? returning id",
                Long.class,"detail-"+suffix+"@example.test",CUSTOMER);
        categoryId=jdbc.queryForObject("insert into categories(name,slug) values('Detail fixture',?) returning id",Integer.class,suffix);
        productId=jdbc.queryForObject("insert into products(category_id,name,slug,sku,price,stock_quantity) values(?,'Current product',?,?,99,5) returning id",Long.class,categoryId,suffix,suffix);
        couponId=jdbc.queryForObject("insert into coupons(code,discount_type,discount_value) values(?,'FIXED',3) returning id",Long.class,"DET-"+suffix.substring(0,20));
        id=jdbc.queryForObject("insert into orders(order_code,user_id,first_name,last_name,email,phone,ship_country,ship_address,ship_city,ship_state,ship_postcode,ship_recipient_name,subtotal,discount_amount,shipping_fee,total,coupon_id,coupon_code,order_status,payment_status,transaction_ref,order_notes,checkout_key,checkout_request_hash) "
                +"values(?,?,'Snapshot','Buyer','snapshot@example.test','0900000999','Vietnam','Old address','HCM','HCM','700000','Recipient snapshot',47,3,5,49,?,'OLD-CODE','SHIPPING','UNPAID','TX-SNAPSHOT','Delivery note',?,'internal-hash') returning id",
                Long.class,"DET-"+suffix.replace("-","").substring(0,16),customerId,couponId,UUID.randomUUID().toString());
        jdbc.update("insert into order_items(order_id,product_id,product_name,unit_price,quantity,line_total) values(?,?,'Product snapshot',20,2,40)",id,productId);
        jdbc.update("insert into order_items(order_id,product_name,unit_price,quantity,line_total) values(?,'Deleted snapshot',7,1,7)",id);
        historyIds.add(event(null,"PENDING",null));historyIds.add(event("PENDING","CONFIRMED",customerId));historyIds.add(event("CONFIRMED","SHIPPING",1L));
    }

    @AfterEach void cleanup() {
        SqlCapture.BEFORE_ITEMS.remove();SqlCapture.SQL.remove();
        // The coherent-read method commits uniquely owned fixtures; normal methods roll back.
        if(!TransactionSynchronizationManager.isActualTransactionActive()) {
            if(id!=null) jdbc.update("delete from orders where id=?",id);
            if(customerId!=null) jdbc.update("delete from users where id=?",customerId);
            if(productId!=null) jdbc.update("delete from products where id=?",productId);
            if(categoryId!=null) jdbc.update("delete from categories where id=?",categoryId);
            if(couponId!=null) jdbc.update("delete from coupons where id=?",couponId);
        }
    }

    @Test void adminAndStaffGetAllSnapshotsPaymentCouponAndOrderedHistory() throws Exception {
        for(String role:new String[]{ADMIN,STAFF}) get(path(),role).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.customer.userId").value(customerId))
                .andExpect(jsonPath("$.data.customer.firstName").value("Snapshot"))
                .andExpect(jsonPath("$.data.customer.email").value("snapshot@example.test"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].productName").value("Product snapshot"))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(20))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].lineTotal").value(40))
                .andExpect(jsonPath("$.data.items[1].productId").doesNotExist())
                .andExpect(jsonPath("$.data.deliveryAddress.address").value("Old address"))
                .andExpect(jsonPath("$.data.deliveryAddress.recipientName").value("Recipient snapshot"))
                .andExpect(jsonPath("$.data.payment.method").value("COD"))
                .andExpect(jsonPath("$.data.payment.status").value("UNPAID"))
                .andExpect(jsonPath("$.data.payment.transactionRef").value("TX-SNAPSHOT"))
                .andExpect(jsonPath("$.data.coupon.code").value("OLD-CODE"))
                .andExpect(jsonPath("$.data.discountAmount").value(3))
                .andExpect(jsonPath("$.data.shippingFee").value(5)).andExpect(jsonPath("$.data.total").value(49))
                .andExpect(jsonPath("$.data.statusHistory.length()").value(3))
                .andExpect(jsonPath("$.data.statusHistory[0].id").value(historyIds.get(0)))
                .andExpect(jsonPath("$.data.statusHistory[0].fromStatus").doesNotExist())
                .andExpect(jsonPath("$.data.statusHistory[0].changedBy").doesNotExist())
                .andExpect(jsonPath("$.data.statusHistory[1].id").value(historyIds.get(1)))
                .andExpect(jsonPath("$.data.statusHistory[1].changedBy.id").value(customerId))
                .andExpect(jsonPath("$.data.statusHistory[1].changedBy.name").value("Current Account"))
                .andExpect(jsonPath("$.data.checkoutKey").doesNotExist())
                .andExpect(jsonPath("$.data.checkoutRequestHash").doesNotExist())
                .andExpect(jsonPath("$.data.customer.passwordHash").doesNotExist());
    }

    @Test void changedCatalogAccountAndCouponNeverRewriteSnapshots() throws Exception {
        jdbc.update("update users set first_name='Changed',email=? where id=?","changed-"+UUID.randomUUID()+"@example.test",customerId);
        jdbc.update("update products set name='Changed',price=1,status='HIDDEN' where id=?",productId);
        jdbc.update("update coupons set code=?,discount_value=99,is_active=false where id=?","NEW-"+UUID.randomUUID().toString().substring(0,20),couponId);
        get(path(),ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.data.customer.firstName").value("Snapshot"))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(20)).andExpect(jsonPath("$.data.items[0].productName").value("Product snapshot"))
                .andExpect(jsonPath("$.data.coupon.code").value("OLD-CODE")).andExpect(jsonPath("$.data.discountAmount").value(3));
    }

    @Test void historyUsesChangedTimeBeforeIdAndItemIdsStayOrdered() throws Exception {
        long older=event(null,"PENDING",null);
        jdbc.update("update order_status_history set changed_at='2026-10-31 10:00:00'::timestamp where id=?",older);
        String json=get(path(),ADMIN).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusHistory[0].id").value(older))
                .andExpect(jsonPath("$.data.statusHistory[1].id").value(historyIds.get(0)))
                .andExpect(jsonPath("$.data.statusHistory[2].id").value(historyIds.get(1)))
                .andReturn().getResponse().getContentAsString();
        long first=((Number)JsonPath.read(json,"$.data.items[0].itemId")).longValue();
        long second=((Number)JsonPath.read(json,"$.data.items[1].itemId")).longValue();
        assertTrue(first<second);
    }

    @Test void deletedReferencesDoNotHideOrderItemsOrHistory() throws Exception {
        jdbc.update("delete from users where id=?",customerId);jdbc.update("delete from products where id=?",productId);jdbc.update("delete from coupons where id=?",couponId);
        get(path(),STAFF).andExpect(status().isOk()).andExpect(jsonPath("$.data.customer.userId").doesNotExist())
                .andExpect(jsonPath("$.data.customer.email").value("snapshot@example.test"))
                .andExpect(jsonPath("$.data.items[0].productId").doesNotExist()).andExpect(jsonPath("$.data.items[0].productName").value("Product snapshot"))
                .andExpect(jsonPath("$.data.coupon.id").doesNotExist()).andExpect(jsonPath("$.data.coupon.code").value("OLD-CODE"))
                .andExpect(jsonPath("$.data.statusHistory.length()").value(3)).andExpect(jsonPath("$.data.statusHistory[1].changedBy").doesNotExist());
    }

    @Test void emptyLegacyItemsHistoryAndNoCouponAreReadable() throws Exception {
        jdbc.update("delete from order_items where order_id=?",id);jdbc.update("delete from order_status_history where order_id=?",id);
        jdbc.update("update orders set coupon_id=null,coupon_code=null,discount_amount=0,transaction_ref=null where id=?",id);
        get(path(),ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.statusHistory").isEmpty()).andExpect(jsonPath("$.data.coupon").doesNotExist())
                .andExpect(jsonPath("$.data.payment.transactionRef").doesNotExist()).andExpect(jsonPath("$.data.total").value(49));
    }

    @ParameterizedTest @ValueSource(strings={"UNPAID","PAID","REFUNDED"})
    void readsHistoricalPaymentStatesWithoutApplyingCheckoutRules(String state) throws Exception {
        jdbc.update("update orders set payment_method='PAYPAL',payment_status=? where id=?",state,id);
        get(path(),STAFF).andExpect(status().isOk()).andExpect(jsonPath("$.data.payment.method").value("PAYPAL"))
                .andExpect(jsonPath("$.data.payment.status").value(state));
    }

    @Test void anonymousCustomerAndMissingOrderPermissions() throws Exception {
        get(path(),ANONYMOUS).andExpect(status().isUnauthorized());get(path(),CUSTOMER).andExpect(status().isForbidden());
        get("/api/admin/orders/9223372036854775807",ADMIN).andExpect(status().isNotFound());
        getWithRawAuthHeader(path(),"Bearer invalid").andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @ValueSource(strings={"0","-1","1.5","oops","9223372036854775808"})
    void invalidIdsAre400(String value) throws Exception { get("/api/admin/orders/"+value,ADMIN).andExpect(status().isBadRequest()); }

    @Test void blockedOrDemotedStaleActorCannotReadDetails() throws Exception {
        var principal=UserPrincipal.from(users.findByEmail(STAFF).orElseThrow());
        jdbc.update("update users set status='BLOCKED' where id=?",principal.getId());em.clear();
        mvc.perform(MockMvcRequestBuilders.get(path()).with(user(principal))).andExpect(status().isUnauthorized());
        jdbc.update("update users set status='ACTIVE',role_id=3 where id=?",principal.getId());em.clear();
        mvc.perform(MockMvcRequestBuilders.get(path()).with(user(principal))).andExpect(status().isForbidden());
    }

    @Test void queryCountIsBoundedAndReadsDoNotChangeOrder() throws Exception {
        jdbc.update("insert into order_items(order_id,product_id,product_name,unit_price,quantity,line_total) select ?,?,'More snapshot',1,1,1 from generate_series(1,30)",id,productId);
        jdbc.update("insert into order_status_history(order_id,to_status,changed_by) select ?,'SHIPPING',? from generate_series(1,30)",id,customerId);
        var before=jdbc.queryForMap("select order_status,payment_status,version,total,updated_at from orders where id=?",id);
        SqlCapture.start();List<String> sql;
        try { get(path(),ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(32)).andExpect(jsonPath("$.data.statusHistory.length()").value(33)); }
        finally { sql=SqlCapture.finish(); }
        var domain=sql.stream().filter(s -> s.contains(" from orders ") || s.contains(" from order_items ") || s.contains(" from order_status_history ")).toList();
        assertEquals(3,domain.size(),"One header, one item and one history query expected");
        assertTrue(domain.stream().noneMatch(s -> s.contains("checkout_key") || s.contains("checkout_request_hash") || s.contains("password_hash")));
        assertTrue(sql.stream().noneMatch(s -> s.contains(" from products ") || s.contains(" from coupons ")));
        assertEquals(before,jdbc.queryForMap("select order_status,payment_status,version,total,updated_at from orders where id=?",id));
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void responseRemainsCoherentWhenStatusAndHistoryCommitBetweenQueries() throws Exception {
        var pool=Executors.newSingleThreadExecutor();
        try {
            SqlCapture.BEFORE_ITEMS.set(() -> {
                try {
                    pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                        jdbc.update("update orders set order_status='COMPLETED',payment_status='PAID',version=version+1 where id=?",id);
                        jdbc.update("insert into order_status_history(order_id,from_status,to_status,changed_by) values(?,'SHIPPING','COMPLETED',1)",id);
                    })).get(5,TimeUnit.SECONDS);
                } catch(Exception ex) { throw new AssertionError("Concurrent writer must be able to commit during detail read",ex); }
            });
            var admin=UserPrincipal.from(users.findByEmail(ADMIN).orElseThrow());
            mvc.perform(MockMvcRequestBuilders.get(path()).with(user(admin))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.orderStatus").value("SHIPPING"))
                    .andExpect(jsonPath("$.data.payment.status").value("UNPAID"))
                    .andExpect(jsonPath("$.data.statusHistory.length()").value(3));
            assertEquals("COMPLETED",jdbc.queryForObject("select order_status from orders where id=?",String.class,id));
            mvc.perform(MockMvcRequestBuilders.get(path()).with(user(admin))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.orderStatus").value("COMPLETED"))
                    .andExpect(jsonPath("$.data.statusHistory.length()").value(4));
        } finally { pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS)); }
    }

    private String path() { return "/api/admin/orders/"+id; }
    private long event(String from,String to,Long actor) {
        long event=jdbc.queryForObject("insert into order_status_history(order_id,from_status,to_status,note,changed_by) values(?, ?,?,'History note',?) returning id",Long.class,id,from,to,actor);
        jdbc.update("update order_status_history set changed_at='2026-11-01 10:00:00'::timestamp where id=?",event);
        return event;
    }
}
