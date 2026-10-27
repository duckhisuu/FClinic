package com.fclinic.appointmentservice.application.result;

import java.time.*;
import java.util.*;

public record AvailabilityView(
    long doctorId, LocalDate date, String timezone, List<SlotView> slots) {}
