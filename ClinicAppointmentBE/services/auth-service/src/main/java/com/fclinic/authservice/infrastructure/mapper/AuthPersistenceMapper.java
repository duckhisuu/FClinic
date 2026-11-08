package com.fclinic.authservice.infrastructure.mapper;

import com.fclinic.authservice.domain.aggregate.User;
import com.fclinic.authservice.domain.model.PasswordResetOtp;
import com.fclinic.authservice.domain.valueobject.Role;
import com.fclinic.authservice.domain.valueobject.RoleName;
import com.fclinic.authservice.infrastructure.entity.JpaPasswordResetOtpEntity;
import com.fclinic.authservice.infrastructure.entity.JpaRoleEntity;
import com.fclinic.authservice.infrastructure.entity.JpaUserEntity;
import org.springframework.stereotype.Component;

@Component
public class AuthPersistenceMapper {

    public Role toDomain(JpaRoleEntity entity) {
        return new Role(
                entity.getId(),
                RoleName.fromString(entity.getRoleName()),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public User toDomain(JpaUserEntity entity) {
        return new User(
                entity.getId(),
                entity.getKeycloakId(),
                entity.getUsername(),
                entity.getEmail(),
                entity.getPhoneNumber(),
                entity.getFullName(),
                toDomain(entity.getRole()),
                entity.getPatientId(),
                entity.getDoctorId(),
                entity.getGoogleId(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public JpaUserEntity toEntity(User domain, JpaRoleEntity managedRole) {
        JpaUserEntity entity = new JpaUserEntity();
        entity.setId(domain.getId());
        entity.setRole(managedRole);
        entity.setKeycloakId(domain.getKeycloakId());
        entity.setUsername(domain.getUsername());
        entity.setEmail(domain.getEmail());
        entity.setPhoneNumber(domain.getPhoneNumber());
        entity.setFullName(domain.getFullName());
        entity.setPatientId(domain.getPatientId());
        entity.setDoctorId(domain.getDoctorId());
        entity.setGoogleId(domain.getGoogleId());
        entity.setActive(domain.isActive());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }

    public PasswordResetOtp toDomain(JpaPasswordResetOtpEntity entity) {
        return new PasswordResetOtp(
                entity.getId(),
                entity.getUserId(),
                entity.getEmail(),
                entity.getOtpCode(),
                entity.getExpiresAt(),
                entity.isUsed(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public JpaPasswordResetOtpEntity toEntity(PasswordResetOtp domain) {
        JpaPasswordResetOtpEntity entity = new JpaPasswordResetOtpEntity();
        entity.setId(domain.getId());
        entity.setUserId(domain.getUserId());
        entity.setEmail(domain.getEmail());
        entity.setOtpCode(domain.getOtpCode());
        entity.setExpiresAt(domain.getExpiresAt());
        entity.setUsed(domain.isUsed());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }
}

