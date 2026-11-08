package com.fclinic.authservice.domain.exception;

import com.fclinic.common.web.error.ErrorCode;

public class InvalidOtpException extends AuthException {

    public InvalidOtpException(String message) {
        super(400, ErrorCode.BAD_REQUEST, message);
    }
}

