package com.fclinic.appointmentservice.application.result;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public record DoctorReference(long id, String name, String specialty, BigDecimal fee) {}
