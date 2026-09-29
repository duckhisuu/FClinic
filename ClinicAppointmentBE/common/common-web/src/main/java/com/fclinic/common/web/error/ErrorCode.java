package com.fclinic.common.web.error;

public enum ErrorCode {
    VALIDATION_ERROR(400, "Dữ liệu yêu cầu không hợp lệ"),
    BAD_REQUEST(400, "Yêu cầu không hợp lệ"),
    UNAUTHORIZED(401, "Yêu cầu xác thực tài khoản"),
    FORBIDDEN(403, "Không có quyền truy cập"),
    NOT_FOUND(404, "Không tìm thấy tài nguyên yêu cầu"),
    CONFLICT(409, "Xảy ra xung đột dữ liệu"),
    INTERNAL_SERVER_ERROR(500, "Hệ thống gặp sự cố nội bộ");

    private final int defaultStatus;
    private final String defaultMessage;

    ErrorCode(int defaultStatus, String defaultMessage) {
        this.defaultStatus = defaultStatus;
        this.defaultMessage = defaultMessage;
    }

    public int defaultStatus() {
        return defaultStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
