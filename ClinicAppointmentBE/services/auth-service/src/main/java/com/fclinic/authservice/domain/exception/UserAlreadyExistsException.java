package com.fclinic.authservice.domain.exception;

import com.fclinic.common.web.error.ErrorCode;

public class UserAlreadyExistsException extends AuthException {

    public UserAlreadyExistsException(String message) {
        super(409, ErrorCode.CONFLICT, message);
    }
}

