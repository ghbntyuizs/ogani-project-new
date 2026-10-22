package com.ogani.store.common.exception;

import lombok.Getter;

import java.util.Map;

/** Lỗi validate ở tầng service, kèm chi tiết theo field: {"confirmPassword": "Passwords do not match"}. */
@Getter
public class BusinessValidationException extends AppException {
    private final transient Map<String, Object> details;

    public BusinessValidationException(ErrorCode errorCode, Map<String, Object> details) {
        super(errorCode);
        this.details = details;
    }
}
