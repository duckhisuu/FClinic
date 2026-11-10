package com.fclinic.authservice.infrastructure.adapter;

import com.fclinic.authservice.application.port.out.UserRepositoryPort;
import com.fclinic.authservice.domain.aggregate.User;
import com.fclinic.authservice.domain.exception.RoleNotFoundException;
import com.fclinic.authservice.domain.valueobject.RoleName;
import com.fclinic.authservice.infrastructure.entity.JpaRoleEntity;
import com.fclinic.authservice.infrastructure.entity.JpaUserEntity;
import com.fclinic.authservice.infrastructure.mapper.AuthPersistenceMapper;
import com.fclinic.authservice.infrastructure.persistence.SpringDataJpaRoleRepository;
import com.fclinic.authservice.infrastructure.persistence.SpringDataJpaUserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaUserRepositoryAdapter implements UserRepositoryPort {

    private final SpringDataJpaUserRepository userRepository;
    private final SpringDataJpaRoleRepository roleRepository;
    private final AuthPersistenceMapper mapper;

    public JpaUserRepositoryAdapter(
            SpringDataJpaUserRepository userRepository,
            SpringDataJpaRoleRepository roleRepository,
            AuthPersistenceMapper mapper
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.mapper = mapper;
    }

    @Override
    public User save(User user) {
        JpaRoleEntity managedRole = roleRepository.findByRoleName(user.getRoleName().name())
                .orElseThrow(() -> new RoleNotFoundException("Không tìm thấy vai trò trong CSDL: " + user.getRoleName()));
        JpaUserEntity entity = mapper.toEntity(user, managedRole);
        return mapper.toDomain(userRepository.save(entity));
    }

    @Override public List<User> findPatientAccounts(UUID patientId) {
        return userRepository.findTop2ByPatientIdAndActiveTrueAndRole_RoleName(patientId, "ROLE_PATIENT")
                .stream().map(mapper::toDomain).toList();
    }

    @Override public List<User> findDoctorAccounts(UUID doctorId) {
        return userRepository.findTop2ByDoctorIdAndActiveTrueAndRole_RoleName(doctorId, "ROLE_DOCTOR")
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<User> findById(UUID id) {
        return userRepository.findById(id).map(mapper::toDomain);
    }
    @Override
    public Optional<User> lockById(UUID id) {
        return userRepository.lockById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByKeycloakId(UUID keycloakId) {
        return userRepository.findByKeycloakId(keycloakId).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByPhoneNumber(String phoneNumber) {
        return userRepository.findByPhoneNumber(phoneNumber).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByGoogleId(String googleId) {
        return userRepository.findByGoogleId(googleId).map(mapper::toDomain);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsernameIgnoreCase(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmailIgnoreCase(email);
    }

    @Override
    public boolean existsByPhoneNumber(String phoneNumber) {
        return userRepository.existsByPhoneNumber(phoneNumber);
    }

    @Override
    public List<User> search(String keyword, RoleName roleName, Boolean active) {
        Specification<JpaUserEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern),
                        cb.like(cb.lower(root.get("fullName")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("phoneNumber"), "")), pattern)
                ));
            }
            if (roleName != null) {
                predicates.add(cb.equal(root.join("role").get("roleName"), roleName.name()));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return userRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<User> lookupPatient(String query) {
        return userRepository.lookupPatient(query).map(mapper::toDomain);
    }
}
