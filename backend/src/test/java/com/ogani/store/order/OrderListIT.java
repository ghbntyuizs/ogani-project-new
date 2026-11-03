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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderListIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    private long buyerId;
    private final List<Long> ids=new ArrayList<>();

    @BeforeEach void fixtures() {
        buyerId=jdbc.queryForObject("select id from users where email=?",Long.class,CUSTOMER);
        jdbc.update("delete from orders where user_id in (select id from users where email in (?,?,?))",CUSTOMER,ADMIN,STAFF);
        ids.add(insert(buyerId,"PENDING","2026-11-01 10:00:00"));
        ids.add(insert(buyerId,"COMPLETED","2026-11-02 10:00:00"));
        ids.add(insert(buyerId,"PENDING","2026-11-02 10:00:00"));
        insert(4L,"PENDING","2026-11-03 10:00:00");
        insert(null,"PENDING","2026-11-03 10:00:00");
    }

    @Test void defaultsReturnOnlyOwnSummariesWithStableNewestFirstOrder() throws Exception {
        get("/api/orders",CUSTOMER).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.page").value(0)).andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(3)).andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(ids.get(2)))
                .andExpect(jsonPath("$.data.content[1].id").value(ids.get(1)))
                .andExpect(jsonPath("$.data.content[2].id").value(ids.get(0)))
                .andExpect(jsonPath("$.data.content[0].orderCode").isString())
                .andExpect(jsonPath("$.data.content[0].orderStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.content[0].total").value(25))
                .andExpect(jsonPath("$.data.content[0].currency").value("USD"))
                .andExpect(jsonPath("$.data.content[0].checkoutKey").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].checkoutRequestHash").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].deliveryAddress").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].items").doesNotExist());
    }

    @Test void pagesDoNotRepeatOrdersWithEqualCreationTimes() throws Exception {
        List<Long> result=new ArrayList<>();
        for(int page=0;page<3;page++) {
            String json=get("/api/orders?page="+page+"&size=1",CUSTOMER).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(3)).andExpect(jsonPath("$.data.totalPages").value(3))
                    .andReturn().getResponse().getContentAsString();
            result.add(((Number)JsonPath.read(json,"$.data.content[0].id")).longValue());
        }
        assertEquals(List.of(ids.get(2),ids.get(1),ids.get(0)),result);
    }

    @Test void ascendingDateSortUsesMatchingIdTieBreaker() throws Exception {
        get("/api/orders?sort=createdAt,asc",CUSTOMER).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(ids.get(0)))
                .andExpect(jsonPath("$.data.content[1].id").value(ids.get(1)))
                .andExpect(jsonPath("$.data.content[2].id").value(ids.get(2)));
    }

    @ParameterizedTest @ValueSource(strings={"PENDING","CONFIRMED","SHIPPING","COMPLETED","CANCELLED"})
    void filtersAllSupportedStatusesAndScopesCounts(String statusName) throws Exception {
        int count=statusName.equals("PENDING")?2:statusName.equals("COMPLETED")?1:0;
        get("/api/orders?status="+statusName+"&size=1",CUSTOMER).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(count)).andExpect(jsonPath("$.data.totalPages").value(count));
    }

    @Test void emptyAndBeyondLastPageReturnSuccessfulEmptyPages() throws Exception {
        get("/api/orders?page=10&size=2",CUSTOMER).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty()).andExpect(jsonPath("$.data.totalElements").value(3));
        jdbc.update("delete from orders where user_id=?",buyerId);
        get("/api/orders",CUSTOMER).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty()).andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.totalPages").value(0));
    }

    @Test void adminAndStaffCanOnlyReadTheirOwnOrdersAndCannotSpoofOwner() throws Exception {
        long adminId=jdbc.queryForObject("select id from users where email=?",Long.class,ADMIN);
        long own=insert(adminId,"SHIPPING","2026-11-01 10:00:00");
        get("/api/orders?userId="+buyerId,ADMIN).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1)).andExpect(jsonPath("$.data.content[0].id").value(own));
        get("/api/orders?userId="+buyerId,STAFF).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty()).andExpect(jsonPath("$.data.totalElements").value(0));
        get("/api/orders?userId="+adminId,CUSTOMER).andExpect(jsonPath("$.data.totalElements").value(3));
    }

    @Test void anonymousInvalidTokenAndBlockedAccountsAreRejected() throws Exception {
        get("/api/orders",ANONYMOUS).andExpect(status().isUnauthorized());
        getWithRawAuthHeader("/api/orders","Bearer invalid").andExpect(status().isUnauthorized());
        UserPrincipal captured=UserPrincipal.from(users.findById(buyerId).orElseThrow());
        jdbc.update("update users set status='BLOCKED' where id=?",buyerId);em.clear();
        mvc.perform(MockMvcRequestBuilders.get("/api/orders").with(user(captured))).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @ValueSource(strings={"page=-1","page=1.5","page=oops","page=2147483648","page=",
            "size=0","size=-1","size=101","size=1.5","size=oops","size=2147483648","size=",
            "page=2147483647&size=100","status=INVALID","status=pending","status=",
            "sort=price,desc","sort=createdAt,invalid","sort=createdAt","sort=","sort=createdAt,asc,id"})
    void rejectsInvalidOrUnsupportedQueryParameters(String query) throws Exception {
        get("/api/orders?"+query,CUSTOMER).andExpect(status().isBadRequest());
    }

    @Test void maximumPageSizeIsAcceptedAndListingDoesNotMutateOrders() throws Exception {
        var before=jdbc.queryForList("select id,order_status,payment_status,total,updated_at from orders where user_id=? order by id",buyerId);
        get("/api/orders?size=100",CUSTOMER).andExpect(status().isOk()).andExpect(jsonPath("$.data.size").value(100));
        assertEquals(before,jdbc.queryForList("select id,order_status,payment_status,total,updated_at from orders where user_id=? order by id",buyerId));
    }

    private long insert(Long owner,String status,String date) {
        return jdbc.queryForObject("insert into orders(order_code,user_id,first_name,last_name,email,phone,ship_country,ship_address,ship_city,ship_recipient_name,subtotal,total,order_status,created_at,checkout_key,checkout_request_hash) "
                +"values(?,?,'List','Test','list@example.test','0901234567','Vietnam','12 Test','HCM','List Test',25,25,?,?::timestamp,?,'internal-secret-hash') returning id",
                Long.class,"LST-"+UUID.randomUUID().toString().replace("-","").substring(0,16),owner,status,date,UUID.randomUUID().toString());
    }
}
