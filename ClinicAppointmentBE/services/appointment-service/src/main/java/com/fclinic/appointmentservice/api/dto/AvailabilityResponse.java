package com.fclinic.appointmentservice.api.dto;

import static com.fclinic.appointmentservice.api.mapper.AppointmentWireFormat.*;

import com.fclinic.appointmentservice.application.result.AvailabilityView;
import java.util.List;

public record AvailabilityResponse(
    long doctorId, String date, String timezone, List<OfferedSlotResponse> slots) {
  public record OfferedSlotResponse(
      long id,
      long doctorId,
      String slotDate,
      String startTime,
      String endTime,
      boolean available) {}

  public static AvailabilityResponse from(AvailabilityView v) {
    return new AvailabilityResponse(
        v.doctorId(),
        com.fclinic.appointmentservice.api.mapper.AppointmentWireFormat.date(v.date()),
        v.timezone(),
        v.slots().stream()
            .map(
                s ->
                    new OfferedSlotResponse(
                        s.id(),
                        s.doctorId(),
                        com.fclinic.appointmentservice.api.mapper.AppointmentWireFormat.date(
                            s.slotDate()),
                        time(s.startTime()),
                        time(s.endTime()),
                        s.available()))
            .toList());
  }
}
