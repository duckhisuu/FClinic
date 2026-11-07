package com.fclinic.doctorservice.application.mapper;

import com.fclinic.doctorservice.application.result.DoctorView;
import com.fclinic.doctorservice.application.result.TimeSlotView;
import com.fclinic.doctorservice.domain.aggregate.Doctor;
import com.fclinic.doctorservice.domain.model.TimeSlot;

public final class DoctorApplicationMapper {

    private DoctorApplicationMapper() {
    }

    public static DoctorView toView(Doctor doctor) {
        if (doctor == null) return null;
        return new DoctorView(
                doctor.getId(), doctor.getName(), doctor.getSpecialty(), doctor.getDepartment(),
                doctor.getQualification(), doctor.getExperienceYears(), doctor.getConsultationFee(),
                doctor.getRoomNumber(), doctor.getBio(), doctor.getAvatarUrl(), doctor.isActive()
        );
    }

    public static TimeSlotView toView(TimeSlot slot) {
        if (slot == null) return null;
        return new TimeSlotView(
                slot.getId(), slot.getDoctorId(), slot.getSlotDate(), slot.getStartTime(),
                slot.getEndTime(), slot.isBooked(), slot.isPublished()
        );
    }
}
