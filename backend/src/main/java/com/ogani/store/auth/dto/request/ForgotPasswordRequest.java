package com.ogani.store.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.*;
import tools.jackson.databind.JsonNode;
import java.util.Locale;

public record ForgotPasswordRequest(@NotBlank @Email @Size(max=150) String email) {
    public ForgotPasswordRequest { if(email!=null) email=email.strip().toLowerCase(Locale.ROOT); }
    @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
    public static ForgotPasswordRequest fromJson(JsonNode node) {
        if(node==null || !node.isObject() || node.size()!=1 || node.get("email")==null || !node.get("email").isString())
            throw new IllegalArgumentException("Supply an email string");
        return new ForgotPasswordRequest(node.get("email").stringValue());
    }
}
