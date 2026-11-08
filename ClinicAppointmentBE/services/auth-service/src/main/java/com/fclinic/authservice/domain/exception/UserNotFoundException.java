package com.fclinic.authservice.domain.exception;

import com.fclinic.common.web.error.ErrorCode;

public class UserNotFoundException extends AuthException {

    public UserNotFoundException(String message) {
        super(404, ErrorCode.NOT_FOUND, message);
    }
}

