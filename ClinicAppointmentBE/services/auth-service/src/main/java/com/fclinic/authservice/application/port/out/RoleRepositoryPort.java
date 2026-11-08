package com.fclinic.authservice.application.port.out;

import com.fclinic.authservice.domain.valueobject.Role;
import com.fclinic.authservice.domain.valueobject.RoleName;

import java.util.List;
import java.util.Optional;

public interface RoleRepositoryPort {

    Optional<Role> findByName(RoleName roleName);

    List<Role> findAll();
}

