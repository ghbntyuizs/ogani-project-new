package com.ogani.store.order;

import com.jayway.jsonpath.JsonPath;
import com.ogani.store.common.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import jakarta.persistence.EntityManager;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.UUID;

class GuestCheckoutIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    private Cookie cookie;private String scope;
    private static final String ITEMS="\"items\":[{\"productId\":1,\"quantity\":2}]";
    @BeforeEach void prepare() throws Exception {
        jdbc.update("update products set stock_quantity=3,status='ACTIVE',price=15,sale_price=null where id=1");
        jdbc.update("update categories set is_active=true where id=(select category_id from products where id=1)");
        jdbc.update("update settings set setting_value='5' where setting_key='shipping_fee'");
        jdbc.update("update settings set setting_value='100' where setting_key='free_shipping_threshold'");
        jdbc.update("update settings set setting_value='USD' where setting_key='currency'");
        var response=mvc.perform(MockMvcRequestBuilders.post("/api/guest/session")).andExpect(status().isOk()).andReturn().getResponse();
        cookie=response.getCookie("ogani_guest");assertNotNull(cookie);scope=JsonPath.read(response.getContentAsString(),"$.data.scope");
    }
    private String token() throws Exception {return JsonPath.read(mvc.perform(MockMvcRequestBuilders.post("/api/guest/checkout/preview").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content("{"+ITEMS+"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(35)).andReturn().getResponse().getContentAsString(),"$.data.cartToken");}
    private String body(String token){return "{"+ITEMS+",\"scope\":\""+scope+"\",\"cartToken\":\""+token+"\",\"firstName\":\"Guest\",\"lastName\":\"Test\",\"email\":\"guest@example.test\",\"paymentMethod\":\"COD\",\"deliveryAddress\":{\"recipientName\":\"Guest Test\",\"phone\":\"0901234567\",\"city\":\"Test City\",\"district\":\"Test District\",\"ward\":\"Test Ward\",\"address\":\"12 Test Street\"}}";}
    @Test void anonymousOrderUsesSnapshotsAndScopedReplay() throws Exception {
        String request=body(token()),key=UUID.randomUUID().toString();
        var first=mvc.perform(MockMvcRequestBuilders.post("/api/guest/checkout").cookie(cookie).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.orderStatus").value("PENDING")).andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.data.deliveryAddress.district").value("Test District")).andReturn().getResponse();
        Number id=JsonPath.read(first.getContentAsString(),"$.data.id");
        assertEquals(1,jdbc.queryForObject("select stock_quantity from products where id=1",Integer.class));
        assertNull(jdbc.queryForObject("select user_id from orders where id=?",Long.class,id.longValue()));
        mvc.perform(MockMvcRequestBuilders.post("/api/guest/checkout").cookie(cookie).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id.longValue()));
        mvc.perform(MockMvcRequestBuilders.post("/api/guest/checkout").cookie(cookie).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(request.replace("Guest Test","Another Recipient")))
                .andExpect(status().isConflict());
        mvc.perform(MockMvcRequestBuilders.get("/api/guest/orders/"+id).cookie(cookie)).andExpect(status().isOk());
        mvc.perform(MockMvcRequestBuilders.get("/api/guest/orders/"+id).cookie(new Cookie("ogani_guest","a".repeat(64)))).andExpect(status().isNotFound());
        mvc.perform(MockMvcRequestBuilders.get("/api/guest/orders/"+id)).andExpect(status().isUnauthorized());
        get("/api/admin/orders/"+id,ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.data.customer.userId").isEmpty());
        get("/api/checkout/addresses",ANONYMOUS).andExpect(status().isUnauthorized());
    }
    @Test void cookieIsHttpOnlyAndGuestDoesNotCreateAddressOrAccount() throws Exception {
        assertTrue(cookie.isHttpOnly());assertEquals("/api/guest",cookie.getPath());assertEquals("Strict",cookie.getAttribute("SameSite"));
        int users=jdbc.queryForObject("select count(*) from users",Integer.class),addresses=jdbc.queryForObject("select count(*) from customer_addresses",Integer.class);
        mvc.perform(MockMvcRequestBuilders.post("/api/guest/checkout").cookie(cookie).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body(token()))).andExpect(status().isCreated());
        assertEquals(users,jdbc.queryForObject("select count(*) from users",Integer.class));assertEquals(addresses,jdbc.queryForObject("select count(*) from customer_addresses",Integer.class));
    }
    @Test void changedPriceAndCookieScopeRejectOldQuoteWithoutDeductingStock() throws Exception {
        String request=body(token());em.flush();em.clear();jdbc.update("update products set price=16 where id=1");
        mvc.perform(MockMvcRequestBuilders.post("/api/guest/checkout").cookie(cookie).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isConflict());
        mvc.perform(MockMvcRequestBuilders.post("/api/guest/checkout").cookie(new Cookie("ogani_guest","b".repeat(64))).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isForbidden());
        assertEquals(3,jdbc.queryForObject("select stock_quantity from products where id=1",Integer.class));
    }
    @Test void unavailableBasketRowsCanBeReadAndRemovedButNotCheckedOut() throws Exception {
        jdbc.update("update products set status='HIDDEN' where id=1");
        mvc.perform(MockMvcRequestBuilders.post("/api/guest/cart").contentType(MediaType.APPLICATION_JSON).content("{"+ITEMS+"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].available").value(false));
        mvc.perform(MockMvcRequestBuilders.post("/api/guest/checkout/preview").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content("{"+ITEMS+"}")).andExpect(status().isConflict());
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","1.5","\"2\"","2147483648"})
    void invalidQuantitiesAreRejected(String quantity) throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/guest/cart").contentType(MediaType.APPLICATION_JSON).content("{\"items\":[{\"productId\":1,\"quantity\":"+quantity+"}]}"))
                .andExpect(status().isBadRequest());
    }
    @Test void duplicateProductsAndEmptyCheckoutAreRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/guest/cart").contentType(MediaType.APPLICATION_JSON).content("{\"items\":[{\"productId\":1,\"quantity\":1},{\"productId\":1,\"quantity\":1}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(MockMvcRequestBuilders.post("/api/guest/checkout/preview").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}")).andExpect(status().isConflict());
    }
    @ParameterizedTest @ValueSource(strings={"090","abc",""}) void invalidPhonesCannotPlace(String phone) throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/guest/checkout").cookie(cookie).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body(token()).replace("0901234567",phone)))
                .andExpect(status().isBadRequest());
    }
    @Test void failedPlacementRollsBackAndSameKeyCanRetry()throws Exception{
        String request=body(token()),key=UUID.randomUUID().toString();em.flush();em.clear();
        jdbc.update("update products set stock_quantity=1 where id=1");
        mvc.perform(MockMvcRequestBuilders.post("/api/guest/checkout").cookie(cookie).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isConflict());
        assertEquals(0,jdbc.queryForObject("select count(*) from orders where guest_scope=?",Integer.class,scope));
        em.clear();jdbc.update("update products set stock_quantity=3 where id=1");
        mvc.perform(MockMvcRequestBuilders.post("/api/guest/checkout").cookie(cookie).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated());
    }
    @ParameterizedTest @ValueSource(strings={"email","firstName","lastName","city","district","ward","address"})
    void blankRequiredContactAndAddressAreRejected(String field)throws Exception{
        String request=body(token()).replaceAll("\""+field+"\":\"[^\"]*\"","\""+field+"\":\" \"");
        mvc.perform(MockMvcRequestBuilders.post("/api/guest/checkout").cookie(cookie).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isBadRequest());
    }
    @Test void guestCannotSupplyMalformedFieldsOrOversizedBasket()throws Exception{
        String request=body(token()).replace("\"firstName\":\"Guest\"","\"firstName\":123");
        mvc.perform(MockMvcRequestBuilders.post("/api/guest/checkout").cookie(cookie).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isBadRequest());
        String rows=IntStream.rangeClosed(1,101).mapToObj(id->"{\"productId\":"+id+",\"quantity\":1}").collect(Collectors.joining(","));
        mvc.perform(MockMvcRequestBuilders.post("/api/guest/cart").contentType(MediaType.APPLICATION_JSON).content("{\"items\":["+rows+"]}")).andExpect(status().isBadRequest());
    }
}



