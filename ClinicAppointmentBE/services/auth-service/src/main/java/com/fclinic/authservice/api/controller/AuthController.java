package com.fclinic.authservice.api.controller;

import com.fclinic.authservice.api.dto.request.ChangePasswordRequest;
import com.fclinic.authservice.api.dto.request.ForgotPasswordRequest;
import com.fclinic.authservice.api.dto.request.GoogleLoginRequest;
import com.fclinic.authservice.api.dto.request.LoginRequest;
import com.fclinic.authservice.api.dto.request.LogoutRequest;
import com.fclinic.authservice.api.dto.request.OAuth2TokenRequest;
import com.fclinic.authservice.api.dto.request.RefreshTokenRequest;
import com.fclinic.authservice.api.dto.request.RegisterRequest;
import com.fclinic.authservice.api.dto.request.ResetPasswordRequest;
import com.fclinic.authservice.api.dto.request.UpdateProfileRequest;
import com.fclinic.authservice.api.dto.response.AuthResponse;
import com.fclinic.authservice.api.dto.response.ForgotPasswordResponse;
import com.fclinic.authservice.api.dto.response.KeycloakConfigResponse;
import com.fclinic.authservice.api.dto.response.TokenValidationResponse;
import com.fclinic.authservice.api.dto.response.UserResponse;
import com.fclinic.authservice.api.mapper.AuthApiMapper;
import com.fclinic.authservice.application.model.AuthenticatedCaller;
import com.fclinic.authservice.application.port.in.ChangePasswordUseCase;
import com.fclinic.authservice.application.port.in.GetCurrentUserUseCase;
import com.fclinic.authservice.application.port.in.GoogleLoginUseCase;
import com.fclinic.authservice.application.port.in.LoginUseCase;
import com.fclinic.authservice.application.port.in.LogoutUseCase;
import com.fclinic.authservice.application.port.in.OAuth2CodeLoginUseCase;
import com.fclinic.authservice.application.port.in.PasswordRecoveryUseCase;
import com.fclinic.authservice.application.port.in.RefreshTokenUseCase;
import com.fclinic.authservice.application.port.in.RegisterUseCase;
import com.fclinic.authservice.application.port.in.UpdateProfileUseCase;
import com.fclinic.authservice.application.port.in.ValidateTokenUseCase;
import com.fclinic.authservice.domain.exception.InvalidTokenException;
import com.fclinic.authservice.infrastructure.config.KeycloakProperties;
import com.fclinic.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegisterUseCase registerUseCase;
    private final LoginUseCase loginUseCase;
    private final GoogleLoginUseCase googleLoginUseCase;
    private final OAuth2CodeLoginUseCase oauth2CodeLoginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final ValidateTokenUseCase validateTokenUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;
    private final UpdateProfileUseCase updateProfileUseCase;
    private final ChangePasswordUseCase changePasswordUseCase;
    private final PasswordRecoveryUseCase passwordRecoveryUseCase;
    private final AuthApiMapper mapper;
    private final KeycloakProperties keycloakProperties;

    public AuthController(
            RegisterUseCase registerUseCase,
            LoginUseCase loginUseCase,
            GoogleLoginUseCase googleLoginUseCase,
            OAuth2CodeLoginUseCase oauth2CodeLoginUseCase,
            RefreshTokenUseCase refreshTokenUseCase,
            LogoutUseCase logoutUseCase,
            ValidateTokenUseCase validateTokenUseCase,
            GetCurrentUserUseCase getCurrentUserUseCase,
            UpdateProfileUseCase updateProfileUseCase,
            ChangePasswordUseCase changePasswordUseCase,
            PasswordRecoveryUseCase passwordRecoveryUseCase,
            AuthApiMapper mapper,
            KeycloakProperties keycloakProperties
    ) {
        this.registerUseCase = registerUseCase;
        this.loginUseCase = loginUseCase;
        this.googleLoginUseCase = googleLoginUseCase;
        this.oauth2CodeLoginUseCase = oauth2CodeLoginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.logoutUseCase = logoutUseCase;
        this.validateTokenUseCase = validateTokenUseCase;
        this.getCurrentUserUseCase = getCurrentUserUseCase;
        this.updateProfileUseCase = updateProfileUseCase;
        this.changePasswordUseCase = changePasswordUseCase;
        this.passwordRecoveryUseCase = passwordRecoveryUseCase;
        this.mapper = mapper;
        this.keycloakProperties = keycloakProperties;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = mapper.toResponse(registerUseCase.register(mapper.toCommand(request)));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Đăng ký tài khoản thành công", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = mapper.toResponse(loginUseCase.login(mapper.toCommand(request)));
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", response));
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(@RequestBody GoogleLoginRequest request) {
        AuthResponse response = mapper.toResponse(googleLoginUseCase.loginWithGoogle(mapper.toCommand(request)));
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập Google thành công", response));
    }

    @PostMapping("/oauth2/token")
    public ResponseEntity<ApiResponse<AuthResponse>> exchangeAuthorizationCode(@Valid @RequestBody OAuth2TokenRequest request) {
        AuthResponse response = mapper.toResponse(oauth2CodeLoginUseCase.exchangeCode(mapper.toCommand(request)));
        return ResponseEntity.ok(ApiResponse.success("Trao đổi mã xác thực OIDC thành công", response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = mapper.toResponse(refreshTokenUseCase.refresh(request.refreshToken()));
        return ResponseEntity.ok(ApiResponse.success("Làm mới token thành công", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) LogoutRequest request,
            @AuthenticationPrincipal AuthenticatedCaller caller
    ) {
        String refreshToken = request != null ? request.refreshToken() : null;
        UUID callerId = caller != null ? caller.userId() : null;
        logoutUseCase.logout(refreshToken, callerId);
        return ResponseEntity.ok(ApiResponse.success("Đăng xuất thành công", null));
    }

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<TokenValidationResponse>> validate(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam(value = "token", required = false) String tokenParam
    ) {
        String candidate = (authorizationHeader != null && !authorizationHeader.isBlank()) ? authorizationHeader : tokenParam;
        TokenValidationResponse response = mapper.toResponse(validateTokenUseCase.validate(candidate));
        return ResponseEntity.ok(ApiResponse.success("Kiểm tra token thành công", response));
    }

    @GetMapping("/keycloak/config")
    public ResponseEntity<ApiResponse<KeycloakConfigResponse>> getKeycloakConfig() {
        KeycloakConfigResponse response = new KeycloakConfigResponse(
                keycloakProperties.realm(),
                keycloakProperties.authorizationEndpoint(),
                keycloakProperties.tokenEndpoint(),
                keycloakProperties.logoutEndpoint(),
                keycloakProperties.jwkSetUri(),
                keycloakProperties.webClientId()
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy cấu hình OIDC Keycloak thành công", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
            @AuthenticationPrincipal AuthenticatedCaller caller
    ) {
        UUID userId = requireCallerId(caller);
        UserResponse response = mapper.toResponse(getCurrentUserUseCase.getCurrentUser(userId));
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin tài khoản thành công", response));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @AuthenticationPrincipal AuthenticatedCaller caller,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        UUID userId = requireCallerId(caller);
        UserResponse response = mapper.toResponse(updateProfileUseCase.updateProfile(userId, mapper.toCommand(request)));
        return ResponseEntity.ok(ApiResponse.success("Cập nhật hồ sơ cá nhân thành công", response));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal AuthenticatedCaller caller,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        UUID userId = requireCallerId(caller);
        changePasswordUseCase.changePassword(userId, mapper.toCommand(request));
        return ResponseEntity.ok(ApiResponse.success("Đổi mật khẩu thành công", null));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<ForgotPasswordResponse>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        ForgotPasswordResponse response = mapper.toResponse(passwordRecoveryUseCase.forgotPassword(request.email()));
        return ResponseEntity.ok(ApiResponse.success("Yêu cầu khôi phục mật khẩu đã được xử lý", response));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordRecoveryUseCase.resetPassword(request.email(), request.otp(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.success("Đặt lại mật khẩu thành công", null));
    }

    private static UUID requireCallerId(AuthenticatedCaller caller) {
        if (caller == null || caller.userId() == null) {
            throw new InvalidTokenException("Phiên đăng nhập không hợp lệ hoặc thiếu thông tin định danh");
        }
        return caller.userId();
    }
}

