package com.ogani.store.order;

import com.jayway.jsonpath.JsonPath;
import com.ogani.store.common.AbstractIntegrationTest;
import com.ogani.store.security.UserPrincipal;
import com.ogani.store.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.ResultActions;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.jpa.properties.hibernate.session_factory.statement_inspector=com.ogani.store.order.AdminOrderListIT$SqlCapture")
class AdminOrderListIT extends AbstractIntegrationTest {
    public static class SqlCapture implements StatementInspector {
        private static final ThreadLocal<List<String>> SQL=new ThreadLocal<>();
        public static void start() { SQL.set(new ArrayList<>()); }
        public static List<String> finish() { var result=List.copyOf(SQL.get()); SQL.remove(); return result; }
        @Override public String inspect(String sql) { if(SQL.get()!=null) SQL.get().add(sql); return sql; }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired UserRepository users;
    private final List<Long> ids=new ArrayList<>();

    @BeforeEach void fixtures() {
        jdbc.update("delete from orders");
        ids.add(insert("ADM-ALPHA",3L,"An","Nguyen","an@example.test","PENDING","UNPAID","2026-11-01T23:59:59.999999"));
        ids.add(insert("ADM-BETA",4L,"Binh","Tran","binh@example.test","SHIPPING","PAID","2026-11-02T00:00:00"));
        ids.add(insert("ADM-GAMMA",null,"O'Brian","100%_Test","old@example.test","CANCELLED","REFUNDED","2026-11-02T23:59:59.999999"));
        ids.add(insert("ADM-DELTA",5L,"An","Nguyen","different@example.test","COMPLETED","PAID","2026-11-03T00:00:00"));
        ids.add(insert("ADM-EPSILON",3L,"Other","Buyer","other@example.test","CONFIRMED","UNPAID","2026-11-03T00:00:00"));
    }

