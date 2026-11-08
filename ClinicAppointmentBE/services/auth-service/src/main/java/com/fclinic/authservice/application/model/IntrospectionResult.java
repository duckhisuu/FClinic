package com.fclinic.authservice.application.model;

import java.util.List;
import java.util.UUID;

public record IntrospectionResult(
        boolean active,
        String contractVersion,
        String iss,
        List<String> aud,
        Long exp,
        String sub,
        String principalType,
        UUID userId,
        UUID patientId,
        UUID doctorId,
        List<String> authorities
) {
    public static final String CONTRACT_VERSION = "patient-identity-introspection/2";

    public static IntrospectionResult inactive() {
        return new IntrospectionResult(false, null, null, null, null, null, null, null, null, null, null);
    }

    public static IntrospectionResult activeUser(
            String iss,
            List<String> aud,
            long exp,
            String sub,
            UUID userId,
            UUID patientId,
            UUID doctorId,
            List<String> authorities
    ) {
        return new IntrospectionResult(
                true, CONTRACT_VERSION, iss, aud, exp, sub,
                "USER", userId, patientId, doctorId, authorities
        );
    }

    public static IntrospectionResult activeService(
            String iss,
            List<String> aud,
            long exp,
            String sub,
            List<String> authorities
    ) {
        return new IntrospectionResult(
                true, CONTRACT_VERSION, iss, aud, exp, sub,
                "SERVICE", null, null, null, authorities
        );
    }
}

