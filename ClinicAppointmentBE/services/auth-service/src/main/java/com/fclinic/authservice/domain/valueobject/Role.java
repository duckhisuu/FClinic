package com.fclinic.authservice.domain.valueobject;

import com.fclinic.authservice.domain.util.UuidV4;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Role {

    private final UUID id;
    private final RoleName name;
    private final String description;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Role(UUID id, RoleName name, String description, Instant createdAt, Instant updatedAt) {
        this.id = UuidV4.requireValid(id, "roleId");
        this.name = Objects.requireNonNull(name, "roleName must not be null");
        this.description = description == null ? "" : description.trim();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public RoleName getName() {
        return name;
    }

    public String getRoleName() {
        return name.name();
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

