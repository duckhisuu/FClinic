package com.fclinic.notificationservice.infrastructure.persistence;

import com.fclinic.notificationservice.infrastructure.entity.JpaProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataJpaProcessedEventRepository extends JpaRepository<JpaProcessedEventEntity, UUID> {
}
