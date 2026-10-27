package com.fclinic.appointmentservice.application.port.out;

import com.fclinic.appointmentservice.application.command.*;
import com.fclinic.appointmentservice.application.result.*;
import java.time.*;
import java.util.*;

public interface NotificationPayloadPort {
  String resolvePayload(OutboxMessage row);
}
