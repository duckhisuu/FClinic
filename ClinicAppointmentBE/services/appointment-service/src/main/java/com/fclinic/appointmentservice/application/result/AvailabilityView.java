package com.fclinic.appointmentservice.application.result;

import java.time.*;
import java.util.*;

public record AvailabilityView(
    UUID doctorId, LocalDate date, String timezone, List<SlotView> slots) {}
