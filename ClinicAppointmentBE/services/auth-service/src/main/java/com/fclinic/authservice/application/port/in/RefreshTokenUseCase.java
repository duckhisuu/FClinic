package com.fclinic.authservice.application.port.in;

import com.fclinic.authservice.application.model.AuthResult;

public interface RefreshTokenUseCase {

    AuthResult refresh(String refreshToken);
}

