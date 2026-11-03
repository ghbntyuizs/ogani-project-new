package com.ogani.store.order.repository;

import com.ogani.store.order.dto.request.order.AdminOrderFilter;
import com.ogani.store.order.entity.Order;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import java.util.ArrayList;
import java.util.Locale;

public final class AdminOrderSpecifications {
    private AdminOrderSpecifications() {}

    public static Specification<Order> from(AdminOrderFilter filter) {
        return (root,query,cb) -> {
            var predicates=new ArrayList<Predicate>();
            if(filter.status()!=null) predicates.add(cb.equal(root.get("orderStatus"),filter.status()));
            if(filter.paymentStatus()!=null) predicates.add(cb.equal(root.get("paymentStatus"),filter.paymentStatus()));
            if(filter.dateFrom()!=null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"),filter.dateFrom().atStartOfDay()));
            if(filter.dateTo()!=null) predicates.add(cb.lessThan(root.get("createdAt"),filter.dateTo().plusDays(1).atStartOfDay()));
            if(filter.keyword()!=null) {
                // Use an explicit escape so %, _ and the escape itself are literal user input.
                String pattern="%"+filter.keyword().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
                var name=cb.concat(cb.concat(root.<String>get("firstName")," "),root.<String>get("lastName"));
                predicates.add(cb.or(cb.like(cb.lower(root.get("orderCode")),pattern,'!'),
                        cb.like(cb.lower(name),pattern,'!'),cb.like(cb.lower(root.get("email")),pattern,'!')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
