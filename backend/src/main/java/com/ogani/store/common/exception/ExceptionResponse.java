package com.ogani.store.common.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ogani.store.common.constants.Constant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Khuôn error response duy nhất: { timestamp, code (HTTP), status, message, details? }. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExceptionResponse {
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Constant.DATE_TIME_PATTERN, timezone = Constant.TIMEZONE_VIETNAM)
    private LocalDateTime timestamp;
    private int code;
    private String status;
    private String message;
    private Object details;
}
