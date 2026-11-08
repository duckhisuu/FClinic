package com.fclinic.authservice.application.service;

import com.fclinic.authservice.application.command.StaffCreatePatientCommand;
import com.fclinic.authservice.application.port.in.StaffUseCase;
import com.fclinic.authservice.application.port.out.UserRepositoryPort;
import com.fclinic.authservice.domain.aggregate.User;
import com.fclinic.authservice.domain.exception.AuthValidationException;
import com.fclinic.authservice.domain.exception.UserNotFoundException;
import com.fclinic.authservice.domain.util.UuidV4;
import com.fclinic.authservice.domain.valueobject.RoleName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class StaffPatientAccountService implements StaffUseCase {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepositoryPort userRepository;
    private final UserProvisioningSupport provisioningSupport;

    public StaffPatientAccountService(
            UserRepositoryPort userRepository,
            UserProvisioningSupport provisioningSupport
    ) {
        this.userRepository = userRepository;
        this.provisioningSupport = provisioningSupport;
    }

    @Override
    @Transactional(readOnly = true)
    public User lookupPatient(String query) {
        if (query == null || query.isBlank()) {
            throw new AuthValidationException("Từ khóa tra cứu bệnh nhân không được để trống");
        }
        return userRepository.lookupPatient(query.trim())
                .orElseThrow(() -> new UserNotFoundException("Không tìm thấy tài khoản bệnh nhân phù hợp: " + query.trim()));
    }

    @Override
    @Transactional
    public User createPatientOnSite(StaffCreatePatientCommand command) {
        String phoneDigits = command.phoneNumber().replaceAll("[^0-9]", "");
        String baseUsername = phoneDigits.isEmpty()
                ? "pat_" + UuidV4.generate().toString().substring(0, 8)
                : "pat_" + phoneDigits;
        String username = userRepository.existsByUsername(baseUsername)
                ? baseUsername + "_" + UuidV4.generate().toString().substring(0, 4)
                : baseUsername;

        String password = (command.initialPassword() != null && !command.initialPassword().isBlank())
                ? command.initialPassword()
                : generateTemporaryPassword();

        return provisioningSupport.provisionNewUser(
                username,
                password,
                command.fullName(),
                command.email(),
                command.phoneNumber(),
                RoleName.ROLE_PATIENT,
                command.patientId(),
                null,
                null
        );
    }

    private String generateTemporaryPassword() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return "Fc@" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

