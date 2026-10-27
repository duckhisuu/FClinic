package com.fclinic.appointmentservice.application.result;

import java.time.*;
import java.util.*;

public record Recipient(String recipientType, Long userId, String name, String email) {}
