package com.hcmut.emobility.shared.common;

import org.springframework.http.HttpStatus;

/** Nem ra khi vi pham business rule, vi du dat cho trung hoac het suc chua. */
public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, String message) {
        this(code, message, HttpStatus.CONFLICT);
    }

    public BusinessException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
}
