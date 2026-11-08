package com.fclinic.authservice.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ActiveIntrospectionResponse(
        boolean active,
        String contractVersion,
        String iss,
        List<String> aud,
        long exp,
        String sub,
        String principalType,
        UUID userId,
        UUID patientId,
        UUID doctorId,
        List<String> authorities
) {
}

