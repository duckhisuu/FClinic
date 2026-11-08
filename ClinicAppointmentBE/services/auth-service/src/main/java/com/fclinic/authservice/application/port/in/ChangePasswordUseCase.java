package com.fclinic.authservice.application.port.in;

import com.fclinic.authservice.application.command.ChangePasswordCommand;

import java.util.UUID;

public interface ChangePasswordUseCase {

    void changePassword(UUID userId, ChangePasswordCommand command);
}

