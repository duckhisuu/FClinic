package com.fclinic.authservice.application.port.in;

import com.fclinic.authservice.application.command.GoogleLoginCommand;
import com.fclinic.authservice.application.model.AuthResult;

public interface GoogleLoginUseCase {

    AuthResult loginWithGoogle(GoogleLoginCommand command);
}

