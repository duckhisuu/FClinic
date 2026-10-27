package com.fclinic.appointmentservice.infrastructure.adapter;

import com.fclinic.appointmentservice.application.port.out.NotificationBrokerPort;
import com.fclinic.appointmentservice.application.result.OutboxMessage;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitNotificationPublisherAdapter implements NotificationBrokerPort {
  private final RabbitTemplate rabbit;

  public RabbitNotificationPublisherAdapter(RabbitTemplate rabbit) {
    this.rabbit = rabbit;
  }

  public void publish(OutboxMessage row, String payload) throws Exception {
    MessageProperties props = new MessageProperties();
    props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    props.setContentEncoding("UTF-8");
    props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
    props.setMessageId(row.id().toString());
    String key =
        switch (row.eventType()) {
          case "APPOINTMENT_CREATED" -> "appointment.created";
          case "APPOINTMENT_CANCELLED" -> "appointment.cancelled";
          case "APPOINTMENT_RESCHEDULED" -> "appointment.rescheduled";
          case "APPOINTMENT_REMINDER" -> "appointment.reminder";
          default -> throw new IllegalArgumentException("Unknown event type");
        };
    CorrelationData correlation = new CorrelationData(row.id().toString());
    rabbit.send(
        "clinic.events",
        key,
        new Message(payload.getBytes(StandardCharsets.UTF_8), props),
        correlation);
    var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
    if (!confirm.isAck() || correlation.getReturned() != null)
      throw new IllegalStateException("Broker did not confirm routing");
  }
}
