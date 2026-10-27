package com.fclinic.appointmentservice.infrastructure.adapter;

import com.fclinic.appointmentservice.application.port.in.GenerateAppointmentRemindersUseCase;
import com.fclinic.appointmentservice.application.port.in.PublishOutboxUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxRelayJob {
  private final PublishOutboxUseCase relay;
  private final GenerateAppointmentRemindersUseCase reminders;

  public OutboxRelayJob(PublishOutboxUseCase relay, GenerateAppointmentRemindersUseCase reminders) {
    this.relay = relay;
    this.reminders = reminders;
  }

  @Scheduled(fixedDelayString = "${appointment.outbox-poll-ms:5000}")
  public void relay() {
    relay.publishPendingEvents();
  }

  @Scheduled(fixedDelayString = "${appointment.reminder-poll-ms:30000}")
  public void reminders() {
    reminders.generateDueReminders();
  }
}
