package com.ogani.store.order;

import com.jayway.jsonpath.JsonPath;
import com.ogani.store.common.AbstractIntegrationTest;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@Transactional(propagation=Propagation.NOT_SUPPORTED)
class CheckoutConcurrencyIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactions;
    private final List<Long> userIds=new ArrayList<>();
    private final List<Long> productIds=new ArrayList<>();
    private Integer categoryId;
    private UserPrincipal first,second;
    private record Result(int status,Long id) {}

    @BeforeEach void fixtures() throws Exception {
        assertTrue(jdbc.queryForObject("select current_database()",String.class).endsWith("_test"));
        String suffix=UUID.randomUUID().toString();
        for(int n=0;n<2;n++) {
            Long id=jdbc.queryForObject("insert into users(role_id,email,password_hash,first_name,last_name) select role_id,?,password_hash,'Checkout','Test' from users where email=? returning id",
                    Long.class,"checkout-"+n+suffix+"@example.test",CUSTOMER);
            userIds.add(id);
        }
        first=UserPrincipal.from(users.findById(userIds.get(0)).orElseThrow());
        second=UserPrincipal.from(users.findById(userIds.get(1)).orElseThrow());
        categoryId=jdbc.queryForObject("insert into categories(name,slug) values('Checkout test',?) returning id",Integer.class,suffix);
        for(int n=0;n<2;n++) productIds.add(jdbc.queryForObject("insert into products(category_id,name,slug,sku,price,stock_quantity) values(?,'Checkout product',?,?,20,5) returning id",
                Long.class,categoryId,n+suffix,n+suffix));
        assertEquals(200,add(first,productIds.get(0),1));
        assertEquals(200,add(second,productIds.get(0),1));
    }

    @AfterEach void cleanup() {
        for(Long id:userIds) {
            jdbc.update("delete from orders where user_id=?",id);
            jdbc.update("delete from users where id=?",id);
        }
        for(Long id:productIds) jdbc.update("delete from products where id=?",id);
        if(categoryId!=null) jdbc.update("delete from categories where id=?",categoryId);
    }

    @Test void concurrentSameKeyCreatesOneOrderAndOneStockDeduction() throws Exception {
        String body=request(quote(first)),key=UUID.randomUUID().toString();
        var results=contend("users",first.getId(),() -> place(first,key,body),() -> place(first,key,body));
        assertEquals(List.of(200,201),results.stream().map(Result::status).sorted().toList());
        assertEquals(results.get(0).id(),results.get(1).id());
        assertEquals(1,orderCount()); assertEquals(4,stock());
    }

    @Test void checkoutAndAddressDeletionCannotLeavePartialOrderOrStockChanges() throws Exception {
        long address=jdbc.queryForObject("insert into customer_addresses(user_id,recipient_name,phone,address_line,city,district,ward,is_default) values(?,'Original recipient','0901234567','Original street','HCM','Original district','Original ward',true) returning id",Long.class,first.getId());
        String body="{\"cartToken\":\""+quote(first)+"\",\"firstName\":\"Checkout\",\"lastName\":\"Buyer\",\"email\":\"buyer@example.test\",\"paymentMethod\":\"COD\",\"addressId\":"+address+"}";
        var results=contend("users",first.getId(),() -> place(first,UUID.randomUUID().toString(),body),() ->
                result(mvc.perform(MockMvcRequestBuilders.delete("/api/addresses/{id}",address).with(user(first))).andReturn().getResponse()));
        assertEquals(200,results.get(1).status());
        assertEquals(0,jdbc.queryForObject("select count(*) from customer_addresses where id=?",Integer.class,address));
        if(results.get(0).status()==201) {
            assertEquals(1,orderCount());assertEquals(4,stock());
            assertEquals("Original street",jdbc.queryForObject("select ship_address from orders where id=?",String.class,results.get(0).id()));
            assertEquals("Original ward",jdbc.queryForObject("select ship_ward from orders where id=?",String.class,results.get(0).id()));
        } else {
            assertEquals(404,results.get(0).status());assertEquals(0,orderCount());assertEquals(5,stock());
            assertEquals(1,jdbc.queryForObject("select sum(ci.quantity) from cart_items ci join carts c on c.id=ci.cart_id where c.user_id=?",Integer.class,first.getId()));
        }
    }

    @Test void differentKeysCannotCreateTwoOrdersFromOneCart() throws Exception {
        String body=request(quote(first));
        var results=contend("users",first.getId(),() -> place(first,UUID.randomUUID().toString(),body),() -> place(first,UUID.randomUUID().toString(),body));
        assertEquals(List.of(201,409),results.stream().map(Result::status).sorted().toList());
        assertEquals(1,orderCount()); assertEquals(4,stock());
    }

    @Test void competingBuyersCannotOversellLastUnit() throws Exception {
        jdbc.update("update products set stock_quantity=1 where id=?",productIds.get(0));
        String a=request(quote(first)),b=request(quote(second));
        var results=contend("products",productIds.get(0),() -> place(first,UUID.randomUUID().toString(),a),() -> place(second,UUID.randomUUID().toString(),b));
        assertEquals(List.of(201,409),results.stream().map(Result::status).sorted().toList());
        assertEquals(1,orderCount());assertEquals(0,stock());
        assertEquals(1,jdbc.queryForObject("select count(*) from cart_items where cart_id in (select id from carts where user_id in (?,?))",Integer.class,first.getId(),second.getId()));
    }

    @Test void inverseCartInsertionOrderDoesNotDeadlock() throws Exception {
        assertEquals(200,add(first,productIds.get(1),1));
        jdbc.update("delete from cart_items where cart_id=(select id from carts where user_id=?)",second.getId());
        assertEquals(200,add(second,productIds.get(1),1)); assertEquals(200,add(second,productIds.get(0),1));
        String a=request(quote(first)),b=request(quote(second));
        var results=contend("products",productIds.get(0),() -> place(first,UUID.randomUUID().toString(),a),() -> place(second,UUID.randomUUID().toString(),b));
        assertEquals(List.of(201,201),results.stream().map(Result::status).toList());
        assertEquals(3,stock());
        assertEquals(3,jdbc.queryForObject("select stock_quantity from products where id=?",Integer.class,productIds.get(1)));
    }

    @ParameterizedTest @ValueSource(strings={"add","update","remove"})
    void checkoutAndCartMutationHaveAnAtomicSerialOutcome(String operation) throws Exception {
        String body=request(quote(first));
        long itemId=jdbc.queryForObject("select id from cart_items where cart_id=(select id from carts where user_id=?)",Long.class,first.getId());
        var results=contend("users",first.getId(),() -> place(first,UUID.randomUUID().toString(),body),() -> {
            if(operation.equals("add")) return new Result(add(first,productIds.get(0),1),null);
            var request=operation.equals("remove") ? MockMvcRequestBuilders.delete("/api/cart/items/"+itemId)
                    : MockMvcRequestBuilders.put("/api/cart/items/"+itemId).contentType("application/json").content("{\"quantity\":2}");
            return result(mvc.perform(request.with(user(first))).andReturn().getResponse());
        });
        int checkout=results.get(0).status(),mutation=results.get(1).status();
        if(checkout==201) {
            assertEquals(1,orderCount());assertEquals(4,stock());
            assertEquals(operation.equals("add")?200:404,mutation);
        } else {
            assertEquals(409,checkout);assertEquals(200,mutation);
            assertEquals(0,orderCount());assertEquals(5,stock());
        }
    }

    @Test void checkoutAndStaleAdminEditCannotRestoreDeductedStock() throws Exception {
        String body=request(quote(first));
        String edit="{\"name\":\"Checkout product\",\"sku\":\""+jdbc.queryForObject("select sku from products where id=?",String.class,productIds.get(0))
                +"\",\"categoryId\":"+categoryId+",\"price\":25,\"stockQuantity\":5,\"version\":0}";
        var results=contend("products",productIds.get(0),() -> place(first,UUID.randomUUID().toString(),body),() ->
                result(mvc.perform(MockMvcRequestBuilders.put("/api/admin/products/"+productIds.get(0))
                        .with(user("admin").roles("ADMIN")).contentType("application/json").content(edit)).andReturn().getResponse()));
        if(results.get(0).status()==201) { assertEquals(409,results.get(1).status()); assertEquals(4,stock()); }
        else { assertEquals(409,results.get(0).status()); assertEquals(200,results.get(1).status()); assertEquals(5,stock()); }
    }

    @Test void blockedAccountCannotReplayOrReadAnExistingOrder() throws Exception {
        String body=request(quote(first)),key=UUID.randomUUID().toString();
        var order=place(first,key,body);assertEquals(201,order.status());
        jdbc.update("update users set status='BLOCKED' where id=?",first.getId());
        assertEquals(401,place(first,key,body).status());
        assertEquals(401,mvc.perform(MockMvcRequestBuilders.get("/api/orders/"+order.id()).with(user(first))).andReturn().getResponse().getStatus());
    }

    private int add(UserPrincipal principal,long product,int quantity) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/cart/items").with(user(principal)).contentType("application/json")
                .content("{\"productId\":"+product+",\"quantity\":"+quantity+"}")).andReturn().getResponse().getStatus();
    }
    private String quote(UserPrincipal principal) throws Exception {
        var response=mvc.perform(MockMvcRequestBuilders.post("/api/checkout/preview").with(user(principal))).andReturn().getResponse();
        assertEquals(200,response.getStatus(),response.getContentAsString());
        return JsonPath.read(response.getContentAsString(),"$.data.cartToken");
    }
    private String request(String token) {
        return "{\"cartToken\":\""+token+"\",\"firstName\":\"Checkout\",\"lastName\":\"Buyer\",\"email\":\"buyer@example.test\",\"paymentMethod\":\"COD\",\"deliveryAddress\":{\"phone\":\"0901234567\",\"country\":\"Vietnam\",\"address\":\"12 Test\",\"city\":\"HCM\"}}";
    }
    private Result place(UserPrincipal principal,String key,String body) throws Exception {
        return result(mvc.perform(MockMvcRequestBuilders.post("/api/checkout").with(user(principal))
                .header("Idempotency-Key",key).contentType("application/json").content(body)).andReturn().getResponse());
    }
    private Result result(MockHttpServletResponse response) throws Exception {
        Long id=null;
        if(response.getStatus()==201 || (response.getStatus()==200 && response.getContentAsString().contains("orderCode")))
            id=((Number)JsonPath.read(response.getContentAsString(),"$.data.id")).longValue();
        return new Result(response.getStatus(),id);
    }
    private List<Result> contend(String table,long id,Callable<Result> firstRequest,Callable<Result> secondRequest) throws Exception {
        var pool=Executors.newFixedThreadPool(2);var futures=new ArrayList<Future<Result>>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.queryForObject("select id from "+table+" where id=? for update",Long.class,id);
                futures.add(pool.submit(firstRequest)); futures.add(pool.submit(secondRequest));
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
                while(true) {
                    jdbc.execute("select pg_stat_clear_snapshot()");
                    int count=jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like ?",Integer.class,"%"+table+"%");
                    if(count>=2) break;
                    if(System.nanoTime()>deadline) fail("Expected two blocked checkout/cart/catalog requests on "+table);
                    try { Thread.sleep(10); } catch(InterruptedException ex) { Thread.currentThread().interrupt();throw new AssertionError(ex); }
                }
            });
            return List.of(futures.get(0).get(15,TimeUnit.SECONDS),futures.get(1).get(15,TimeUnit.SECONDS));
        } finally { pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS)); }
    }
    private int stock() { return jdbc.queryForObject("select stock_quantity from products where id=?",Integer.class,productIds.get(0)); }
    private int orderCount() { return jdbc.queryForObject("select count(*) from orders where user_id in (?,?)",Integer.class,first.getId(),second.getId()); }
}
