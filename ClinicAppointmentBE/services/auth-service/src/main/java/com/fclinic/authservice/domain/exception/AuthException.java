package com.fclinic.authservice.domain.exception;

import com.fclinic.common.web.error.ErrorCode;

public abstract class AuthException extends RuntimeException {

    private final int httpStatus;
    private final ErrorCode errorCode;

    protected AuthException(int httpStatus, ErrorCode errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    protected AuthException(int httpStatus, ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

