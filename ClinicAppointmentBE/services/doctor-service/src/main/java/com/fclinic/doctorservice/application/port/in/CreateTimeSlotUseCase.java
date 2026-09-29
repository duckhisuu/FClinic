package com.fclinic.doctorservice.application.port.in;

import com.fclinic.doctorservice.application.command.CreateTimeSlotCommand;
import com.fclinic.doctorservice.application.result.TimeSlotView;

public interface CreateTimeSlotUseCase {
    TimeSlotView createTimeSlot(CreateTimeSlotCommand command);
}
