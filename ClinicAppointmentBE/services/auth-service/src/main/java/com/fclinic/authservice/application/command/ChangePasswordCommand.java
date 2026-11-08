package com.fclinic.authservice.application.command;

public record ChangePasswordCommand(
        String oldPassword,
        String newPassword
) {
    @Override
    public String toString() {
        return "ChangePasswordCommand[redacted]";
    }
}

