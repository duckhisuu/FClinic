package com.fclinic.authservice.api.mapper;

import com.fclinic.authservice.api.dto.request.ChangePasswordRequest;
import com.fclinic.authservice.api.dto.request.CreateUserRequest;
import com.fclinic.authservice.api.dto.request.GoogleLoginRequest;
import com.fclinic.authservice.api.dto.request.LoginRequest;
import com.fclinic.authservice.api.dto.request.OAuth2TokenRequest;
import com.fclinic.authservice.api.dto.request.RegisterRequest;
import com.fclinic.authservice.api.dto.request.StaffCreatePatientRequest;
import com.fclinic.authservice.api.dto.request.UpdateProfileRequest;
import com.fclinic.authservice.api.dto.request.UpdateUserRequest;
import com.fclinic.authservice.api.dto.response.ActiveIntrospectionResponse;
import com.fclinic.authservice.api.dto.response.AuthResponse;
import com.fclinic.authservice.api.dto.response.ForgotPasswordResponse;
import com.fclinic.authservice.api.dto.response.InactiveIntrospectionResponse;
import com.fclinic.authservice.api.dto.response.RoleResponse;
import com.fclinic.authservice.api.dto.response.TokenValidationResponse;
import com.fclinic.authservice.api.dto.response.UserResponse;
import com.fclinic.authservice.application.command.ChangePasswordCommand;
import com.fclinic.authservice.application.command.CreateUserCommand;
import com.fclinic.authservice.application.command.GoogleLoginCommand;
import com.fclinic.authservice.application.command.LoginCommand;
import com.fclinic.authservice.application.command.OAuth2CodeCommand;
import com.fclinic.authservice.application.command.RegisterCommand;
import com.fclinic.authservice.application.command.StaffCreatePatientCommand;
import com.fclinic.authservice.application.command.UpdateProfileCommand;
import com.fclinic.authservice.application.command.UpdateUserCommand;
import com.fclinic.authservice.application.model.AuthResult;
import com.fclinic.authservice.application.model.ForgotPasswordResult;
import com.fclinic.authservice.application.model.IntrospectionResult;
import com.fclinic.authservice.application.model.TokenValidationResult;
import com.fclinic.authservice.domain.aggregate.User;
import com.fclinic.authservice.domain.valueobject.Role;
import org.springframework.stereotype.Component;

@Component
public class AuthApiMapper {

    public RegisterCommand toCommand(RegisterRequest request) {
        return new RegisterCommand(
                request.username(),
                request.password(),
                request.fullName(),
                request.email(),
                request.phoneNumber(),
                request.patientId()
        );
    }

    public LoginCommand toCommand(LoginRequest request) {
        return new LoginCommand(request.username(), request.password());
    }

    public GoogleLoginCommand toCommand(GoogleLoginRequest request) {
        return new GoogleLoginCommand(
                request.email(),
                request.name(),
                request.googleId(),
                request.avatarUrl(),
                request.idToken(),
                request.authorizationCode(),
                request.redirectUri(),
                request.codeVerifier()
        );
    }

    public OAuth2CodeCommand toCommand(OAuth2TokenRequest request) {
        return new OAuth2CodeCommand(
                request.code(),
                request.redirectUri(),
                request.codeVerifier(),
                request.clientId()
        );
    }

    public UpdateProfileCommand toCommand(UpdateProfileRequest request) {
        return new UpdateProfileCommand(request.fullName(), request.email(), request.phoneNumber());
    }

    public ChangePasswordCommand toCommand(ChangePasswordRequest request) {
        return new ChangePasswordCommand(request.oldPassword(), request.newPassword());
    }

    public CreateUserCommand toCommand(CreateUserRequest request) {
        return new CreateUserCommand(
                request.username(),
                request.password(),
                request.fullName(),
                request.email(),
                request.phoneNumber(),
                request.role(),
                request.patientId(),
                request.doctorId()
        );
    }

    public UpdateUserCommand toCommand(UpdateUserRequest request) {
        return new UpdateUserCommand(
                request.fullName(),
                request.email(),
                request.phoneNumber(),
                request.role(),
                request.active(),
                request.patientId(),
                request.doctorId()
        );
    }

    public StaffCreatePatientCommand toCommand(StaffCreatePatientRequest request) {
        return new StaffCreatePatientCommand(
                request.fullName(),
                request.phoneNumber(),
                request.email(),
                request.initialPassword(),
                request.patientId()
        );
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getKeycloakId(),
                user.getPatientId(),
                user.getDoctorId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getFullName(),
                user.getRoleName().name(),
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    public RoleResponse toResponse(Role role) {
        return new RoleResponse(role.getId(), role.getRoleName(), role.getDescription());
    }

    public AuthResponse toResponse(AuthResult result) {
        return new AuthResponse(
                result.accessToken(),
                result.refreshToken(),
                result.tokenType(),
                result.expiresIn(),
                toResponse(result.user())
        );
    }

    public TokenValidationResponse toResponse(TokenValidationResult result) {
        return new TokenValidationResponse(
                result.valid(),
                result.userId(),
                result.keycloakId(),
                result.patientId(),
                result.doctorId(),
                result.username(),
                result.role(),
                result.email()
        );
    }

    public ForgotPasswordResponse toResponse(ForgotPasswordResult result) {
        return new ForgotPasswordResponse(
                result.email(),
                result.message(),
                result.otp(),
                result.expiresInSeconds()
        );
    }

    public Object toIntrospectionWireResponse(IntrospectionResult result) {
        if (result == null || !result.active()) {
            return new InactiveIntrospectionResponse();
        }
        return new ActiveIntrospectionResponse(
                true,
                result.contractVersion(),
                result.iss(),
                result.aud(),
                result.exp(),
                result.sub(),
                result.principalType(),
                result.userId(),
                result.patientId(),
                result.doctorId(),
                result.authorities()
        );
    }
}

