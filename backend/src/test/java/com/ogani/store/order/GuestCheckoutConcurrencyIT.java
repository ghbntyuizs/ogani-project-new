package com.ogani.store.order;

import com.ogani.store.common.AbstractIntegrationTest;
import com.ogani.store.common.exception.AppException;
import com.ogani.store.order.dto.request.checkout.GuestCartRequest;
import com.ogani.store.order.dto.request.checkout.GuestCheckoutRequest;
import com.ogani.store.order.service.checkout.CheckoutPricing;
import com.ogani.store.order.service.checkout.GuestCheckoutService;
import com.ogani.store.user.dto.request.CreateAddressRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Transactional(propagation=Propagation.NOT_SUPPORTED)
class GuestCheckoutConcurrencyIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired GuestCheckoutService checkout;
    @Autowired CheckoutPricing pricing;
    @Autowired PlatformTransactionManager transactions;
    private Long product;private Integer category;private final List<String> scopes=new ArrayList<>();
    @BeforeEach void fixture(){
        assertTrue(jdbc.queryForObject("select current_database()",String.class).endsWith("_test"));
        String suffix=UUID.randomUUID().toString();
        category=jdbc.queryForObject("insert into categories(name,slug) values('Guest concurrency',?) returning id",Integer.class,suffix);
        product=jdbc.queryForObject("insert into products(category_id,name,slug,sku,price,stock_quantity) values(?,'Guest concurrency',?,?,20,1) returning id",Long.class,category,suffix,suffix);
    }
    @AfterEach void cleanup(){
        for(String scope:scopes)jdbc.update("delete from orders where guest_scope=?",scope);
        if(product!=null)jdbc.update("delete from products where id=?",product);
        if(category!=null)jdbc.update("delete from categories where id=?",category);
    }
    private GuestCheckoutRequest request(){
        String scope=UUID.randomUUID().toString().replace("-","").repeat(2);scopes.add(scope);
        var rows=List.of(new GuestCartRequest.Line(product,1));
        String token=new TransactionTemplate(transactions).execute(s->pricing.guestSnapshot(scope,rows).preview().cartToken());
        return new GuestCheckoutRequest(scope,token,"Guest","Test","guest@example.test","COD",new CreateAddressRequest("Guest Test","0901234567","City","District","Ward","12 Test Street",false),null,rows);
    }
    private List<Object> race(GuestCheckoutRequest a,String keyA,GuestCheckoutRequest b,String keyB)throws Exception{
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{
            List<Future<Object>> futures=new ArrayList<>();
            for(var pair:List.of(Map.entry(a,keyA),Map.entry(b,keyB)))futures.add(pool.submit(()->{start.await();try{return checkout.place(pair.getKey().scope(),pair.getValue(),pair.getKey());}catch(AppException e){return e;}}));
            start.countDown();var results=new ArrayList<Object>();for(var future:futures)results.add(future.get(20,TimeUnit.SECONDS));return results;
        }finally{pool.shutdownNow();}
    }
    @Test void simultaneousSameKeyCreatesExactlyOneOrder()throws Exception{
        var request=request();String key=UUID.randomUUID().toString();var results=race(request,key,request,key);
        var a=assertInstanceOf(GuestCheckoutService.Placement.class,results.get(0));var b=assertInstanceOf(GuestCheckoutService.Placement.class,results.get(1));
        assertEquals(a.order().id(),b.order().id());assertNotEquals(a.replay(),b.replay());
        assertEquals(1,jdbc.queryForObject("select count(*) from orders where guest_scope=?",Integer.class,request.scope()));
        assertEquals(0,jdbc.queryForObject("select stock_quantity from products where id=?",Integer.class,product));
    }
    @Test void distinctGuestsCannotOversellLastUnit()throws Exception{
        var results=race(request(),UUID.randomUUID().toString(),request(),UUID.randomUUID().toString());
        assertEquals(1,results.stream().filter(GuestCheckoutService.Placement.class::isInstance).count());
        var failure=(AppException)results.stream().filter(AppException.class::isInstance).findFirst().orElseThrow();assertEquals(409,failure.getHttpStatus().value());
        assertEquals(0,jdbc.queryForObject("select stock_quantity from products where id=?",Integer.class,product));
    }
}