    @Test void adminAndStaffSeeAllOrdersIncludingDeletedOwners() throws Exception {
        for(String role:new String[]{ADMIN,STAFF}) get("/api/admin/orders",role).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.size").value(20)).andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.content[0].id").value(ids.get(4)))
                .andExpect(jsonPath("$.data.content[2].customerName").value("O'Brian 100%_Test"))
                .andExpect(jsonPath("$.data.content[2].customerEmail").value("old@example.test"))
                .andExpect(jsonPath("$.data.content[0].checkoutKey").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].checkoutRequestHash").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].deliveryAddress").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].items").doesNotExist());
    }

    @Test void anonymousAndCustomerAreDenied() throws Exception {
        get("/api/admin/orders",ANONYMOUS).andExpect(status().isUnauthorized());
        get("/api/admin/orders",CUSTOMER).andExpect(status().isForbidden());
        getWithRawAuthHeader("/api/admin/orders","Bearer invalid").andExpect(status().isUnauthorized());
    }

    @Test void blockedOrDemotedStalePrincipalCannotQuery() throws Exception {
        var admin=users.findByEmail(ADMIN).orElseThrow();
        UserPrincipal captured=UserPrincipal.from(admin);
        jdbc.update("update users set status='BLOCKED' where id=?",admin.getId());em.clear();
        mvc.perform(MockMvcRequestBuilders.get("/api/admin/orders").with(user(captured))).andExpect(status().isUnauthorized());
        jdbc.update("update users set status='ACTIVE',role_id=3 where id=?",captured.getId());em.clear();
        mvc.perform(MockMvcRequestBuilders.get("/api/admin/orders").with(user(captured))).andExpect(status().isForbidden());
    }

    @Test void searchesCodesNamesEmailsCaseInsensitivelyAndUsesSnapshots() throws Exception {
        search("adm-beta",ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        search("AN NGUYEN",ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
        search("OLD@EXAMPLE.TEST",STAFF).andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].id").value(ids.get(2)));
        jdbc.update("update users set first_name='Renamed',last_name='Account' where id=3");em.clear();
        search("An Nguyen",ADMIN).andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test void keywordWildcardsQuotesAndBlankAreHandledLiterally() throws Exception {
        for(String keyword:new String[]{"%","_","O'Brian"}) search(keyword,ADMIN)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(ids.get(2)));
        search("\\",ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        get("/api/admin/orders?keyword=",ADMIN).andExpect(jsonPath("$.data.totalElements").value(5));
        search("' OR 1=1",ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isEmpty());
    }

    @ParameterizedTest @ValueSource(strings={"PENDING","CONFIRMED","SHIPPING","COMPLETED","CANCELLED"})
    void supportsEveryOrderStatus(String state) throws Exception {
        get("/api/admin/orders?status="+state,ADMIN).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1)).andExpect(jsonPath("$.data.content[0].orderStatus").value(state));
    }

    @ParameterizedTest @ValueSource(strings={"UNPAID","PAID","REFUNDED"})
    void supportsEveryPaymentStatus(String state) throws Exception {
        get("/api/admin/orders?paymentStatus="+state,STAFF).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(state.equals("REFUNDED")?1:2));
    }

    @Test void dateRangeIncludesBothWholeDaysAndExcludesNextMidnight() throws Exception {
        get("/api/admin/orders?dateFrom=2026-11-02&dateTo=2026-11-02",ADMIN).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].id").value(ids.get(2)))
                .andExpect(jsonPath("$.data.content[1].id").value(ids.get(1)));
        get("/api/admin/orders?dateFrom=2026-11-03",ADMIN).andExpect(jsonPath("$.data.totalElements").value(2));
        get("/api/admin/orders?dateTo=2026-11-01",ADMIN).andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test void combinesFiltersWithAndAndScopesCountsToMatches() throws Exception {
        get("/api/admin/orders?keyword=An&status=COMPLETED&paymentStatus=PAID&dateFrom=2026-11-03&dateTo=2026-11-03&size=1",ADMIN)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1)).andExpect(jsonPath("$.data.content[0].id").value(ids.get(3)));
        get("/api/admin/orders?status=PENDING&paymentStatus=PAID",ADMIN).andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test void escapeCharacterAndTrimmedKeywordRemainLiteral() throws Exception {
        jdbc.update("update orders set first_name='Bang!' where id=?",ids.get(0));
        search("  Bang!  ",ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(ids.get(0)));
    }

    @Test void validLeapDayAndSupportedDateExtremesAreAccepted() throws Exception {
        get("/api/admin/orders?dateFrom=2024-02-29&dateTo=2024-02-29",ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isEmpty());
        get("/api/admin/orders?dateFrom=0001-01-01&dateTo=9999-12-31",ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(5));
    }

    @Test void paginationAndDateSortAreStableWithTiedDates() throws Exception {
        List<Long> results=new ArrayList<>();
        for(int page=0;page<5;page++) {
            String json=get("/api/admin/orders?page="+page+"&size=1&sort=createdAt,asc",ADMIN).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalPages").value(5)).andReturn().getResponse().getContentAsString();
            results.add(((Number)JsonPath.read(json,"$.data.content[0].id")).longValue());
        }
        assertEquals(ids,results);
        get("/api/admin/orders?page=100&size=2",ADMIN).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty()).andExpect(jsonPath("$.data.totalElements").value(5));
    }

    @Test void emptyOrdersReturnAnEmptySuccessfulPage() throws Exception {
        jdbc.update("delete from orders");
        get("/api/admin/orders",ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0)).andExpect(jsonPath("$.data.totalPages").value(0));
    }

    @ParameterizedTest @ValueSource(strings={"page=-1","page=1.5","page=oops","page=2147483648","page=",
            "size=0","size=-1","size=101","size=1.5","size=oops","size=2147483648","size=",
            "page=2147483647&size=100","status=INVALID","status=pending","status=",
            "paymentStatus=INVALID","paymentStatus=paid","paymentStatus=",
            "sort=total,desc","sort=createdAt,invalid","sort=createdAt","sort=",
            "dateFrom=2026-02-30","dateFrom=2026-02-29","dateTo=2026-13-01","dateFrom=2026-11-03&dateTo=2026-11-01",
            "dateFrom=","dateTo=","dateFrom=0000-01-01","dateTo=not-a-date","dateFrom=2026-11-02T12:00:00"})
    void rejectsInvalidQueryParameters(String query) throws Exception { get("/api/admin/orders?"+query,ADMIN).andExpect(status().isBadRequest()); }

    @Test void keywordLimitAndMaximumPageSize() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/admin/orders").param("keyword","x".repeat(201)).with(user(UserPrincipal.from(users.findByEmail(ADMIN).orElseThrow()))))
                .andExpect(status().isBadRequest());
        get("/api/admin/orders?size=100",ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.data.size").value(100));
    }

    @Test void projectionQueriesAreBoundedAndNeverSelectDetailsOrMutateOrders() throws Exception {
        var before=jdbc.queryForList("select id,order_status,payment_status,total,updated_at,version from orders order by id");
        SqlCapture.start();List<String> sql;
        try { get("/api/admin/orders?size=1",ADMIN).andExpect(status().isOk()); }
        finally { sql=SqlCapture.finish(); }
        var orderQueries=sql.stream().filter(s -> s.contains(" from orders ")).toList();
        assertEquals(2,orderQueries.size(),"One paginated select and one count expected");
        assertTrue(orderQueries.stream().noneMatch(s -> s.contains("checkout_key") || s.contains("checkout_request_hash") || s.contains("ship_address") || s.contains("order_notes")));
        assertTrue(sql.stream().noneMatch(s -> s.contains(" from order_items ") || s.contains(" from order_status_history ")));
        assertEquals(before,jdbc.queryForList("select id,order_status,payment_status,total,updated_at,version from orders order by id"));
    }

    private ResultActions search(String keyword,String as) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get("/api/admin/orders").param("keyword",keyword)
                .with(user(UserPrincipal.from(users.findByEmail(as).orElseThrow()))));
    }

    private long insert(String code,Long owner,String first,String last,String email,String orderStatus,String paymentStatus,String time) {
        long id=jdbc.queryForObject("insert into orders(order_code,user_id,first_name,last_name,email,phone,ship_country,ship_address,ship_city,ship_recipient_name,subtotal,total,order_status,payment_status) "
                +"values(?,?,?,?,?,'0901234567','Vietnam','12 Test','HCM','Recipient',25,25,?,?) returning id",Long.class,code,owner,first,last,email,orderStatus,paymentStatus);
        em.createQuery("update Order o set o.createdAt=:date where o.id=:id").setParameter("date",LocalDateTime.parse(time)).setParameter("id",id).executeUpdate();
        return id;
    }
}
