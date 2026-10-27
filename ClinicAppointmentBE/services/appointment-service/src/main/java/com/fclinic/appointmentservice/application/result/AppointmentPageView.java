package com.fclinic.appointmentservice.application.result;

import java.time.*;
import java.util.*;

public record AppointmentPageView(
    List<AppointmentView> content, int page, int size, long totalElements, long totalPages) {}
