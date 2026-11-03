package com.ogani.store.order.dto.request.order;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ogani.store.order.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public record UpdateOrderStatusRequest(@NotNull OrderStatus status,@NotNull @PositiveOrZero Long version,
                                      @Size(max=200) String note) {
    @JsonCreator
    public static UpdateOrderStatusRequest fromJson(@JsonProperty("status") JsonNode status,
            @JsonProperty("version") JsonNode version,@JsonProperty("note") JsonNode note) {
        OrderStatus target=null;Long revision=null;String text=null;
        if(status!=null && !status.isNull()) {
            if(!status.isString()) throw new IllegalArgumentException("status must be an enum name");
            target=OrderStatus.valueOf(status.stringValue());
        }
        if(version!=null && !version.isNull()) {
            if(!version.isIntegralNumber() || !version.canConvertToLong()) throw new IllegalArgumentException("version must be a whole integer");
            revision=version.longValue();
        }
        if(note!=null && !note.isNull()) {
            if(!note.isString()) throw new IllegalArgumentException("note must be a string");
            text=note.stringValue();
        }
        return new UpdateOrderStatusRequest(target,revision,text);
    }
}
