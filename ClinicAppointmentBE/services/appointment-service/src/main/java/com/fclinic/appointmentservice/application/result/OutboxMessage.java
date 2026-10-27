package com.fclinic.appointmentservice.application.result;

import java.time.*;
import java.util.*;

public record OutboxMessage(
    UUID id,
    String eventType,
    long appointmentId,
    long aggregateVersion,
    String intent,
    String payload,
    UUID claimToken,
    ReminderContext reminder) {}
