package com.fclinic.authservice.application.port.out;

import com.fclinic.authservice.domain.model.PasswordResetOtp;

import java.util.Optional;

public interface PasswordResetOtpRepositoryPort {

    PasswordResetOtp save(PasswordResetOtp otp);

    Optional<PasswordResetOtp> findLatestByEmail(String email);
}

