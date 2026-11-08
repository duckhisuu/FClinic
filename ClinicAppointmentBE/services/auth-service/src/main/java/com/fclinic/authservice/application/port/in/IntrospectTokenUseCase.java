package com.fclinic.authservice.application.port.in;

import com.fclinic.authservice.application.model.IntrospectionResult;

public interface IntrospectTokenUseCase {

    IntrospectionResult introspect(String rawToken);
}

