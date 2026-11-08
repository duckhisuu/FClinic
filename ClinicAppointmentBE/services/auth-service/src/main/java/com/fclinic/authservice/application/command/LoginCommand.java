package com.fclinic.authservice.application.command;

public record LoginCommand(
        String username,
        String password
) {
    @Override
    public String toString() {
        return "LoginCommand[username=" + username + "]";
    }
}

