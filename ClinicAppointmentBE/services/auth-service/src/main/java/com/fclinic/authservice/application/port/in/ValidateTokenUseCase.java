package com.fclinic.authservice.application.port.in;

import com.fclinic.authservice.application.model.TokenValidationResult;

public interface ValidateTokenUseCase {

    TokenValidationResult validate(String rawToken);
}

