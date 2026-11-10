package com.hcmut.emobility.shared.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

/**
 * Chuyen moi loi thanh mot dang JSON thong nhat: { code, message, details, timestamp }.
 *
 * Thu tu quan trong: cac handler cu the phai dung TRUOC handler bat Exception chung,
 * neu khong moi loi deu thanh 500 - ke ca 404 va 403.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Vi pham business rule: dat cho trung, het suc chua, chuyen trang thai khong hop le... */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiError.of(ex.getCode(), ex.getMessage(), Map.of()));
    }

    /** Invalid request parameters (@NotNull, @Min...) -> 400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> details = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            details.put(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(ApiError.of("VALIDATION_FAILED", "Invalid request parameters", details));
    }

    /** Duong dan khong ton tai -> 404 (truoc day bi bat nham thanh 500) */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("NOT_FOUND", "Route not found: " + ex.getResourcePath(), Map.of()));
    }

    /** Khong du quyen -> 403 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of("ACCESS_DENIED", "You do not have permission to perform this action", Map.of()));
    }

    /**
     * Hai giao dich tranh cung mot dong (khoa lac quan @Version, khoa bi giu, timeout khoa)
     * -> 409 de nguoi dung thu lai, thay vi 500. Vi du: M2 dat xe dung luc M6 cap nhat pin.
     */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ApiError> handleConcurrency(ConcurrencyFailureException ex) {
        log.warn("Tranh chap dong thoi: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("RESOURCE_CONFLICT",
                        "This resource was changed by another request. Please try again.", Map.of()));
    }

    /** Moi loi con lai -> 500, co ghi log day du de con debug */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex) {
        log.error("Loi khong xu ly duoc: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR", "An internal error occurred. Check the server logs.", Map.of()));
    }
}