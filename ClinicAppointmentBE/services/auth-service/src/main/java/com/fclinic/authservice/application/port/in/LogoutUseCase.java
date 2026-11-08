package com.fclinic.authservice.application.port.in;

import java.util.UUID;

public interface LogoutUseCase {

    void logout(String refreshToken, UUID currentUserId);
}

