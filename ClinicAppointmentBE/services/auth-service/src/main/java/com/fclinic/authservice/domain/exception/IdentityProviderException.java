package com.fclinic.authservice.domain.exception;

import com.fclinic.common.web.error.ErrorCode;

public class IdentityProviderException extends AuthException {

    public IdentityProviderException(String message, Throwable cause) {
        super(503, ErrorCode.INTERNAL_SERVER_ERROR, message, cause);
    }

    public IdentityProviderException(String message) {
        super(503, ErrorCode.INTERNAL_SERVER_ERROR, message);
    }
}

