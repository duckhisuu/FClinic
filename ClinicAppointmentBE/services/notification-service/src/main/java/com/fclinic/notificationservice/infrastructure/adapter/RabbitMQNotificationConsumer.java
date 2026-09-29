package com.fclinic.notificationservice.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fclinic.notificationservice.application.command.ProcessNotificationCommand;
import com.fclinic.notificationservice.application.port.in.ProcessNotificationUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class RabbitMQNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQNotificationConsumer.class);

    private final ProcessNotificationUseCase processNotificationUseCase;
    private final ObjectMapper objectMapper;

    public RabbitMQNotificationConsumer(
            ProcessNotificationUseCase processNotificationUseCase,
            ObjectMapper objectMapper
    ) {
        this.processNotificationUseCase = processNotificationUseCase;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${fclinic.rabbitmq.queue:fclinic.appointment.notifications}")
    public void receiveNotification(Object rawMessage) {
        try {
            log.info("[RabbitMQNotificationConsumer] Received message from queue: {}", rawMessage);

            JsonNode node;
            if (rawMessage instanceof String str) {
                node = objectMapper.readTree(str);
            } else {
                node = objectMapper.valueToTree(rawMessage);
            }

            String eventType = node.has("eventType") ? node.get("eventType").asText() : "APPOINTMENT_NOTIFICATION";
            String bookingCode = node.has("bookingCode") ? node.get("bookingCode").asText() : "FC-UNKNOWN";
            String patientName = node.has("patientName") ? node.get("patientName").asText() : "Bệnh nhân";
            String patientPhone = node.has("patientPhone") ? node.get("patientPhone").asText() : "";
            String doctorName = node.has("doctorName") ? node.get("doctorName").asText() : "";
            String apptDate = node.has("appointmentDate") ? node.get("appointmentDate").asText() : "";
            String startTime = node.has("startTime") ? node.get("startTime").asText() : "";
            String apptTime = (!apptDate.isEmpty() && !startTime.isEmpty()) ? (apptDate + " " + startTime) : "Thời gian đã định";

            String message;
            if ("APPOINTMENT_CANCELLED".equalsIgnoreCase(eventType)) {
                String reason = node.has("reason") ? node.get("reason").asText() : "Yêu cầu của bệnh nhân";
                message = String.format("FClinic: Cuộc hẹn %s của quý khách đã được hủy thành công. Lý do: %s.", bookingCode, reason);
            } else {
                message = String.format("FClinic: Quý khách %s đã đặt lịch khám thành công với %s vào lúc %s. Mã đặt hẹn: %s.",
                        patientName, doctorName, apptTime, bookingCode);
            }

            ProcessNotificationCommand command = new ProcessNotificationCommand(
                    bookingCode,
                    patientName,
                    patientPhone,
                    doctorName,
                    apptTime,
                    "SMS/ZALO",
                    message
            );

            processNotificationUseCase.processNotification(command);
        } catch (Exception e) {
            log.error("[RabbitMQNotificationConsumer] Failed to parse and process notification: {}", e.getMessage(), e);
        }
    }
}
