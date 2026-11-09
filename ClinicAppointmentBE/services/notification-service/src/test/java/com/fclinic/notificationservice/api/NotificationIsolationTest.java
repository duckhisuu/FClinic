package com.fclinic.notificationservice.api;

import com.fclinic.notificationservice.application.port.out.NotificationRepositoryPort;
import com.fclinic.notificationservice.domain.aggregate.Notification;
import com.fclinic.notificationservice.domain.model.NotificationType;
import com.fclinic.notificationservice.domain.model.RecipientType;
import com.fclinic.notificationservice.infrastructure.persistence.SpringDataJpaNotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification-isolation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.dynamic=false",
        "eureka.client.enabled=false"
})
class NotificationIsolationTest {
    private static final UUID PATIENT = UUID.fromString("11111111-1111-4111-8111-111111111104");
    private static final UUID DOCTOR = UUID.fromString("11111111-1111-4111-8111-111111111103");
    private static final UUID ADMIN = UUID.fromString("11111111-1111-4111-8111-111111111101");
    @Autowired WebApplicationContext context;
    @Autowired NotificationRepositoryPort notifications;
    @Autowired SpringDataJpaNotificationRepository repository;
    @MockitoBean JwtDecoder decoder;
    private MockMvc mvc;
    private UUID patientNotification;
    private UUID doctorNotification;
    private final UUID sharedEvent = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        patientNotification = save(PATIENT, RecipientType.PATIENT);
        doctorNotification = save(DOCTOR, RecipientType.DOCTOR);
        assertThat(patientNotification.version()).isEqualTo(4);
        assertThat(doctorNotification.version()).isEqualTo(4);
        save(null, RecipientType.PATIENT);
        when(decoder.decode("patient")).thenReturn(token("patient", PATIENT, "ROLE_PATIENT"));
        when(decoder.decode("doctor")).thenReturn(token("doctor", DOCTOR, "ROLE_DOCTOR"));
        when(decoder.decode("admin")).thenReturn(token("admin", ADMIN, "ROLE_ADMIN"));
        when(decoder.decode("staff")).thenReturn(token("staff", UUID.randomUUID(), "ROLE_STAFF"));
        when(decoder.decode("forged")).thenThrow(new BadJwtException("Invalid signature"));
    }

    private UUID save(UUID user, RecipientType type) {
        var n = Notification.pending(user == null ? UUID.randomUUID() : sharedEvent, UUID.randomUUID(), user, type,
                "Người nhận", "shared@example.test", NotificationType.APPOINTMENT_CONFIRMATION,
                "Thông báo", "<p>Lịch hẹn</p>", Instant.now());
        n.markSent(Instant.now());
        return notifications.save(n).getId();
    }

    private Jwt token(String token, UUID user, String role) {
        return Jwt.withTokenValue(token).header("alg", "RS256").subject(UUID.randomUUID().toString())
                .claim("fclinic_user_id", user.toString()).claim("authorities", List.of(role)).build();
    }

    @Test
    void anonymousAndForgedRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/notifications/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/notifications/me").header("Authorization", "Bearer forged"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void eachUserSeesOnlyTheirOwnRowsEvenWhenEmailsMatch() throws Exception {
        mvc.perform(get("/api/v1/notifications/me").header("Authorization", "Bearer patient"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(patientNotification.toString()));
        mvc.perform(get("/api/v1/notifications/me").header("Authorization", "Bearer doctor"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(doctorNotification.toString()));
    }

    @Test
    void suppliedUserIdCannotChangeTheInbox() throws Exception {
        mvc.perform(get("/api/v1/notifications/me").param("userId", DOCTOR.toString())
                        .header("Authorization", "Bearer patient"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].recipientUserId").value(PATIENT.toString()));
    }

    @Test
    void usersCannotReadAnotherRecipientsDetail() throws Exception {
        mvc.perform(get("/api/v1/notifications/1").header("Authorization", "Bearer patient"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/notifications/" + patientNotification).header("Authorization", "Bearer patient"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/notifications/" + doctorNotification).header("Authorization", "Bearer patient"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/notifications/" + patientNotification).header("Authorization", "Bearer doctor"))
                .andExpect(status().isNotFound());
    }

    @Test
    void globalListDetailAndRetryAreAdminOnly() throws Exception {
        for (String role : List.of("patient", "doctor", "staff")) {
            mvc.perform(get("/api/v1/admin/notifications").header("Authorization", "Bearer " + role))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/admin/notifications/" + patientNotification).header("Authorization", "Bearer " + role))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/admin/notifications/" + patientNotification + "/retry")
                            .header("Authorization", "Bearer " + role)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/admin/notifications").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(3));
        mvc.perform(get("/api/v1/admin/notifications/" + doctorNotification).header("Authorization", "Bearer admin"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/notifications/" + patientNotification + "/retry")
                        .header("Authorization", "Bearer admin"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/notifications/me").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
    }
}
