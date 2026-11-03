package com.ogani.store.order;

import com.jayway.jsonpath.JsonPath;
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

class CheckoutIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired UserRepository users;
    private UserPrincipal buyer;
    private final String delivery = "\"deliveryAddress\":{\"phone\":\"0901234567\",\"country\":\"Vietnam\",\"address\":\"12 Test Street\",\"city\":\"HCM\"}";

    @BeforeEach void prepare() throws Exception {
        jdbc.update("delete from carts where user_id=(select id from users where email=?)", CUSTOMER);
        jdbc.update("update products set stock_quantity=2,status='ACTIVE',price=20,sale_price=15 where id=1");
        jdbc.update("update categories set is_active=true where id=(select category_id from products where id=1)");
        jdbc.update("update settings set setting_value=case setting_key when 'shipping_fee' then '5' when 'free_shipping_threshold' then '99' else 'USD' end where setting_key in ('shipping_fee','free_shipping_threshold','currency')");
        buyer = UserPrincipal.from(users.findByEmail(CUSTOMER).orElseThrow());
        post("/api/cart/items", CUSTOMER, "{\"productId\":1,\"quantity\":2}").andExpect(status().isOk());
        fresh();
    }

    @Test void previewShowsCurrentPricesShippingAndDoesNotReserveStock() throws Exception {
        preview().andExpect(status().isOk()).andExpect(jsonPath("$.data.subtotal").value(30))
                .andExpect(jsonPath("$.data.shippingFee").value(5)).andExpect(jsonPath("$.data.total").value(35))
                .andExpect(jsonPath("$.data.currency").value("USD")).andExpect(jsonPath("$.data.cartToken").isString());
        assertEquals(2, jdbc.queryForObject("select stock_quantity from products where id=1", Integer.class));
    }

    @Test void createsOrderSnapshotsHistoryDeductsStockAndClearsPurchasedItems() throws Exception {
        String originalName=jdbc.queryForObject("select name from products where id=1",String.class);
        long version = jdbc.queryForObject("select version from products where id=1", Long.class);
        int sold = jdbc.queryForObject("select sold_count from products where id=1", Integer.class);
        String json = place(UUID.randomUUID().toString(), request(token(), delivery)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.paymentMethod").value("COD"))
                .andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.data.orderStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.total").value(35)).andExpect(jsonPath("$.data.items[0].unitPrice").value(15))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(json,"$.data.id")).longValue();
        assertEquals(0, jdbc.queryForObject("select stock_quantity from products where id=1", Integer.class));
        assertEquals("OUT_OF_STOCK", jdbc.queryForObject("select status from products where id=1", String.class));
        assertEquals(version+1, jdbc.queryForObject("select version from products where id=1", Long.class));
        assertEquals(sold, jdbc.queryForObject("select sold_count from products where id=1", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from order_status_history where order_id=? and to_status='PENDING'", Integer.class,id));
        get("/api/cart",CUSTOMER).andExpect(jsonPath("$.data.items").isEmpty());
        jdbc.update("update products set name='Changed later',price=99,sale_price=null where id=1");
        fresh();
        get("/api/orders/"+id,CUSTOMER).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(35))
                .andExpect(jsonPath("$.data.items[0].productName").value(originalName))
                .andExpect(jsonPath("$.data.deliveryAddress.address").value("12 Test Street"));
        get("/api/orders/"+id,STAFF).andExpect(status().isNotFound());
    }

    @Test void successfulRetryReturnsSameOrderAndLeavesNewCartContentsUntouched() throws Exception {
        String key=UUID.randomUUID().toString(), body=request(token(),delivery);
        String first=place(key,body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        fresh();
        jdbc.update("update products set status='ACTIVE',stock_quantity=3 where id=1");
        post("/api/cart/items",CUSTOMER,"{\"productId\":1,\"quantity\":1}").andExpect(status().isOk());
        fresh();
        place(key,body).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value((Number)JsonPath.read(first,"$.data.id")));
        get("/api/cart",CUSTOMER).andExpect(jsonPath("$.data.items[0].quantity").value(1));
        place(key,body.replace("First","Different")).andExpect(status().isConflict());
    }

    @Test void selectsOwnedSavedAddressAndSnapshotsRecipient() throws Exception {
        long address=jdbc.queryForObject("select id from customer_addresses where user_id=? limit 1",Long.class,buyer.getId());
        String recipient=jdbc.queryForObject("select recipient_name from customer_addresses where id=?",String.class,address);
        get("/api/checkout/addresses",CUSTOMER).andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        place(UUID.randomUUID().toString(),request(token(),"\"addressId\":"+address)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.deliveryAddress.recipientName").value(recipient));
    }

    @Test void newAddressDistrictAndWardSurviveCheckoutAndLaterAddressChanges() throws Exception {
        var created=mvc.perform(MockMvcRequestBuilders.post("/api/addresses").with(user(buyer)).contentType("application/json")
                .content("{\"recipientName\":\"Saved Buyer\",\"phone\":\"0901234567\",\"city\":\"HCM\",\"district\":\"Quận 1\",\"ward\":\"Bến Nghé\",\"address\":\"12 Test\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long address=((Number)JsonPath.read(created,"$.data.id")).longValue();fresh();
        String checkoutKey=UUID.randomUUID().toString(),checkoutBody=request(token(),"\"addressId\":"+address);
        var result=place(checkoutKey,checkoutBody).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.deliveryAddress.district").value("Quận 1")).andExpect(jsonPath("$.data.deliveryAddress.ward").value("Bến Nghé"))
                .andReturn().getResponse().getContentAsString();
        long order=((Number)JsonPath.read(result,"$.data.id")).longValue();
        fresh();
        mvc.perform(MockMvcRequestBuilders.put("/api/addresses/{id}",address).with(user(buyer)).contentType("application/json")
                .content("{\"recipientName\":\"Changed Buyer\",\"phone\":\"0901234567\",\"city\":\"Changed city\",\"district\":\"Changed district\",\"ward\":\"Changed ward\",\"address\":\"Changed street\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.ward").value("Changed ward"));fresh();
        get("/api/orders/"+order,CUSTOMER).andExpect(status().isOk()).andExpect(jsonPath("$.data.deliveryAddress.district").value("Quận 1"));
        get("/api/admin/orders/"+order,ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.data.deliveryAddress.ward").value("Bến Nghé"));
        mvc.perform(MockMvcRequestBuilders.delete("/api/addresses/{id}",address).with(user(buyer))).andExpect(status().isOk());fresh();
        assertEquals(0,jdbc.queryForObject("select count(*) from customer_addresses where id=?",Integer.class,address));
        get("/api/orders/"+order,CUSTOMER).andExpect(status().isOk()).andExpect(jsonPath("$.data.deliveryAddress.district").value("Quận 1"));
        get("/api/admin/orders/"+order,ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.data.deliveryAddress.ward").value("Bến Nghé"));
        place(checkoutKey,checkoutBody).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(order))
                .andExpect(jsonPath("$.data.deliveryAddress.address").value("12 Test"));
    }

    @Test void foreignAddressAndConflictingAddressSourcesAreRejected() throws Exception {
        long foreign=jdbc.queryForObject("select id from customer_addresses where user_id<>? limit 1",Long.class,buyer.getId());
        String token=token();
        place(UUID.randomUUID().toString(),request(token,"\"addressId\":"+foreign)).andExpect(status().isNotFound());
        place(UUID.randomUUID().toString(),request(token,delivery+",\"addressId\":"+foreign)).andExpect(status().isBadRequest());
        assertStockAndCartUnchanged();
    }

    @Test void shippingThresholdIsInclusive() throws Exception {
        jdbc.update("update products set sale_price=null,price=49.49 where id=1"); fresh();
        preview().andExpect(status().isOk()).andExpect(jsonPath("$.data.shippingFee").value(5));
        jdbc.update("update products set price=49.50 where id=1"); fresh();
        preview().andExpect(status().isOk()).andExpect(jsonPath("$.data.shippingFee").value(0));
    }

    @ParameterizedTest @ValueSource(strings={"price","quantity","shipping"})
    void changedCheckoutSnapshotRequiresNewConfirmation(String change) throws Exception {
        String token=token();
        if(change.equals("price")) jdbc.update("update products set sale_price=14 where id=1");
        else if(change.equals("quantity")) jdbc.update("update cart_items set quantity=1 where cart_id=(select id from carts where user_id=?)",buyer.getId());
        else jdbc.update("update settings set setting_value='6' where setting_key='shipping_fee'");
        fresh();
        place(UUID.randomUUID().toString(),request(token,delivery)).andExpect(status().isConflict());
        assertEquals(2,jdbc.queryForObject("select stock_quantity from products where id=1",Integer.class));
    }

    @ParameterizedTest @ValueSource(strings={"HIDDEN","OUT_OF_STOCK","NO_STOCK","CATEGORY"})
    void unavailableCartDoesNotPartiallyCheckout(String state) throws Exception {
        String token=token();
        if(state.equals("NO_STOCK")) jdbc.update("update products set stock_quantity=1 where id=1");
        else if(state.equals("CATEGORY")) jdbc.update("update categories set is_active=false where id=(select category_id from products where id=1)");
        else jdbc.update("update products set status=? where id=1",state);
        fresh();
        long before=jdbc.queryForObject("select count(*) from orders",Long.class);
        place(UUID.randomUUID().toString(),request(token,delivery)).andExpect(status().isConflict());
        assertEquals(before,jdbc.queryForObject("select count(*) from orders",Long.class));
        assertEquals(2,jdbc.queryForObject("select quantity from cart_items where cart_id=(select id from carts where user_id=?)",Integer.class,buyer.getId()));
    }

    @ParameterizedTest @ValueSource(strings={"shipping_fee","free_shipping_threshold","currency"})
    void invalidSettingsCannotCreateAnOrder(String setting) throws Exception {
        jdbc.update("update settings set setting_value='invalid' where setting_key=?",setting); fresh();
        preview().andExpect(status().isConflict());
        assertStockAndCartUnchanged();
    }

    @Test void overflowIsRejectedBeforeDatabaseWrite() throws Exception {
        jdbc.update("update products set price=9999999999.99,sale_price=null where id=1");fresh();
        preview().andExpect(status().isConflict());
        assertStockAndCartUnchanged();
    }

    @Test void invalidRequestPaymentCouponAndHeaderAreRejected() throws Exception {
        String token=token(), key=UUID.randomUUID().toString();
        place(key,"{}").andExpect(status().isBadRequest());
        place("not-a-uuid",request(token,delivery)).andExpect(status().isBadRequest());
        place(key,request(token,delivery).replace("COD","PAYPAL")).andExpect(status().isBadRequest());
        place(key,request(token,delivery+",\"couponCode\":\"SAVE10\"")).andExpect(status().isBadRequest());
        assertStockAndCartUnchanged();
    }

    @Test void emptyCartAndUnauthenticatedCallsAreRejected() throws Exception {
        post("/api/checkout/preview",ANONYMOUS,"").andExpect(status().isUnauthorized());
        jdbc.update("delete from cart_items where cart_id=(select id from carts where user_id=?)",buyer.getId());fresh();
        preview().andExpect(status().isConflict());
    }

    @Test void maximumValidContactNamesFitDeliverySnapshot() throws Exception {
        String body=request(token(),delivery).replace("First","F".repeat(60)).replace("Last","L".repeat(60));
        place(UUID.randomUUID().toString(),body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.deliveryAddress.recipientName").value("F".repeat(60)+" "+"L".repeat(60)));
    }

    @Test void fractionalSavedAddressIdCannotBeTruncated() throws Exception {
        long id=jdbc.queryForObject("select id from customer_addresses where user_id=? limit 1",Long.class,buyer.getId());
        place(UUID.randomUUID().toString(),request(token(),"\"addressId\":"+id+".5")).andExpect(status().isBadRequest());
        assertStockAndCartUnchanged();
    }

    @Test void paymentMethodMustBeExplicitEnumNameNotOrdinal() throws Exception {
        place(UUID.randomUUID().toString(),request(token(),delivery).replace("\"COD\"","0")).andExpect(status().isBadRequest());
        assertStockAndCartUnchanged();
    }

    @Test void oneUnavailableItemRejectsTheWholeMultiProductOrder() throws Exception {
        jdbc.update("update products set status='ACTIVE',stock_quantity=4,price=7,sale_price=null where id=2");
        post("/api/cart/items",CUSTOMER,"{\"productId\":2,\"quantity\":1}").andExpect(status().isOk());
        fresh();String token=token();
        long before=jdbc.queryForObject("select count(*) from orders",Long.class);
        jdbc.update("update products set stock_quantity=0 where id=2");fresh();
        place(UUID.randomUUID().toString(),request(token,delivery)).andExpect(status().isConflict());
        assertEquals(before,jdbc.queryForObject("select count(*) from orders",Long.class));
        assertEquals(2,jdbc.queryForObject("select stock_quantity from products where id=1",Integer.class));
        assertEquals(2,jdbc.queryForObject("select count(*) from cart_items where cart_id=(select id from carts where user_id=?)",Integer.class,buyer.getId()));
    }

    @Test void validZeroProductPriceDoesNotBecomeNegativeOrUseAClientPrice() throws Exception {
        jdbc.update("update products set price=0,sale_price=null where id=1");fresh();
        place(UUID.randomUUID().toString(),request(token(),delivery+",\"subtotal\":999,\"shippingFee\":0"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.subtotal").value(0))
                .andExpect(jsonPath("$.data.shippingFee").value(5)).andExpect(jsonPath("$.data.total").value(5));
    }

    private ResultActions preview() throws Exception { return post("/api/checkout/preview",CUSTOMER,""); }
    private String token() throws Exception {
        return JsonPath.read(preview().andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),"$.data.cartToken");
    }
    private String request(String token,String address) {
        return "{\"cartToken\":\""+token+"\",\"firstName\":\"First\",\"lastName\":\"Last\",\"email\":\"buyer@example.com\",\"paymentMethod\":\"COD\","+address+"}";
    }
    private ResultActions place(String key,String body) throws Exception {
        fresh();
        return mvc.perform(MockMvcRequestBuilders.post("/api/checkout").with(user(buyer))
                .header("Idempotency-Key",key).contentType("application/json").content(body));
    }
    private void fresh() { em.flush(); em.clear(); }
    private void assertStockAndCartUnchanged() {
        assertEquals(2,jdbc.queryForObject("select stock_quantity from products where id=1",Integer.class));
        assertEquals(2,jdbc.queryForObject("select quantity from cart_items where cart_id=(select id from carts where user_id=?)",Integer.class,buyer.getId()));
    }
}
