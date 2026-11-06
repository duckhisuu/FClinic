package com.fclinic.appointmentservice.application.result;

import java.time.*;
import java.util.*;

public record Recipient(String recipientType, UUID userId, String name, String email) {}
