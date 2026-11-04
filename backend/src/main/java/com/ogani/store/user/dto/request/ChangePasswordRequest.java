package com.ogani.store.user.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public record ChangePasswordRequest(@NotBlank @Size(max=72) String currentPassword,
        @NotBlank @Size(min=6,max=72) String newPassword,@NotBlank @Size(min=6,max=72) String confirmPassword) {
    @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
    public static ChangePasswordRequest fromJson(JsonNode node) {
        if(node==null || !node.isObject() || node.size()!=3) throw new IllegalArgumentException("Supply currentPassword, newPassword and confirmPassword");
        return new ChangePasswordRequest(text(node,"currentPassword"),text(node,"newPassword"),text(node,"confirmPassword"));
    }
    private static String text(JsonNode node,String name) {
        var value=node.get(name);
        if(value==null || !value.isString()) throw new IllegalArgumentException(name+" must be a string");
        return value.stringValue();
    }
    @Override public String toString() { return "ChangePasswordRequest[REDACTED]"; }
}
