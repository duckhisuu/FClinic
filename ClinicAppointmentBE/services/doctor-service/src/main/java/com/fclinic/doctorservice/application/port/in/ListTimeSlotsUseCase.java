package com.fclinic.doctorservice.application.port.in;

import com.fclinic.doctorservice.application.result.TimeSlotView;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ListTimeSlotsUseCase {
    List<TimeSlotView> listSlots(UUID doctorId, LocalDate date);
}
