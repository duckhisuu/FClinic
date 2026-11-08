package com.fclinic.authservice.api.controller;

import com.fclinic.authservice.api.dto.request.CreateUserRequest;
import com.fclinic.authservice.api.dto.request.UpdateUserRequest;
import com.fclinic.authservice.api.dto.request.UpdateUserRoleRequest;
import com.fclinic.authservice.api.dto.response.RoleResponse;
import com.fclinic.authservice.api.dto.response.UserResponse;
import com.fclinic.authservice.api.mapper.AuthApiMapper;
import com.fclinic.authservice.application.port.in.ManageUserUseCase;
import com.fclinic.authservice.domain.util.UuidV4;
import com.fclinic.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminUserController {

    private final ManageUserUseCase manageUserUseCase;
    private final AuthApiMapper mapper;

    public AdminUserController(ManageUserUseCase manageUserUseCase, AuthApiMapper mapper) {
        this.manageUserUseCase = manageUserUseCase;
        this.mapper = mapper;
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserResponse>>> searchUsers(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "active", required = false) Boolean active
    ) {
        List<UserResponse> users = manageUserUseCase.searchUsers(keyword, role, active).stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách người dùng thành công", users));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable("id") UUID id) {
        UserResponse user = mapper.toResponse(manageUserUseCase.getUserById(UuidV4.requireValid(id, "id")));
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin người dùng thành công", user));
    }

    @PostMapping("/users")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = mapper.toResponse(manageUserUseCase.createUser(mapper.toCommand(request)));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Tạo tài khoản người dùng thành công", created));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        UserResponse updated = mapper.toResponse(
                manageUserUseCase.updateUser(UuidV4.requireValid(id, "id"), mapper.toCommand(request))
        );
        return ResponseEntity.ok(ApiResponse.success("Cập nhật người dùng thành công", updated));
    }

    @PatchMapping("/users/{id}/status")
    public ResponseEntity<ApiResponse<UserResponse>> toggleUserStatus(
            @PathVariable("id") UUID id,
            @RequestParam("active") boolean active
    ) {
        UserResponse updated = mapper.toResponse(
                manageUserUseCase.toggleUserStatus(UuidV4.requireValid(id, "id"), active)
        );
        String msg = active ? "Mở khóa tài khoản thành công" : "Đã khóa tài khoản và thu hồi phiên Keycloak";
        return ResponseEntity.ok(ApiResponse.success(msg, updated));
    }

    @PatchMapping("/users/{id}/role")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserRole(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateUserRoleRequest request
    ) {
        UserResponse updated = mapper.toResponse(
                manageUserUseCase.updateUserRole(UuidV4.requireValid(id, "id"), request.role())
        );
        return ResponseEntity.ok(ApiResponse.success("Cập nhật vai trò người dùng thành công", updated));
    }

    @GetMapping("/roles")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
        List<RoleResponse> roles = manageUserUseCase.getAllRoles().stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách vai trò thành công", roles));
    }
}

