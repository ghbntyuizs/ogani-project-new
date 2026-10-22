package com.ogani.store.common.exception;

import com.ogani.store.common.constants.Constant;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Điểm DUY NHẤT tạo error response. Lỗi ở filter chain (401/403) cũng được
 * SecurityExceptionHandler chuyển về đây.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ---------- Fallback ----------
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionResponse> handleGenericException(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception at {} {}", request.getMethod(), request.getRequestURI(), exception);
        return buildResponse(ErrorCode.UNCATEGORIZED_EXCEPTION, ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage(), null);
    }

    // ---------- Lỗi nghiệp vụ ----------
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ExceptionResponse> handleAppException(AppException exception, HttpServletRequest request) {
        return buildResponse(exception.getErrorCode(), exception.getFormattedMessage(), null);
    }

    @ExceptionHandler(BusinessValidationException.class)
    public ResponseEntity<ExceptionResponse> handleBusinessValidationException(BusinessValidationException ex, HttpServletRequest request) {
        return buildResponse(ex.getErrorCode(), ex.getFormattedMessage(), ex.getDetails());
    }

    // ---------- Validation / request sai ----------
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ExceptionResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, String>> details = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            boolean alreadyAdded = details.stream().anyMatch(d -> d.get("field").equals(fieldError.getField()));
            if (!alreadyAdded) {
                details.add(detail(fieldError.getField(), toIssueCode(fieldError), fieldError.getDefaultMessage()));
            }
        }
        return buildResponse(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(), details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ExceptionResponse> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.VALIDATION_ERROR, "Request body is missing or malformed", null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ExceptionResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(),
                List.of(detail(ex.getName(), "WRONG_FORMAT", "Invalid value for parameter '" + ex.getName() + "'")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ExceptionResponse> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(),
                List.of(detail(ex.getParameterName(), "REQUIRED", "Missing required parameter '" + ex.getParameterName() + "'")));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ExceptionResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getMessage(), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ExceptionResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.RESOURCE_NOT_FOUND, String.format(ErrorCode.RESOURCE_NOT_FOUND.getMessage(), "Endpoint"), null);
    }

    // ---------- Security ----------
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ExceptionResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.INVALID_CREDENTIALS, ErrorCode.INVALID_CREDENTIALS.getMessage(), null);
    }

    /** DisabledException, LockedException, AccountExpiredException... */
    @ExceptionHandler(AccountStatusException.class)
    public ResponseEntity<ExceptionResponse> handleAccountStatus(AccountStatusException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.ACCOUNT_LOCKED, ErrorCode.ACCOUNT_LOCKED.getMessage(), null);
    }

    /** Chưa đăng nhập / token sai. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ExceptionResponse> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.UNAUTHENTICATED, ErrorCode.UNAUTHENTICATED.getMessage(), null);
    }

    /** Thiếu quyền: từ filter chain hoặc từ @PreAuthorize. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ExceptionResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getMessage(), null);
    }

    // ---------- helpers ----------
    private ResponseEntity<ExceptionResponse> buildResponse(ErrorCode errorCode, String message, Object details) {
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ExceptionResponse.builder()
                        .timestamp(LocalDateTime.now(ZoneId.of(Constant.TIMEZONE_VIETNAM)))
                        .code(errorCode.getHttpStatusCode())
                        .status(errorCode.getHttpStatus().getReasonPhrase())
                        .message(message)
                        .details(details)
                        .build());
    }

    private static Map<String, String> detail(String field, String issue, String message) {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("field", field);
        d.put("issue", issue);
        d.put("message", message);
        return d;
    }

    private String toIssueCode(FieldError fieldError) {
        String[] codes = fieldError.getCodes();
        String code = codes != null && codes.length > 0 ? codes[codes.length - 1] : "";
        return switch (code) {
            case "NotBlank", "NotNull", "NotEmpty" -> "REQUIRED";
            case "Pattern", "Email" -> "WRONG_FORMAT";
            default -> "INVALID_VALUE";
        };
    }
}
