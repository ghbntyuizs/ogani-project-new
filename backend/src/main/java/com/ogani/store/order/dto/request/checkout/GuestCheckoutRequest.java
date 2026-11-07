package com.ogani.store.order.dto.request.checkout;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.ogani.store.user.dto.request.CreateAddressRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import tools.jackson.databind.JsonNode;
import java.util.List;

public record GuestCheckoutRequest(
        @NotBlank @Pattern(regexp="[0-9a-f]{64}") String scope,
        @NotBlank @Pattern(regexp="[0-9a-f]{64}") String cartToken,
        @NotBlank @Size(max=60) String firstName,
        @NotBlank @Size(max=60) String lastName,
        @NotBlank @Email @Size(max=150) String email,
        @NotBlank @Pattern(regexp="COD") String paymentMethod,
        @NotNull @Valid CreateAddressRequest deliveryAddress,
        @Size(max=2000) String orderNotes,
        List<GuestCartRequest.Line> items) {
    public GuestCheckoutRequest { items=new GuestCartRequest(items).items(); }
    @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
    public static GuestCheckoutRequest fromJson(JsonNode node) {
        if(node==null || !node.isObject())throw new IllegalArgumentException("Checkout must be an object");
        var address=node.get("deliveryAddress");
        return new GuestCheckoutRequest(text(node,"scope"),text(node,"cartToken"),text(node,"firstName"),text(node,"lastName"),
                text(node,"email"),text(node,"paymentMethod"),address==null || address.isNull()?null:CreateAddressRequest.fromJson(address),
                text(node,"orderNotes"),GuestCartRequest.fromItems(node.get("items")).items());
    }
    private static String text(JsonNode node,String field){
        var value=node.get(field);
        if(value==null || value.isNull())return null;
        if(!value.isString())throw new IllegalArgumentException(field+" must be a string");
        return value.stringValue().strip();
    }
}
