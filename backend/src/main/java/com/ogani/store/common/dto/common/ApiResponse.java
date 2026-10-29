package com.ogani.store.common.dto;

/** Khuôn response thành công thống nhất: { success, message, data }. */
public record ApiResponse<T>(boolean success, String message, T data) {
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(true, "OK", data); }
    public static <T> ApiResponse<T> ok(String message, T data) { return new ApiResponse<>(true, message, data); }
}
