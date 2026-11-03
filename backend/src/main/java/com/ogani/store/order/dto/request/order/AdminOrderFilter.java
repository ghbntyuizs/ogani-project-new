package com.ogani.store.order.dto.request.order;

import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import com.ogani.store.order.entity.OrderStatus;
import com.ogani.store.order.entity.PaymentStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public record AdminOrderFilter(String keyword,OrderStatus status,PaymentStatus paymentStatus,
                               LocalDate dateFrom,LocalDate dateTo,PageRequest pageable) {
    public static AdminOrderFilter parse(String keyword,String status,String paymentStatus,
            String dateFrom,String dateTo,String page,String size,String sort) {
        String search=keyword==null?null:keyword.strip();
        if(search!=null && search.length()>200) throw invalid("keyword must be at most 200 characters");
        if(search!=null && search.isEmpty()) search=null;
        int pageNumber=integer(page,0,"page"),pageSize=integer(size,20,"size");
        if(pageSize<1 || pageSize>100) throw invalid("size must be between 1 and 100");
        if((long)pageNumber*pageSize>Integer.MAX_VALUE) throw invalid("Pagination offset exceeds the supported range");
        String sorting=sort==null?"createdAt,desc":sort;
        if(!sorting.equals("createdAt,asc") && !sorting.equals("createdAt,desc")) throw invalid("sort must be createdAt,asc or createdAt,desc");
        var direction=sorting.endsWith("asc")?Sort.Direction.ASC:Sort.Direction.DESC;
        var from=date(dateFrom,"dateFrom");var to=date(dateTo,"dateTo");
        if(from!=null && to!=null && from.isAfter(to)) throw invalid("dateFrom must not be after dateTo");
        OrderStatus orderState=null;PaymentStatus paymentState=null;
        try { if(status!=null) orderState=OrderStatus.valueOf(status); }
        catch(IllegalArgumentException ex) { throw invalid("Invalid order status"); }
        try { if(paymentStatus!=null) paymentState=PaymentStatus.valueOf(paymentStatus); }
        catch(IllegalArgumentException ex) { throw invalid("Invalid payment status"); }
        return new AdminOrderFilter(search,orderState,paymentState,from,to,
                PageRequest.of(pageNumber,pageSize,Sort.by(direction,"createdAt","id")));
    }

    private static int integer(String raw,int fallback,String name) {
        if(raw==null) return fallback;
        if(!raw.matches("[0-9]+")) throw invalid(name+" must be a non-negative integer");
        try { return Integer.parseInt(raw); }
        catch(NumberFormatException ex) { throw invalid(name+" exceeds supported range"); }
    }

    private static LocalDate date(String raw,String name) {
        if(raw==null) return null;
        if(!raw.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid(name+" must use yyyy-MM-dd");
        try {
            LocalDate result=LocalDate.parse(raw);
            if(result.getYear()<1) throw invalid(name+" year must be between 0001 and 9999");
            return result;
        } catch(DateTimeParseException ex) { throw invalid(name+" is not a valid date"); }
    }

    private static AppException invalid(String message) { return AppException.withCustomMessage(ErrorCode.VALIDATION_ERROR,message); }
}
