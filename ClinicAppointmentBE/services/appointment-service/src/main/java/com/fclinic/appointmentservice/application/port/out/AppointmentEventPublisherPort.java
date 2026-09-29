package com.fclinic.appointmentservice.application.port.out;

import com.fclinic.common.events.DomainEvent;

public interface AppointmentEventPublisherPort {
    void publish(DomainEvent event);
}
