package com.ogani.store.order;

import com.jayway.jsonpath.JsonPath;
import com.ogani.store.common.AbstractIntegrationTest;
import com.ogani.store.order.entity.Order;
import com.ogani.store.order.entity.OrderStatus;
import com.ogani.store.order.repository.OrderRepository;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@Transactional(propagation=Propagation.NOT_SUPPORTED)
class OrderCancellationConcurrencyIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired OrderRepository orders;
    @Autowired PlatformTransactionManager transactions;
    private final List<Long> userIds=new ArrayList<>(),productIds=new ArrayList<>(),orderIds=new ArrayList<>();
    private Integer categoryId;
    private UserPrincipal first,second;

    @BeforeEach void fixture() {
        assertTrue(jdbc.queryForObject("select current_database()",String.class).endsWith("_test"),"Committed cancellation tests require a *_test database");
        String suffix=UUID.randomUUID().toString();
        for(int n=0;n<2;n++) userIds.add(jdbc.queryForObject("insert into users(role_id,email,password_hash,first_name,last_name) select role_id,?,password_hash,'Cancel','Test' from users where email=? returning id",
                Long.class,"cancel-"+n+suffix+"@example.test",CUSTOMER));
        first=UserPrincipal.from(users.findById(userIds.get(0)).orElseThrow());second=UserPrincipal.from(users.findById(userIds.get(1)).orElseThrow());
        categoryId=jdbc.queryForObject("insert into categories(name,slug) values('Cancellation test',?) returning id",Integer.class,suffix);
        for(int n=0;n<2;n++) productIds.add(jdbc.queryForObject("insert into products(category_id,name,slug,sku,price,stock_quantity,status) values(?,'Cancel product',?,?,20,0,'OUT_OF_STOCK') returning id",
                Long.class,categoryId,n+suffix,n+suffix));
        for(Long owner:userIds) orderIds.add(jdbc.queryForObject("insert into orders(order_code,user_id,first_name,last_name,email,phone,ship_country,ship_address,ship_city,ship_recipient_name,subtotal,total) "
                        +"values(?,?,'Cancel','Buyer','buyer@example.test','0901234567','Vietnam','12 Test','HCM','Cancel Buyer',40,40) returning id",
                Long.class,"CNL-"+UUID.randomUUID().toString().replace("-","").substring(0,16),owner));
        line(orderIds.get(0),productIds.get(0));line(orderIds.get(0),productIds.get(1));
        // Deliberately inverse insertion order for the second buyer's products.
        line(orderIds.get(1),productIds.get(1));line(orderIds.get(1),productIds.get(0));
    }

    @AfterEach void cleanup() {
        for(Long id:orderIds) jdbc.update("delete from orders where id=?",id);
        for(Long id:userIds) jdbc.update("delete from orders where user_id=?",id);
        for(Long id:userIds) jdbc.update("delete from users where id=?",id);
        for(Long id:productIds) jdbc.update("delete from products where id=?",id);
        if(categoryId!=null) jdbc.update("delete from categories where id=?",categoryId);
    }

    @Test void concurrentSameOrderRestocksAndRecordsHistoryOnce() throws Exception {
        var result=contend("users",first.getId(),() -> cancel(first,orderIds.get(0)),() -> cancel(first,orderIds.get(0)));
        assertEquals(List.of(200,200),result);
        assertEquals(2,stock());assertEquals(1,cancellations(orderIds.get(0)));
        assertEquals(1L,jdbc.queryForObject("select version from orders where id=?",Long.class,orderIds.get(0)));
        assertEquals(1L,jdbc.queryForObject("select version from products where id=?",Long.class,productIds.get(0)));
    }

    @Test void inverseOrderItemsAcrossBuyersDoNotDeadlockOrLoseRestocks() throws Exception {
        assertEquals(List.of(200,200),contend("products",productIds.get(0),() -> cancel(first,orderIds.get(0)),() -> cancel(second,orderIds.get(1))));
        assertEquals(4,stock());assertEquals(4,jdbc.queryForObject("select stock_quantity from products where id=?",Integer.class,productIds.get(1)));
    }

    @Test void statusCommittedWhileCancellationWaitsIsRevalidated() throws Exception {
        var pool=Executors.newSingleThreadExecutor();var result=new ArrayList<Future<Integer>>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.update("update orders set order_status='SHIPPING',version=version+1 where id=?",orderIds.get(0));
                result.add(pool.submit(() -> cancel(first,orderIds.get(0))));
                awaitBlocked("orders",1);
            });
            assertEquals(409,result.get(0).get(15,TimeUnit.SECONDS));assertEquals(0,stock());assertEquals(0,cancellations(orderIds.get(0)));
        } finally { pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS)); }
    }

    @Test void staleVersionedStatusWriterCannotOverwriteCancellation() throws Exception {
        Order stale=orders.findById(orderIds.get(0)).orElseThrow();
        assertEquals(200,cancel(first,orderIds.get(0)));
        stale.setOrderStatus(OrderStatus.SHIPPING);
        assertThrows(OptimisticLockingFailureException.class,() -> new TransactionTemplate(transactions).execute(tx -> orders.saveAndFlush(stale)));
        assertEquals("CANCELLED",jdbc.queryForObject("select order_status from orders where id=?",String.class,orderIds.get(0)));
        assertEquals(2,stock());assertEquals(1,cancellations(orderIds.get(0)));
    }

    @Test void committedRestockOverflowRollsBackAllProductsOrderAndHistory() throws Exception {
        jdbc.update("update products set stock_quantity=2147483647 where id=?",productIds.get(1));
        assertEquals(409,cancel(first,orderIds.get(0)));
        assertEquals(0,stock());assertEquals(0,cancellations(orderIds.get(0)));
        assertEquals("PENDING",jdbc.queryForObject("select order_status from orders where id=?",String.class,orderIds.get(0)));
        assertEquals(0L,jdbc.queryForObject("select version from orders where id=?",Long.class,orderIds.get(0)));
    }

    @Test void lateDatabaseFailureRollsBackHistoryAndInventoryAndCanRetry() throws Exception {
        String function="cancel_fail_"+UUID.randomUUID().toString().replace("-","");
        try {
            jdbc.execute("create function "+function+"() returns trigger language plpgsql as $$ begin if new.id="+orderIds.get(0)+" and new.order_status='CANCELLED' then raise exception 'Intentional test failure'; end if; return new; end $$");
            jdbc.execute("create trigger "+function+" before update on orders for each row execute function "+function+"()");
            assertEquals(500,cancel(first,orderIds.get(0)));
            assertEquals(0,stock());assertEquals(0,cancellations(orderIds.get(0)));
            assertEquals("PENDING",jdbc.queryForObject("select order_status from orders where id=?",String.class,orderIds.get(0)));
        } finally {
            jdbc.execute("drop trigger if exists "+function+" on orders");jdbc.execute("drop function if exists "+function+"()");
        }
        assertEquals(200,cancel(first,orderIds.get(0)));assertEquals(2,stock());assertEquals(1,cancellations(orderIds.get(0)));
    }

    @Test void checkoutReplayAfterCancellationReturnsOriginalOrderAndLeavesNewCart() throws Exception {
        jdbc.update("update products set stock_quantity=3,status='ACTIVE' where id=?",productIds.get(0));
        String cart="{\"productId\":"+productIds.get(0)+",\"quantity\":1}";
        assertEquals(200,mvc.perform(MockMvcRequestBuilders.post("/api/cart/items").with(user(first)).contentType("application/json").content(cart)).andReturn().getResponse().getStatus());
        var preview=mvc.perform(MockMvcRequestBuilders.post("/api/checkout/preview").with(user(first))).andReturn().getResponse();
        String token=JsonPath.read(preview.getContentAsString(),"$.data.cartToken"),key=UUID.randomUUID().toString();
        String body="{\"cartToken\":\""+token+"\",\"firstName\":\"Cancel\",\"lastName\":\"Test\",\"email\":\"buyer@example.test\",\"paymentMethod\":\"COD\",\"deliveryAddress\":{\"phone\":\"0901234567\",\"country\":\"Vietnam\",\"address\":\"12 Test\",\"city\":\"HCM\"}}";
        var placed=mvc.perform(MockMvcRequestBuilders.post("/api/checkout").with(user(first)).header("Idempotency-Key",key).contentType("application/json").content(body)).andReturn().getResponse();
        assertEquals(201,placed.getStatus());long order=((Number)JsonPath.read(placed.getContentAsString(),"$.data.id")).longValue();
        assertEquals(200,cancel(first,order));assertEquals(3,stock());
        mvc.perform(MockMvcRequestBuilders.post("/api/cart/items").with(user(first)).contentType("application/json").content(cart));
        var replay=mvc.perform(MockMvcRequestBuilders.post("/api/checkout").with(user(first)).header("Idempotency-Key",key).contentType("application/json").content(body)).andReturn().getResponse();
        assertEquals(200,replay.getStatus());assertEquals("CANCELLED",JsonPath.read(replay.getContentAsString(),"$.data.orderStatus"));
        assertEquals(order,((Number)JsonPath.read(replay.getContentAsString(),"$.data.id")).longValue());assertEquals(3,stock());
        assertEquals(1,jdbc.queryForObject("select quantity from cart_items where cart_id=(select id from carts where user_id=?)",Integer.class,first.getId()));
    }

    @Test void cancellationAndCompetingCheckoutSerializeSharedInventory() throws Exception {
        jdbc.update("update products set stock_quantity=1,status='ACTIVE' where id=?",productIds.get(0));
        String cart="{\"productId\":"+productIds.get(0)+",\"quantity\":1}";
        mvc.perform(MockMvcRequestBuilders.post("/api/cart/items").with(user(second)).contentType("application/json").content(cart));
        var preview=mvc.perform(MockMvcRequestBuilders.post("/api/checkout/preview").with(user(second))).andReturn().getResponse();
        String token=JsonPath.read(preview.getContentAsString(),"$.data.cartToken");
        String body="{\"cartToken\":\""+token+"\",\"firstName\":\"Other\",\"lastName\":\"Buyer\",\"email\":\"buyer@example.test\",\"paymentMethod\":\"COD\",\"deliveryAddress\":{\"phone\":\"0901234567\",\"country\":\"Vietnam\",\"address\":\"12 Test\",\"city\":\"HCM\"}}";
        var results=contend("products",productIds.get(0),() -> cancel(first,orderIds.get(0)),() ->
                mvc.perform(MockMvcRequestBuilders.post("/api/checkout").with(user(second)).header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content(body)).andReturn().getResponse().getStatus());
        assertEquals(List.of(200,201),results);assertEquals(2,stock());assertEquals(1,cancellations(orderIds.get(0)));
    }

    private void line(long order,long product) { jdbc.update("insert into order_items(order_id,product_id,product_name,unit_price,quantity,line_total) values(?,?,'Snapshot',20,2,40)",order,product); }
    private int cancel(UserPrincipal principal,long order) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/orders/"+order+"/cancel").with(user(principal)).contentType("application/json").content("{\"reason\":\"Test cancellation\"}"))
                .andReturn().getResponse().getStatus();
    }
    private int stock() { return jdbc.queryForObject("select stock_quantity from products where id=?",Integer.class,productIds.get(0)); }
    private int cancellations(long order) { return jdbc.queryForObject("select count(*) from order_status_history where order_id=? and to_status='CANCELLED'",Integer.class,order); }
    private List<Integer> contend(String table,long id,Callable<Integer> a,Callable<Integer> b) throws Exception {
        var pool=Executors.newFixedThreadPool(2);var requests=new ArrayList<Future<Integer>>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.queryForObject("select id from "+table+" where id=? for update",Long.class,id);
                requests.add(pool.submit(a));requests.add(pool.submit(b));awaitBlocked(table,2);
            });
            return List.of(requests.get(0).get(15,TimeUnit.SECONDS),requests.get(1).get(15,TimeUnit.SECONDS));
        } finally { pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS)); }
    }
    private void awaitBlocked(String table,int expected) {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        do {
            jdbc.execute("select pg_stat_clear_snapshot()");
            if(jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like ?",Integer.class,"%"+table+"%")>=expected) return;
            try { Thread.sleep(10); } catch(InterruptedException ex) { Thread.currentThread().interrupt();throw new AssertionError(ex); }
        } while(System.nanoTime()<deadline);
        fail("Expected blocked requests on "+table);
    }
}
