package com.fclinic.authservice.api.dto.response;

public record InactiveIntrospectionResponse(
        boolean active
) {
    public InactiveIntrospectionResponse() {
        this(false);
    }
}

