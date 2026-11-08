package com.fclinic.authservice.domain.exception;

import com.fclinic.common.web.error.ErrorCode;

public class AccountDisabledException extends AuthException {

    public AccountDisabledException(String message) {
        super(403, ErrorCode.FORBIDDEN, message);
    }
}

