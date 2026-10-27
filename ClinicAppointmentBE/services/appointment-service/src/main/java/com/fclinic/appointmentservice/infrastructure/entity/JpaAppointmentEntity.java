package com.fclinic.appointmentservice.infrastructure.entity;

import com.fclinic.appointmentservice.domain.model.AppointmentStatus;
import com.fclinic.common.web.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
public class JpaAppointmentEntity extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "booking_code", nullable = false, unique = true, length = 40)
  private String bookingCode;

  @Column(name = "patient_id", nullable = false)
  private UUID patientId;

  @Column(name = "patient_name", nullable = false)
  private String patientName;

  @Column(name = "patient_phone", nullable = false, length = 16)
  private String patientPhone;

  @Column(name = "doctor_id", nullable = false)
  private Long doctorId;

  @Column(name = "doctor_name", nullable = false)
  private String doctorName;

  @Column(name = "doctor_specialty", nullable = false)
  private String doctorSpecialty;

  @Column(name = "slot_id", nullable = false)
  private Long slotId;

  @Column(name = "appointment_date", nullable = false)
  private LocalDate appointmentDate;

  @Column(name = "start_time", nullable = false)
  private LocalTime startTime;

  @Column(name = "end_time", nullable = false)
  private LocalTime endTime;

  @Column(name = "reason_for_visit", columnDefinition = "text")
  private String reasonForVisit;

  @Column(name = "consultation_fee", nullable = false, columnDefinition = "numeric")
  private BigDecimal consultationFee;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AppointmentStatus status;

  @Version
  @Column(nullable = false)
  private long version;
}
