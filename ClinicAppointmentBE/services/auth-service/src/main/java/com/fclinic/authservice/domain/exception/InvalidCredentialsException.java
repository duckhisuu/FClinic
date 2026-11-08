package com.fclinic.authservice.domain.exception;

import com.fclinic.common.web.error.ErrorCode;

public class InvalidCredentialsException extends AuthException {

    public InvalidCredentialsException(String message) {
        super(401, ErrorCode.UNAUTHORIZED, message);
    }
}

