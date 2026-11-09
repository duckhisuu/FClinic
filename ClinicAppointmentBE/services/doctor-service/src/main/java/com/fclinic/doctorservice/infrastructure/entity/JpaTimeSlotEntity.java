package com.fclinic.doctorservice.infrastructure.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "doctor_time_slots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JpaTimeSlotEntity {

    @Id
    private UUID id;

    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "is_booked", nullable = false)
    private boolean isBooked;

    @Column(name = "published", nullable = false)
    @Builder.Default
    private boolean published = true;
}
