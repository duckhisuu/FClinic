package com.fclinic.appointmentservice.application.result;

import java.time.*;
import java.util.*;

public record MedicalReferenceView(UUID id, UUID patientId, UUID doctorId, String status) {}
