package com.fclinic.appointmentservice.application.result;

import java.time.*;
import java.util.*;

public record ReminderContext(long slotId, LocalDate date, LocalTime start, LocalTime end) {}
