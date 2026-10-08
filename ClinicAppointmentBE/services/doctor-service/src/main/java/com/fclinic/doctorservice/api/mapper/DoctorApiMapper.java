package com.fclinic.doctorservice.api.mapper;

import com.fclinic.doctorservice.api.dto.*;
import com.fclinic.doctorservice.application.command.CreateDoctorCommand;
import com.fclinic.doctorservice.application.command.CreateTimeSlotCommand;
import com.fclinic.doctorservice.application.result.DoctorScheduleView;
import com.fclinic.doctorservice.application.result.DoctorView;
import com.fclinic.doctorservice.application.result.TimeSlotView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

public final class DoctorApiMapper {

    private DoctorApiMapper() {
    }

    public static CreateDoctorCommand toCommand(CreateDoctorRequest request) {
        return new CreateDoctorCommand(request.getName(), request.getSpecialty(), request.getDepartment(),
                request.getQualification(), request.getExperienceYears(), request.getConsultationFee(),
                request.getRoomNumber(), request.getBio(), request.getAvatarUrl());
    }

    public static CreateTimeSlotCommand toCommand(Long doctorId, CreateTimeSlotRequest request) {
        return new CreateTimeSlotCommand(doctorId, request.getSlotDate(), request.getStartTime(), request.getEndTime());
    }

    public static DoctorResponse toResponse(DoctorView view) {
        return new DoctorResponse(view.id(), view.name(), view.specialty(), view.department(), view.qualification(),
                view.experienceYears(), view.consultationFee(), view.roomNumber(), view.bio(), view.avatarUrl(), view.active());
    }

    public static TimeSlotResponse toResponse(TimeSlotView view) {
        return new TimeSlotResponse(view.id(), view.doctorId(), view.slotDate(), view.startTime(), view.endTime(), view.isBooked());
    }

    public static DoctorScheduleResponse toResponse(DoctorScheduleView view) {
        var slotsByDate = view.slots().stream().collect(Collectors.groupingBy(
                TimeSlotView::slotDate, LinkedHashMap::new, Collectors.toList()));
        List<DoctorScheduleResponse.ScheduleDayResponse> days = slotsByDate.entrySet().stream()
                .map(entry -> new DoctorScheduleResponse.ScheduleDayResponse(
                        entry.getKey(), entry.getValue().stream().map(DoctorApiMapper::toResponse).toList()))
                .toList();
        return new DoctorScheduleResponse(view.doctorId(), view.period().name(), view.fromDate(), view.toDate(), days);
    }
}
