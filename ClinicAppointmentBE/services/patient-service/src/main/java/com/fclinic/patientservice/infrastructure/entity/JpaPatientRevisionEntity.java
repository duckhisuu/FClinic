package com.fclinic.patientservice.infrastructure.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="patient_revisions")
@Getter @Setter
public class JpaPatientRevisionEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(name="patient_id", nullable=false)
    private UUID patientId;
    @Column(name="entity_version", nullable=false)
    private long entityVersion;
    @Column(nullable=false, length=10)
    private String action;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="before_data", columnDefinition="jsonb")
    private String beforeData;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="after_data", nullable=false, columnDefinition="jsonb")
    private String afterData;
    @Column(name="actor_id", nullable=false, length=255)
    private String actorId;
    @Column(name="changed_at", nullable=false)
    private Instant changedAt;

}
