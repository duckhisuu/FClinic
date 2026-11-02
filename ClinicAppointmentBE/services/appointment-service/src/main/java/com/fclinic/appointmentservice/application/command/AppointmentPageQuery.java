package com.fclinic.appointmentservice.application.command;

import java.time.*;
import java.util.*;

public record AppointmentPageQuery(
    UUID patientId, UUID doctorId, LocalDate date, String status, int page, int size) {}
