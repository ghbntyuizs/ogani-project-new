package com.ogani.store.user.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;
import java.util.Set;
import java.util.HashSet;

public record UpdateProfileRequest(@Size(max=60) String firstName,@Size(max=60) String lastName,
        @Pattern(regexp="(?:0[35789][0-9]{8}|02[0-9]{9})") String phone,
        @JsonIgnore @Schema(hidden=true) Set<String> provided) {
    @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
    public static UpdateProfileRequest fromJson(JsonNode node) {
        if(node==null || !node.isObject()) throw new IllegalArgumentException("Profile must be an object");
        var fields=new HashSet<String>();
        for(String field:new String[]{"firstName","lastName","phone"}) if(node.get(field)!=null) fields.add(field);
        if(fields.isEmpty() || fields.size()!=node.size()) throw new IllegalArgumentException("Only firstName, lastName and phone may be updated");
        String phone=text(node,"phone");
        if(phone!=null && phone.startsWith("+84")) phone="0"+phone.substring(3);
        return new UpdateProfileRequest(text(node,"firstName"),text(node,"lastName"),phone,Set.copyOf(fields));
    }
    private static String text(JsonNode node,String name) {
        var value=node.get(name);
        if(value==null || value.isNull()) return null;
        if(!value.isString()) throw new IllegalArgumentException(name+" must be a string");
        String result=value.stringValue();
        if(result.codePoints().anyMatch(c -> Character.isISOControl(c) && !Character.isWhitespace(c))) throw new IllegalArgumentException("Control characters are not allowed");
        return result.replaceAll("[\\p{Z}\\s]+"," ").strip();
    }
}
