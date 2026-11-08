package com.fclinic.authservice.domain.exception;

import com.fclinic.common.web.error.ErrorCode;

public class AuthValidationException extends AuthException {

    public AuthValidationException(String message) {
        super(400, ErrorCode.VALIDATION_ERROR, message);
    }
}

