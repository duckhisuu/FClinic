package com.fclinic.authservice.domain.exception;

import com.fclinic.common.web.error.ErrorCode;

public class RoleNotFoundException extends AuthException {

    public RoleNotFoundException(String message) {
        super(404, ErrorCode.NOT_FOUND, message);
    }
}

