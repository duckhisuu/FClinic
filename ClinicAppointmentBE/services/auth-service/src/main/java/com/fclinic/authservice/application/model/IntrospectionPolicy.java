package com.fclinic.authservice.application.model;

import java.util.List;

public record IntrospectionPolicy(
        String issuer,
        List<String> defaultAudiences
) {
}

