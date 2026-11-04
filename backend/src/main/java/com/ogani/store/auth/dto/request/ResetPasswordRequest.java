package com.ogani.store.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.*;
import tools.jackson.databind.JsonNode;

public record ResetPasswordRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token,
        @NotBlank @Size(min=6,max=72) String newPassword,@NotBlank @Size(min=6,max=72) String confirmPassword) {
    @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
    public static ResetPasswordRequest fromJson(JsonNode node) {
        if(node==null || !node.isObject() || node.size()!=3) throw new IllegalArgumentException("Supply token, newPassword and confirmPassword");
        return new ResetPasswordRequest(text(node,"token"),text(node,"newPassword"),text(node,"confirmPassword"));
    }
    private static String text(JsonNode node,String name) {
        var value=node.get(name);if(value==null || !value.isString()) throw new IllegalArgumentException(name+" must be a string");return value.stringValue();
    }
    @Override public String toString() { return "ResetPasswordRequest[REDACTED]"; }
}
