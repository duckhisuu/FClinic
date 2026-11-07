package com.fclinic.doctorservice.application.port.in;

import com.fclinic.doctorservice.application.result.TimeSlotView;

import java.util.Optional;
import java.util.UUID;

public interface GetPublishedSlotUseCase {
    Optional<TimeSlotView> getPublishedSlot(UUID doctorId, UUID slotId);
}
