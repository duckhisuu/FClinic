package com.fclinic.appointmentservice.application.result;

import java.time.*;
import java.util.*;

public record MedicalReferenceView(long id, UUID patientId, long doctorId, String status) {}
