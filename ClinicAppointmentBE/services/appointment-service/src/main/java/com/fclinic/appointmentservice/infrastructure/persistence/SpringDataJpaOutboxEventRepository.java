package com.fclinic.appointmentservice.infrastructure.persistence;

import com.fclinic.appointmentservice.infrastructure.entity.JpaOutboxEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataJpaOutboxEventRepository extends JpaRepository<JpaOutboxEventEntity, UUID> {

    @Query("SELECT e FROM JpaOutboxEventEntity e WHERE e.status = 'PENDING' ORDER BY e.createdAt ASC")
    List<JpaOutboxEventEntity> findPending(Pageable pageable);

    default List<JpaOutboxEventEntity> findPendingForUpdate(int limit) {
        return findPending(Pageable.ofSize(limit));
    }

    List<JpaOutboxEventEntity> findTop50ByOrderByCreatedAtDesc();
}
