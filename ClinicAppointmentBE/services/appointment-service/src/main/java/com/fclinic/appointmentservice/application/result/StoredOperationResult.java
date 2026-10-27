package com.fclinic.appointmentservice.application.result;

import java.time.*;
import java.util.*;

public record StoredOperationResult(String requestHash, AppointmentOperationResult result) {}
