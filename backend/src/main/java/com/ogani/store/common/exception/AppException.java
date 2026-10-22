package com.ogani.store.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Exception nghiệp vụ duy nhất của hệ thống. Mọi thông tin lấy từ {@link ErrorCode}. */
@Getter
public class AppException extends RuntimeException {
    private final ErrorCode errorCode;
    private final String formattedMessage;
    private final HttpStatus httpStatus;

    /** Message mặc định của ErrorCode. */
    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.formattedMessage = errorCode.getMessage();
        this.httpStatus = errorCode.getHttpStatus();
    }

    /** Điền tham số vào các %s trong message: new AppException(RESOURCE_NOT_FOUND, "Product"). */
    public AppException(ErrorCode errorCode, Object... args) {
        super(String.format(errorCode.getMessage(), args));
        this.errorCode = errorCode;
        this.formattedMessage = super.getMessage();
        this.httpStatus = errorCode.getHttpStatus();
    }

    /** Message tuỳ ý, không qua String.format (an toàn khi message có ký tự %). */
    public static AppException withCustomMessage(ErrorCode errorCode, String customMessage) {
        return new AppException(errorCode, customMessage, true);
    }

    private AppException(ErrorCode errorCode, String message, boolean custom) {
        super(message);
        this.errorCode = errorCode;
        this.formattedMessage = message;
        this.httpStatus = errorCode.getHttpStatus();
    }
}
