package com.ogani.store.user.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public record CreateAddressRequest(
        @NotBlank @Size(max=120) String recipientName,
        @NotBlank @Pattern(regexp="(?:0[35789][0-9]{8}|02[0-9]{9})") String phone,
        @NotBlank @Size(max=80) String city,
        @NotBlank @Size(max=80) String district,
        @NotBlank @Size(max=80) String ward,
        @NotBlank @Size(max=255) String address,
        boolean defaultAddress) {
    public CreateAddressRequest {
        recipientName=normalize(recipientName);phone=normalize(phone);city=normalize(city);
        district=normalize(district);ward=normalize(ward);address=normalize(address);
        if(phone!=null && phone.startsWith("+84")) phone="0"+phone.substring(3);
    }
    @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
    public static CreateAddressRequest fromJson(JsonNode node) {
        if(node==null || !node.isObject()) throw new IllegalArgumentException("Address must be an object");
        var flag=node.get("defaultAddress");
        if(flag!=null && !flag.isBoolean()) throw new IllegalArgumentException("defaultAddress must be boolean");
        return new CreateAddressRequest(text(node,"recipientName"),text(node,"phone"),text(node,"city"),
                text(node,"district"),text(node,"ward"),text(node,"address"),flag!=null && flag.booleanValue());
    }
    private static String text(JsonNode node,String field) {
        var value=node.get(field);
        if(value==null || value.isNull()) return null;
        if(!value.isString()) throw new IllegalArgumentException(field+" must be a string");
        return value.stringValue();
    }
    private static String normalize(String value) {
        if(value==null) return null;
        if(value.codePoints().anyMatch(c -> Character.isISOControl(c) && !Character.isWhitespace(c)))
            throw new IllegalArgumentException("Control characters are not allowed");
        return value.replaceAll("[\\p{Z}\\s]+"," ").strip();
    }
}
