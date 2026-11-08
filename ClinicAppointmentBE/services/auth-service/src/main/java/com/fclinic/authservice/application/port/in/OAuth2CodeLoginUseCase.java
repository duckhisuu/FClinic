package com.fclinic.authservice.application.port.in;

import com.fclinic.authservice.application.command.OAuth2CodeCommand;
import com.fclinic.authservice.application.model.AuthResult;

public interface OAuth2CodeLoginUseCase {

    AuthResult exchangeCode(OAuth2CodeCommand command);
}

