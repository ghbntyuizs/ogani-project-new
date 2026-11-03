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

class AddressDeleteDefaultIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    long first,second,third;
    @BeforeEach void fixture() {
        jdbc.update("delete from customer_addresses where user_id=3");
        first=insert(true);second=insert(false);third=insert(false);
    }
    long insert(boolean flag) {
        return jdbc.queryForObject("insert into customer_addresses(user_id,recipient_name,phone,address_line,city,is_default) values(3,'Buyer','0901234567','Street','HCM',?) returning id",Long.class,flag);
    }
    ResultActions command(boolean remove,String id,String actor) throws Exception {
        var req=remove?MockMvcRequestBuilders.delete("/api/addresses/"+id):MockMvcRequestBuilders.patch("/api/addresses/"+id+"/default");
        if(actor!=null) req.with(user(UserPrincipal.from(users.findByEmail(actor).orElseThrow())));
        return mvc.perform(req);
    }
    @Test void deletingNondefaultPreservesDefaultAndOtherOwners() throws Exception {
        var foreign=jdbc.queryForList("select * from customer_addresses where user_id<>3 order by id");
        command(true,""+second,CUSTOMER).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        assertEquals(0,jdbc.queryForObject("select count(*) from customer_addresses where id=?",Integer.class,second));
        assertEquals(first,defaultId());assertEquals(foreign,jdbc.queryForList("select * from customer_addresses where user_id<>3 order by id"));
        command(true,""+second,CUSTOMER).andExpect(status().isNotFound());
    }
    @Test void deletingDefaultPromotesOldestRemainingThenLastDeletionLeavesEmpty() throws Exception {
        command(true,""+first,CUSTOMER).andExpect(status().isOk());assertEquals(second,defaultId());em.clear();
        command(true,""+second,CUSTOMER).andExpect(status().isOk());assertEquals(third,defaultId());em.clear();
        command(true,""+third,CUSTOMER).andExpect(status().isOk());
        get("/api/checkout/addresses",CUSTOMER).andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    }
    @Test void defaultSelectionIsRepeatableAndPreservesAllAddressDetails() throws Exception {
        var before=jdbc.queryForMap("select recipient_name,phone,address_line,city,district,ward from customer_addresses where id=?",second);
        command(false,""+second,CUSTOMER).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(second)).andExpect(jsonPath("$.data.defaultAddress").value(true));em.clear();
        command(false,""+second,CUSTOMER).andExpect(status().isOk());assertEquals(second,defaultId());
        assertEquals(1,jdbc.queryForObject("select count(*) from customer_addresses where user_id=3 and is_default",Integer.class));
        assertEquals(before,jdbc.queryForMap("select recipient_name,phone,address_line,city,district,ward from customer_addresses where id=?",second));
        get("/api/checkout/addresses",CUSTOMER).andExpect(jsonPath("$.data[0].id").value(second));
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void hidesForeignAndMissingAddresses(boolean remove) throws Exception {
        long foreign=jdbc.queryForObject("select id from customer_addresses where user_id=4 limit 1",Long.class);
        var before=jdbc.queryForList("select * from customer_addresses order by id");
        command(remove,""+foreign,CUSTOMER).andExpect(status().isNotFound());
        command(remove,""+Long.MAX_VALUE,CUSTOMER).andExpect(status().isNotFound());
        assertEquals(before,jdbc.queryForList("select * from customer_addresses order by id"));
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void rejectsAnonymousAdminAndStaff(boolean remove) throws Exception {
        command(remove,""+second,null).andExpect(status().isUnauthorized());
        command(remove,""+second,ADMIN).andExpect(status().isForbidden());command(remove,""+second,STAFF).andExpect(status().isForbidden());
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","oops","1.5","9223372036854775808"})
    void validatesBothPathIds(String id) throws Exception {
        command(true,id,CUSTOMER).andExpect(status().isBadRequest());command(false,id,CUSTOMER).andExpect(status().isBadRequest());
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void rejectsStalePrincipalAfterBlockingOrRoleChange(boolean remove) throws Exception {
        var principal=UserPrincipal.from(users.findByEmail(CUSTOMER).orElseThrow());
        jdbc.update("update users set status='BLOCKED' where id=3");em.clear();
        var req=remove?MockMvcRequestBuilders.delete("/api/addresses/"+second):MockMvcRequestBuilders.patch("/api/addresses/"+second+"/default");
        mvc.perform(req.with(user(principal))).andExpect(status().isUnauthorized());
        jdbc.update("update users set status='ACTIVE',role_id=2 where id=3");em.clear();
        req=remove?MockMvcRequestBuilders.delete("/api/addresses/"+second):MockMvcRequestBuilders.patch("/api/addresses/"+second+"/default");
        mvc.perform(req.with(user(principal))).andExpect(status().isForbidden());
    }
    long defaultId() { return jdbc.queryForObject("select id from customer_addresses where user_id=3 and is_default",Long.class); }
}
