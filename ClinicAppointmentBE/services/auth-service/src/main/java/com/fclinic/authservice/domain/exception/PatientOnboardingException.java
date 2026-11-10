package com.fclinic.authservice.domain.exception;

import com.fclinic.common.web.error.ErrorCode;

public class PatientOnboardingException extends AuthException {
    public PatientOnboardingException(int status, String message) {
        super(status, status == 409 ? ErrorCode.CONFLICT : ErrorCode.DEPENDENCY_UNAVAILABLE, message);
    }
}
