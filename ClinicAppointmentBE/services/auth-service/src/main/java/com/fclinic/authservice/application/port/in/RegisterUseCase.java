package com.fclinic.authservice.application.port.in;

import com.fclinic.authservice.application.command.RegisterCommand;
import com.fclinic.authservice.application.model.AuthResult;

public interface RegisterUseCase {

    AuthResult register(RegisterCommand command);
}

