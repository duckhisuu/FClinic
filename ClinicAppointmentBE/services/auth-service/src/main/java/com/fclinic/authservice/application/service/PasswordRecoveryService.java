package com.fclinic.authservice.application.service;

import com.fclinic.authservice.application.model.ForgotPasswordResult;
import com.fclinic.authservice.application.model.PasswordRecoveryPolicy;
import com.fclinic.authservice.application.port.in.PasswordRecoveryUseCase;
import com.fclinic.authservice.application.port.out.KeycloakIdentityPort;
import com.fclinic.authservice.application.port.out.OtpDeliveryPort;
import com.fclinic.authservice.application.port.out.PasswordResetOtpRepositoryPort;
import com.fclinic.authservice.application.port.out.UserRepositoryPort;
import com.fclinic.authservice.domain.aggregate.User;
import com.fclinic.authservice.domain.exception.AccountDisabledException;
import com.fclinic.authservice.domain.exception.InvalidOtpException;
import com.fclinic.authservice.domain.exception.UserNotFoundException;
import com.fclinic.authservice.domain.model.PasswordResetOtp;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;

@Service
public class PasswordRecoveryService implements PasswordRecoveryUseCase {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepositoryPort userRepository;
    private final PasswordResetOtpRepositoryPort otpRepository;
    private final OtpDeliveryPort otpDeliveryPort;
    private final KeycloakIdentityPort keycloakIdentityPort;
    private final PasswordRecoveryPolicy policy;
    private final Clock clock;

    public PasswordRecoveryService(
            UserRepositoryPort userRepository,
            PasswordResetOtpRepositoryPort otpRepository,
            OtpDeliveryPort otpDeliveryPort,
            KeycloakIdentityPort keycloakIdentityPort,
            PasswordRecoveryPolicy policy,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.otpDeliveryPort = otpDeliveryPort;
        this.keycloakIdentityPort = keycloakIdentityPort;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ForgotPasswordResult forgotPassword(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UserNotFoundException("Không tìm thấy tài khoản với email: " + normalizedEmail));

        if (!user.isActive()) {
            throw new AccountDisabledException("Tài khoản đã bị vô hiệu hóa, không thể khôi phục mật khẩu");
        }

        String otpCode = String.format("%06d", RANDOM.nextInt(1_000_000));
        Instant now = clock.instant();
        PasswordResetOtp otp = PasswordResetOtp.issue(user.getId(), normalizedEmail, otpCode, now, policy.expirationSeconds());
        otpRepository.save(otp);

        otpDeliveryPort.sendPasswordResetOtp(normalizedEmail, user.getFullName(), otpCode, policy.expirationSeconds());

        return new ForgotPasswordResult(
                normalizedEmail,
                "Mã OTP khôi phục mật khẩu đã được tạo và gửi tới email đăng ký",
                policy.exposeOtpInResponse() ? otpCode : null,
                policy.expirationSeconds()
        );
    }

    @Override
    @Transactional
    public void resetPassword(String email, String otpCode, String newPassword) {
        String normalizedEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UserNotFoundException("Không tìm thấy tài khoản với email: " + normalizedEmail));

        PasswordResetOtp storedOtp = otpRepository.findLatestByEmail(normalizedEmail)
                .orElseThrow(() -> new InvalidOtpException("Không tìm thấy yêu cầu OTP cho email này"));

        Instant now = clock.instant();
        if (!storedOtp.isValidFor(otpCode.trim(), now)) {
            throw new InvalidOtpException("Mã OTP không hợp lệ hoặc đã hết hạn");
        }

        storedOtp.markUsed(now);
        otpRepository.save(storedOtp);

        keycloakIdentityPort.resetPassword(user.getKeycloakId(), newPassword);
        keycloakIdentityPort.revokeAllUserSessions(user.getKeycloakId());
    }
}

