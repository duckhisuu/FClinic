package com.fclinic.notificationservice.infrastructure.messaging;

import com.fclinic.notificationservice.application.dto.event.AppointmentNotificationEvent;
import com.fclinic.notificationservice.application.port.in.IngestAppointmentEventUseCase;
import com.fclinic.notificationservice.config.RabbitMqConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Thin by design: failures propagate so listener retry and the DLX handle them. No SMTP here. */
@Component
public class AppointmentEventListener {

    private final IngestAppointmentEventUseCase ingestUseCase;

    public AppointmentEventListener(IngestAppointmentEventUseCase ingestUseCase) {
        this.ingestUseCase = ingestUseCase;
    }

    @RabbitListener(queues = RabbitMqConfig.NOTIFICATION_QUEUE)
    public void consume(AppointmentNotificationEvent event) {
        ingestUseCase.ingest(event);
    }
}
