package com.ogani.store.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Nơi duy nhất khai báo lỗi trả về client: message + HTTP status.
 * Message có thể chứa %s để AppException truyền tham số vào (vd "%s not found").
 * Thêm lỗi mới = thêm 1 dòng ở đây.
 */
@Getter
public enum ErrorCode {

    // === 1. Authentication & Authorization ===
    UNAUTHENTICATED("Unauthenticated", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("You do not have permission", HttpStatus.FORBIDDEN),
    INVALID_CREDENTIALS("Email or password is incorrect", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED("Your account is locked", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN("Refresh token is invalid or expired", HttpStatus.UNAUTHORIZED),

    // === 2. Validation Errors ===
    VALIDATION_ERROR("Invalid input data", HttpStatus.BAD_REQUEST),
    INVALID_SALE_PRICE("Sale price must not exceed the regular price", HttpStatus.BAD_REQUEST),

    // === 3. Business Logic Errors ===
    INSUFFICIENT_STOCK("Insufficient stock for product %s", HttpStatus.CONFLICT),
    PRODUCT_UNAVAILABLE("Product is not available for purchase", HttpStatus.CONFLICT),
    CHECKOUT_CONFLICT("Checkout changed or is unavailable; review the cart again", HttpStatus.CONFLICT),

    // === 4. Resource / Data Not Found or Conflicts ===
    RESOURCE_NOT_FOUND("%s not found", HttpStatus.NOT_FOUND),
    RESOURCE_ALREADY_EXISTS("%s already exists", HttpStatus.CONFLICT),
    CONCURRENT_MODIFICATION("Record changed; reload and try again", HttpStatus.CONFLICT),
    METHOD_NOT_ALLOWED("HTTP method is not supported for this endpoint", HttpStatus.METHOD_NOT_ALLOWED),

    // === 5. System & Uncategorized ===
    UNCATEGORIZED_EXCEPTION("Unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String message, HttpStatus httpStatus) {
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int getHttpStatusCode() {
        return httpStatus.value();
    }
}
