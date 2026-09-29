package com.fclinic.doctorservice.application.port.in;

import com.fclinic.doctorservice.application.result.TimeSlotView;
import java.time.LocalDate;
import java.util.List;

public interface ListTimeSlotsUseCase {
    List<TimeSlotView> listSlots(Long doctorId, LocalDate date);
}
