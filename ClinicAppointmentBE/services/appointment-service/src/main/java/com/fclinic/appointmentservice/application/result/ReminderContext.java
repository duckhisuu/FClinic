package com.fclinic.appointmentservice.application.result;

import java.time.*;
import java.util.*;

public record ReminderContext(UUID slotId, LocalDate date, LocalTime start, LocalTime end) {}
