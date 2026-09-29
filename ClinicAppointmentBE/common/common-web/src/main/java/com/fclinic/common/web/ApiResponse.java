package com.fclinic.common.web;

import com.fclinic.common.web.error.ErrorCode;

public record ApiResponse<T>(int status, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return success(200, "Thành công", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return success(200, message, data);
    }

    public static <T> ApiResponse<T> success(int status, String message, T data) {
        return new ApiResponse<>(status, message, data);
    }

    public static ApiResponse<Void> failure(String message) {
        return failure(ErrorCode.BAD_REQUEST, message);
    }

    public static ApiResponse<Void> failure(ErrorCode code, String message) {
        return failure(code.defaultStatus(), code, message);
    }

    public static ApiResponse<Void> failure(int status, ErrorCode code, String message) {
        String responseMessage = (message == null || message.isBlank()) ? code.defaultMessage() : message;
        return new ApiResponse<>(status, responseMessage, null);
    }
}
