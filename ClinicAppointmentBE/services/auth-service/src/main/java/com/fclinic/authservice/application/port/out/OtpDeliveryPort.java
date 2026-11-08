package com.fclinic.authservice.application.port.out;

public interface OtpDeliveryPort {

    void sendPasswordResetOtp(String email, String fullName, String otpCode, long expiresInSeconds);
}

