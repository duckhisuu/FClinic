package com.fclinic.authservice.infrastructure.adapter;

import com.fclinic.authservice.application.port.out.RoleRepositoryPort;
import com.fclinic.authservice.domain.valueobject.Role;
import com.fclinic.authservice.domain.valueobject.RoleName;
import com.fclinic.authservice.infrastructure.mapper.AuthPersistenceMapper;
import com.fclinic.authservice.infrastructure.persistence.SpringDataJpaRoleRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaRoleRepositoryAdapter implements RoleRepositoryPort {

    private final SpringDataJpaRoleRepository jpaRepository;
    private final AuthPersistenceMapper mapper;

    public JpaRoleRepositoryAdapter(SpringDataJpaRoleRepository jpaRepository, AuthPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Role> findByName(RoleName roleName) {
        return jpaRepository.findByRoleName(roleName.name()).map(mapper::toDomain);
    }

    @Override
    public List<Role> findAll() {
        return jpaRepository.findAll().stream().map(mapper::toDomain).toList();
    }
}

