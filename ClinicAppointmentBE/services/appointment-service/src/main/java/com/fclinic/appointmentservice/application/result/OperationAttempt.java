package com.fclinic.appointmentservice.application.result;

import java.time.*;
import java.util.*;

public record OperationAttempt(String subject, String operation, UUID key, String requestHash) {}
