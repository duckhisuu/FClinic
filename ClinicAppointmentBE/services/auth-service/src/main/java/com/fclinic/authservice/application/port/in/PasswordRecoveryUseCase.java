package com.fclinic.authservice.application.port.in;

import com.fclinic.authservice.application.model.ForgotPasswordResult;

public interface PasswordRecoveryUseCase {

    ForgotPasswordResult forgotPassword(String email);

    void resetPassword(String email, String otp, String newPassword);
}

