package com.fclinic.authservice.api.controller;

import com.fclinic.authservice.application.service.AppointmentRecipientService;
import com.fclinic.authservice.infrastructure.config.ServiceClientCredentials;
import com.fclinic.common.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Dedicated SERVICE endpoint; human bearers/introspection/scope secrets cannot access it. */
@RestController
public class AppointmentRecipientController {
    public static final String PATH = "/api/v1/auth/internal/appointment-recipient-accounts";
    private final AppointmentRecipientService recipients;
    private final Environment env;
    public AppointmentRecipientController(AppointmentRecipientService recipients, Environment env) {
        this.recipients = recipients; this.env = env;
    }
    public record Request(UUID patientId, UUID doctorId) { }
    @PostMapping(PATH)
    public ResponseEntity<?> resolve(HttpServletRequest request, @RequestBody Request body) {
        var headers = Collections.list(request.getHeaders("Authorization"));
        var response = ResponseEntity.status(200).cacheControl(CacheControl.noStore())
                .header("X-FClinic-Reference-Contract", "appointment-recipient-accounts/1");
        if (headers.size() != 1 || !ServiceClientCredentials.matches(headers.getFirst(),
                env.getProperty("app.recipients.client-id"), env.getProperty("app.recipients.client-secret")))
            return ResponseEntity.status(401).cacheControl(CacheControl.noStore()).build();
        try { return response.body(ApiResponse.success(recipients.resolve(body.patientId(), body.doctorId()))); }
        catch (AppointmentRecipientService.UnresolvedMapping e) {
            return ResponseEntity.status(503).cacheControl(CacheControl.noStore()).body(
                    ApiResponse.failure("Chưa xác định được tài khoản nhận thông báo."));
        }
    }
}
