package com.fclinic.notificationservice.application.port.in;

import com.fclinic.notificationservice.application.dto.event.AppointmentNotificationEvent;

public interface IngestAppointmentEventUseCase {

    /** Validates the event and creates durable notification jobs; duplicates are ignored. */
    void ingest(AppointmentNotificationEvent event);
}
