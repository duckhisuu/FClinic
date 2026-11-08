package com.fclinic.authservice.domain.exception;

import com.fclinic.common.web.error.ErrorCode;

public class InvalidTokenException extends AuthException {

    public InvalidTokenException(String message) {
        super(401, ErrorCode.UNAUTHORIZED, message);
    }
}

