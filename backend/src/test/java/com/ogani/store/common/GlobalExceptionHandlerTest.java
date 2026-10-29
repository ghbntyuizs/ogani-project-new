package com.ogani.store.common;

import com.ogani.store.common.exception.ExceptionResponse;
import com.ogani.store.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Vi phạm ràng buộc DB (race unique slug/sku) -> 409, không lộ chi tiết SQL")
    void dataIntegrityViolation_returns409WithoutSqlDetails() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("ERROR: duplicate key value violates unique constraint \"uq_categories_active_slug\""));

        ResponseEntity<ExceptionResponse> response =
                handler.handleDataIntegrity(ex, new MockHttpServletRequest("POST", "/api/admin/categories"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        ExceptionResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(409, body.getCode());
        assertFalse(body.getMessage().contains("duplicate key"));
        assertFalse(body.getMessage().contains("uq_categories_active_slug"));
    }
}
