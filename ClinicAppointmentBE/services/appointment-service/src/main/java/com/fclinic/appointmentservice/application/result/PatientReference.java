package com.fclinic.appointmentservice.application.result;

import java.time.*;
import java.util.*;

public record PatientReference(UUID id, String name, String phone) {}
