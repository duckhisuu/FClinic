package com.fclinic.authservice.infrastructure.adapter;

import com.fclinic.authservice.application.port.out.PasswordResetOtpRepositoryPort;
import com.fclinic.authservice.domain.model.PasswordResetOtp;
import com.fclinic.authservice.infrastructure.mapper.AuthPersistenceMapper;
import com.fclinic.authservice.infrastructure.persistence.SpringDataJpaPasswordResetOtpRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaPasswordResetOtpRepositoryAdapter implements PasswordResetOtpRepositoryPort {

    private final SpringDataJpaPasswordResetOtpRepository repository;
    private final AuthPersistenceMapper mapper;

    public JpaPasswordResetOtpRepositoryAdapter(
            SpringDataJpaPasswordResetOtpRepository repository,
            AuthPersistenceMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public PasswordResetOtp save(PasswordResetOtp otp) {
        return mapper.toDomain(repository.save(mapper.toEntity(otp)));
    }

    @Override
    public Optional<PasswordResetOtp> findLatestByEmail(String email) {
        return repository.findTopByEmailIgnoreCaseOrderByCreatedAtDesc(email).map(mapper::toDomain);
    }
}

