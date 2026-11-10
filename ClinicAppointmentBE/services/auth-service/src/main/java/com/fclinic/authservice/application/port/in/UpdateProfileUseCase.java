package com.fclinic.authservice.application.port.in;

import com.fclinic.authservice.application.command.UpdateProfileCommand;
import com.fclinic.authservice.domain.aggregate.User;

import java.util.UUID;

public interface UpdateProfileUseCase {

    User updateProfile(UUID userId, UpdateProfileCommand command);

    User linkPatientProfile(UUID userId, UUID patientId, String authorization);
}

