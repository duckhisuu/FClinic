package com.fclinic.appointmentservice.infrastructure.persistence;

import com.fclinic.appointmentservice.infrastructure.entity.JpaOutboxEventEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataJpaOutboxEventRepository
    extends JpaRepository<JpaOutboxEventEntity, UUID> {}
