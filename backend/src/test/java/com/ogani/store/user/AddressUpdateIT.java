package com.ogani.store.user;

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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AddressUpdateIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    long id,other;
    @BeforeEach void fixture() {
        jdbc.update("delete from customer_addresses where user_id=3");
        id=insert(true);other=insert(false);
    }
    long insert(boolean flag) {
        return jdbc.queryForObject("insert into customer_addresses(user_id,recipient_name,phone,address_line,city,is_default) values(3,'Original','0901234567','Old street','Old city',?) returning id",Long.class,flag);
    }
    ResultActions update(long target,String body,String actor) throws Exception {
        var req=MockMvcRequestBuilders.put("/api/addresses/{id}",target).contentType("application/json").content(body);
        if(actor!=null) req.with(user(UserPrincipal.from(users.findByEmail(actor).orElseThrow())));
        return mvc.perform(req);
    }
    String body(boolean flag) { return AddressCreateIT.BODY.replace("\"city\"","\"defaultAddress\":"+flag+",\"city\""); }
    @Test void updatesAllSupportedFieldsAndKeepsIdentityOwnerAndCount() throws Exception {
        update(other,body(false),CUSTOMER).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(other))
                .andExpect(jsonPath("$.data.recipientName").value("Nguyễn Văn An")).andExpect(jsonPath("$.data.phone").value("0901234567"))
                .andExpect(jsonPath("$.data.city").value("HCM")).andExpect(jsonPath("$.data.district").value("Quận 1"))
                .andExpect(jsonPath("$.data.ward").value("Bến Nghé")).andExpect(jsonPath("$.data.address").value("12 Nguyễn Huệ"))
                .andExpect(jsonPath("$.data.defaultAddress").value(false));
        assertEquals(2,jdbc.queryForObject("select count(*) from customer_addresses where user_id=3",Integer.class));
        assertEquals(3L,jdbc.queryForObject("select user_id from customer_addresses where id=?",Long.class,other));
    }
    @Test void falsePreservesExistingDefaultAndTrueSwitchesThenRetriesSafely() throws Exception {
        update(id,body(false),CUSTOMER).andExpect(status().isOk()).andExpect(jsonPath("$.data.defaultAddress").value(true));em.clear();
        update(other,body(true),CUSTOMER).andExpect(status().isOk()).andExpect(jsonPath("$.data.defaultAddress").value(true));em.clear();
        update(other,body(true),CUSTOMER).andExpect(status().isOk());em.clear();
        assertFalse(jdbc.queryForObject("select is_default from customer_addresses where id=?",Boolean.class,id));
        assertTrue(jdbc.queryForObject("select is_default from customer_addresses where id=?",Boolean.class,other));
        assertEquals(1,jdbc.queryForObject("select count(*) from customer_addresses where user_id=3 and is_default",Integer.class));
    }
    @Test void updateAtAddressLimitIsAllowedAndIgnoresClientOwner() throws Exception {
        for(int i=0;i<8;i++) insert(false);
        update(other,body(false).replace("\"city\"","\"userId\":1,\"city\""),CUSTOMER).andExpect(status().isOk());
        assertEquals(10,jdbc.queryForObject("select count(*) from customer_addresses where user_id=3",Integer.class));
        assertEquals(3L,jdbc.queryForObject("select user_id from customer_addresses where id=?",Long.class,other));
    }
    @Test void missingAndForeignAre404AndForeignDataIsUntouched() throws Exception {
        long foreign=jdbc.queryForObject("select id from customer_addresses where user_id=4 limit 1",Long.class);
        var before=jdbc.queryForMap("select * from customer_addresses where id=?",foreign);
        update(foreign,body(true),CUSTOMER).andExpect(status().isNotFound());
        update(Long.MAX_VALUE,body(true),CUSTOMER).andExpect(status().isNotFound());
        assertEquals(before,jdbc.queryForMap("select * from customer_addresses where id=?",foreign));
    }
    @ParameterizedTest @ValueSource(strings={"recipientName","phone","city","district","ward","address"})
    void allFieldsRequiredEvenForPut(String field) throws Exception {
        var before=jdbc.queryForMap("select * from customer_addresses where id=?",other);
        update(other,body(true).replaceFirst("\""+field+"\":\"[^\"]*\"","\""+field+"\":null"),CUSTOMER).andExpect(status().isBadRequest());
        assertEquals(before,jdbc.queryForMap("select * from customer_addresses where id=?",other));
    }
    @ParameterizedTest @ValueSource(strings={"recipientName","city","district","ward","address"})
    void validatesLengthBoundaries(String field) throws Exception {
        int max=field.equals("recipientName")?120:field.equals("address")?255:80;
        String pattern="\""+field+"\":\"[^\"]*\"";
        update(other,body(false).replaceFirst(pattern,"\""+field+"\":\""+"a".repeat(max)+"\""),CUSTOMER).andExpect(status().isOk());
        update(other,body(false).replaceFirst(pattern,"\""+field+"\":\""+"a".repeat(max+1)+"\""),CUSTOMER).andExpect(status().isBadRequest());
    }
    @Test void validatesTypesPhoneAndAccess() throws Exception {
        update(other,body(false).replace("+84901234567","123"),CUSTOMER).andExpect(status().isBadRequest());
        update(other,body(false).replace("\"HCM\"","123"),CUSTOMER).andExpect(status().isBadRequest());
        update(other,body(false).replace("\"defaultAddress\":false","\"defaultAddress\":\"true\""),CUSTOMER).andExpect(status().isBadRequest());
        update(0,body(false),CUSTOMER).andExpect(status().isBadRequest());
        update(other,body(false),null).andExpect(status().isUnauthorized());
        update(other,body(false),ADMIN).andExpect(status().isForbidden());update(other,body(false),STAFF).andExpect(status().isForbidden());
    }
    @Test void checksCurrentAccountForStalePrincipal() throws Exception {
        var principal=UserPrincipal.from(users.findByEmail(CUSTOMER).orElseThrow());
        jdbc.update("update users set status='BLOCKED' where id=3");em.clear();
        mvc.perform(MockMvcRequestBuilders.put("/api/addresses/{id}",other).with(user(principal)).contentType("application/json").content(body(false))).andExpect(status().isUnauthorized());
        jdbc.update("update users set status='ACTIVE',role_id=2 where id=3");em.clear();
        mvc.perform(MockMvcRequestBuilders.put("/api/addresses/{id}",other).with(user(principal)).contentType("application/json").content(body(false))).andExpect(status().isForbidden());
    }

    @Test void repairsLegacyMissingDefaultWhenUpdatingWithFalse() throws Exception {
        jdbc.update("update customer_addresses set is_default=false where user_id=3");em.clear();
        update(other,body(false),CUSTOMER).andExpect(status().isOk()).andExpect(jsonPath("$.data.defaultAddress").value(true));
        assertEquals(1,jdbc.queryForObject("select count(*) from customer_addresses where user_id=3 and is_default",Integer.class));
    }

    @Test void preservesLegacyFieldsOutsideTheSupportedRequest() throws Exception {
        jdbc.update("update customer_addresses set country='Vietnam',state='Legacy state',postcode='700000' where id=?",other);em.clear();
        update(other,body(false),CUSTOMER).andExpect(status().isOk()).andExpect(jsonPath("$.data.country").value("Vietnam"))
                .andExpect(jsonPath("$.data.state").value("Legacy state")).andExpect(jsonPath("$.data.postcode").value("700000"));
    }
}
