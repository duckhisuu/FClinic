package com.fclinic.doctorservice.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public class TimeSlot {

    private UUID id;
    private UUID doctorId;
    private LocalDate slotDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private boolean isBooked;
    private boolean published;

    public TimeSlot(UUID id, UUID doctorId, LocalDate slotDate, LocalTime startTime, LocalTime endTime, boolean isBooked, boolean published) {
        this.id = id != null ? id : UUID.randomUUID();
        this.doctorId = doctorId;
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.isBooked = isBooked;
        this.published = published;
    }

    public TimeSlot(UUID id, UUID doctorId, LocalDate slotDate, LocalTime startTime, LocalTime endTime, boolean isBooked) {
        this(id, doctorId, slotDate, startTime, endTime, isBooked, true);
    }

    public static TimeSlot createNew(UUID doctorId, LocalDate slotDate, LocalTime startTime, LocalTime endTime) {
        return new TimeSlot(UUID.randomUUID(), doctorId, slotDate, startTime, endTime, false, true);
    }

    public void markBooked(boolean booked) {
        this.isBooked = booked;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getDoctorId() { return doctorId; }
    public void setDoctorId(UUID doctorId) { this.doctorId = doctorId; }
    public LocalDate getSlotDate() { return slotDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public boolean isBooked() { return isBooked; }
    public boolean isPublished() { return published; }
}
