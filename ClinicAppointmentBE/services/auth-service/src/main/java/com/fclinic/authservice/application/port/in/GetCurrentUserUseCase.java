package com.fclinic.authservice.application.port.in;

import com.fclinic.authservice.domain.aggregate.User;

import java.util.UUID;

public interface GetCurrentUserUseCase {

    User getCurrentUser(UUID userId);
}

