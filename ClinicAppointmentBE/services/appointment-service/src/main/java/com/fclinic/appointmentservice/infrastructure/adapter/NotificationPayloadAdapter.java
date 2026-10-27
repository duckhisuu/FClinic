package com.fclinic.appointmentservice.infrastructure.adapter;

import static com.fclinic.appointmentservice.infrastructure.AppointmentJson.*;

import com.fclinic.appointmentservice.application.port.out.*;
import com.fclinic.appointmentservice.application.result.*;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class NotificationPayloadAdapter implements NotificationPayloadPort {
  private final RecipientLookupPort recipients;

  public NotificationPayloadAdapter(RecipientLookupPort recipients) {
    this.recipients = recipients;
  }

  public String resolvePayload(OutboxMessage row) {
    var n = read(row.intent()).deepCopy();
    var resolved =
        recipients.recipients(
            UUID.fromString(n.path("patientId").asText()), n.path("doctorId").longValue());
    ((tools.jackson.databind.node.ObjectNode) n).set("recipients", JSON.valueToTree(resolved));
    return write(n);
  }
}
