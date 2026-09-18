package com.hcmut.emobility.shared.common;

import java.time.OffsetDateTime;
import java.util.Map;

/** Dang loi thong nhat toan he thong: { code, message, details, timestamp }. */
public record ApiError(String code, String message, Map<String, Object> details, OffsetDateTime timestamp) {
    public static ApiError of(String code, String message, Map<String, Object> details) {
        return new ApiError(code, message, details, OffsetDateTime.now());
    }
}
