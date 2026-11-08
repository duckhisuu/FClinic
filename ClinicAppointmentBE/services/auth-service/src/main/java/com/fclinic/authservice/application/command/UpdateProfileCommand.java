package com.fclinic.authservice.application.command;

public record UpdateProfileCommand(
        String fullName,
        String email,
        String phoneNumber
) {
}

