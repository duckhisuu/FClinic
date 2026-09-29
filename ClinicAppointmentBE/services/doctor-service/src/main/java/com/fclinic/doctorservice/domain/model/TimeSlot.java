package com.fclinic.doctorservice.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;

public class TimeSlot {

    private Long id;
    private Long doctorId;
    private LocalDate slotDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private boolean isBooked;

    public TimeSlot(Long id, Long doctorId, LocalDate slotDate, LocalTime startTime, LocalTime endTime, boolean isBooked) {
        this.id = id;
        this.doctorId = doctorId;
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.isBooked = isBooked;
    }

    public static TimeSlot createNew(Long doctorId, LocalDate slotDate, LocalTime startTime, LocalTime endTime) {
        return new TimeSlot(null, doctorId, slotDate, startTime, endTime, false);
    }

    public void markBooked(boolean booked) {
        this.isBooked = booked;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDoctorId() { return doctorId; }
    public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }
    public LocalDate getSlotDate() { return slotDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public boolean isBooked() { return isBooked; }
}
