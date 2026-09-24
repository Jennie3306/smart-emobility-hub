package com.hcmut.emobility.shared.common;

import java.time.OffsetDateTime;

/** 
 * Dang JSON thong nhat cho moi luong tra ve thanh cong: { code, message, data, timestamp }. 
 */

public record ApiResponse<T>(String code, String message, T data, OffsetDateTime timestamp) {
    
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", "Thao tac thanh cong", data, OffsetDateTime.now());
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>("SUCCESS", message, data, OffsetDateTime.now());
    }
}