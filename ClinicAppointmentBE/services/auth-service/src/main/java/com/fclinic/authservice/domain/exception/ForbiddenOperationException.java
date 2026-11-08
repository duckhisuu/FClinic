package com.fclinic.authservice.domain.exception;

import com.fclinic.common.web.error.ErrorCode;

public class ForbiddenOperationException extends AuthException {

    public ForbiddenOperationException(String message) {
        super(403, ErrorCode.FORBIDDEN, message);
    }
}

