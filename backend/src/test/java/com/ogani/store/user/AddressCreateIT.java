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

class AddressCreateIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    static final String BODY="{\"recipientName\":\"  Nguyễn   Văn An  \",\"phone\":\"+84901234567\",\"city\":\"HCM\",\"district\":\"Quận 1\",\"ward\":\"Bến Nghé\",\"address\":\"12 Nguyễn Huệ\"}";
    @BeforeEach void clean() { jdbc.update("delete from customer_addresses where user_id=3"); }
    ResultActions create(String body,String actor) throws Exception {
        var req=MockMvcRequestBuilders.post("/api/addresses").contentType("application/json").content(body);
        if(actor!=null) req.with(user(UserPrincipal.from(users.findByEmail(actor).orElseThrow())));
        return mvc.perform(req);
    }
    @Test void firstAddressIsDefaultOwnedAndNormalized() throws Exception {
        create(BODY,CUSTOMER).andExpect(status().isCreated()).andExpect(jsonPath("$.data.recipientName").value("Nguyễn Văn An")).andExpect(jsonPath("$.data.phone").value("0901234567")).andExpect(jsonPath("$.data.defaultAddress").value(true)).andExpect(jsonPath("$.data.district").value("Quận 1")).andExpect(jsonPath("$.data.ward").value("Bến Nghé"));
        assertEquals(3L,jdbc.queryForObject("select user_id from customer_addresses where user_id=3",Long.class));
    }
    @Test void laterDefaultReplacesOldAndFalseKeepsCurrent() throws Exception {
        create(BODY,CUSTOMER).andExpect(status().isCreated());
        create(BODY,CUSTOMER).andExpect(status().isCreated()).andExpect(jsonPath("$.data.defaultAddress").value(false));
        create(BODY.replace("\"city\"","\"defaultAddress\":true,\"city\""),CUSTOMER).andExpect(status().isCreated()).andExpect(jsonPath("$.data.defaultAddress").value(true));
        assertEquals(1,jdbc.queryForObject("select count(*) from customer_addresses where user_id=3 and is_default",Integer.class));
        assertTrue(jdbc.queryForObject("select is_default from customer_addresses where user_id=3 order by id desc limit 1",Boolean.class));
    }
    @Test void eleventhFailsWithoutChangingDefaults() throws Exception {
        for(int i=0;i<10;i++) create(BODY,CUSTOMER).andExpect(status().isCreated());
        var before=jdbc.queryForList("select * from customer_addresses where user_id=3 order by id");
        create(BODY.replace("\"city\"","\"defaultAddress\":true,\"city\""),CUSTOMER).andExpect(status().isConflict());
        assertEquals(before,jdbc.queryForList("select * from customer_addresses where user_id=3 order by id"));
    }
    @ParameterizedTest @ValueSource(strings={"recipientName","phone","city","district","ward","address"})
    void requiredFields(String field) throws Exception {
        create(BODY.replaceFirst("\""+field+"\":\"[^\"]*\"","\""+field+"\":\"   \""),CUSTOMER).andExpect(status().isBadRequest());
    }
    @ParameterizedTest @ValueSource(strings={"123","0123456789","09012345678","abc","+10901234567"})
    void invalidPhones(String phone) throws Exception { create(BODY.replace("+84901234567",phone),CUSTOMER).andExpect(status().isBadRequest()); }
    @Test void rejectsWrongTypesAndUnauthorized() throws Exception {
        create(BODY.replace("\"HCM\"","123"),CUSTOMER).andExpect(status().isBadRequest());
        create(BODY.replace("\"city\"","\"defaultAddress\":\"true\",\"city\""),CUSTOMER).andExpect(status().isBadRequest());
        create(BODY,null).andExpect(status().isUnauthorized());create(BODY,ADMIN).andExpect(status().isForbidden());create(BODY,STAFF).andExpect(status().isForbidden());
    }

    @ParameterizedTest @ValueSource(strings={"recipientName","city","district","ward","address"})
    void acceptsExactLimitAndRejectsTooLong(String field) throws Exception {
        int max=field.equals("recipientName")?120:field.equals("address")?255:80;
        String pattern="\""+field+"\":\"[^\"]*\"";
        create(BODY.replaceFirst(pattern,"\""+field+"\":\""+"a".repeat(max)+"\""),CUSTOMER).andExpect(status().isCreated());
        create(BODY.replaceFirst(pattern,"\""+field+"\":\""+"a".repeat(max+1)+"\""),CUSTOMER).andExpect(status().isBadRequest());
    }

    @Test void ignoresClientOwnerAndListsOnlyOwnedAddresses() throws Exception {
        create(BODY.replace("\"city\"","\"userId\":1,\"city\""),CUSTOMER).andExpect(status().isCreated());
        assertEquals(1,jdbc.queryForObject("select count(*) from customer_addresses where user_id=3",Integer.class));
        get("/api/checkout/addresses",CUSTOMER).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].district").value("Quận 1")).andExpect(jsonPath("$.data[0].ward").value("Bến Nghé"));
    }

    @Test void checksCurrentAccountStatusAndRole() throws Exception {
        var principal=UserPrincipal.from(users.findByEmail(CUSTOMER).orElseThrow());
        jdbc.update("update users set status='BLOCKED' where id=3");em.clear();
        mvc.perform(MockMvcRequestBuilders.post("/api/addresses").with(user(principal)).contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
        jdbc.update("update users set status='ACTIVE',role_id=2 where id=3");em.clear();
        mvc.perform(MockMvcRequestBuilders.post("/api/addresses").with(user(principal)).contentType("application/json").content(BODY)).andExpect(status().isForbidden());
    }
}
