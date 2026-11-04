package com.ogani.store.common.util;

import com.ogani.store.common.exception.AppException;
import com.ogani.store.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;

public final class PasswordRules {
    private PasswordRules() {}

    public static void requireBcryptLength(String value) {
        if(value==null || value.getBytes(StandardCharsets.UTF_8).length>72) {
            throw AppException.withCustomMessage(ErrorCode.VALIDATION_ERROR,"Passwords must not exceed 72 UTF-8 bytes");
        }
    }
}
