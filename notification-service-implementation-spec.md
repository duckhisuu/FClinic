# Clinic Management System — Notification Service Implementation Specification

> **Purpose of this document**
>
> This is an implementation-ready specification for the `notification-service` in the Clinic Management System microservices project.
> It is written so that an implementation agent can build the service with minimal ambiguity.
>
> The service is **event-driven**. It consumes appointment lifecycle events from RabbitMQ, creates durable notification jobs, renders notification content, sends email through SMTP/MailHog, records delivery history, prevents duplicate processing, retries temporary failures, and exposes limited administrative/query APIs.
>
> **Primary rule:** this service must never own appointment business rules. It reacts to facts that have already occurred.

---

# 1. Scope

## 1.1 Responsibilities

The Notification Service MUST:

1. Consume these appointment events from RabbitMQ:
   - `appointment.created`
   - `appointment.cancelled`
   - `appointment.rescheduled`
   - `appointment.reminder`
2. Validate the inbound event contract.
3. Prevent duplicate event processing using `eventId`.
4. Create one or more durable `Notification` records from an event.
5. Render notification subject/body from templates.
6. Send HTML email using SMTP through Spring `JavaMailSender`.
7. Use MailHog in local development.
8. Track delivery state and attempt history.
9. Retry transient email failures with application-level backoff.
10. Move irrecoverable RabbitMQ ingestion failures to a dead-letter queue.
11. Allow authorized users/admins to query notification history.
12. Allow an authorized admin to manually retry a permanently failed notification.
13. Recover notifications that were left in `PROCESSING` because an instance crashed.
14. Expose health/metrics through Spring Boot Actuator.
15. Register with the project service-discovery mechanism if Eureka is enabled.

## 1.2 Non-responsibilities

The Notification Service MUST NOT:

- validate doctor availability;
- determine whether an appointment may be booked;
- modify appointments;
- query or write the Appointment Service database;
- query or write Patient Service or Doctor Service databases;
- own patient/doctor profiles;
- calculate appointment reminders from appointment data;
- directly schedule medical appointments;
- synchronously call Patient Service or Doctor Service merely to compose an email;
- share database tables with other services.

The Appointment Service should publish a sufficiently rich event containing all notification-relevant snapshot data.

---

# 2. Architectural Decisions

## 2.1 Processing model

Use two distinct phases:

```text
PHASE A — EVENT INGESTION

Appointment Service
       |
       | publish event
       v
   RabbitMQ
       |
       v
AppointmentEventListener
       |
       v
NotificationIngestionService
       |
       +--> validate event
       +--> idempotency check
       +--> render templates
       +--> save Notification row(s)
       +--> save ProcessedEvent row
       |
       v
ACK RabbitMQ message


PHASE B — DELIVERY

NotificationDeliveryScheduler
       |
       | find due jobs
       v
NotificationDeliveryService
       |
       +--> atomically claim Notification
       +--> EmailSenderService
       |       |
       |       v
       |    SMTP / MailHog
       |
       +--> mark SENT
       |
       +--> or schedule RETRY
       |
       +--> or mark PERMANENT_FAILURE
```

### Why use two phases?

Do **not** send email directly inside the RabbitMQ listener.

If email delivery is performed inside the listener, SMTP availability controls whether the broker message can be acknowledged. That causes awkward duplicate deliveries, long-running consumers, and broker redelivery loops.

Instead:

- RabbitMQ guarantees the event reaches this service.
- The database guarantees a durable notification job exists.
- The delivery worker owns SMTP retries.
- `ProcessedEvent.eventId` provides idempotent event ingestion.
- `Notification.status` provides independent delivery state.

---

# 3. Recommended Technology Stack

## 3.1 Baseline

If the overall project already has a parent POM/BOM, **inherit its Java, Spring Boot, and Spring Cloud versions**. Do not independently upgrade this service.

For a fresh service created today, the recommended baseline is:

- Java 21
- Spring Boot 4.1.x
- Maven
- Spring Web
- Spring Data JPA
- PostgreSQL
- Flyway
- Spring AMQP / RabbitMQ
- Spring Mail / `JavaMailSender`
- Thymeleaf
- Bean Validation
- Spring Boot Actuator
- Springdoc OpenAPI if used by the rest of the project
- Eureka Client if used by the rest of the project
- Docker
- MailHog for local email testing

Spring Boot 4.1.1 requires Java 17 or newer. Java 21 is preferred as a stable LTS baseline.

## 3.2 Important version rule

The agent MUST NOT hardcode a Spring Cloud version without checking the root project BOM.

If the root project already contains:

```xml
<dependencyManagement>
    ...
</dependencyManagement>
```

reuse it.

---

# 4. Service Identity and Ports

Default service identity:

```yaml
spring:
  application:
    name: notification-service

server:
  port: 8085
```

Default local infrastructure:

| Component | Port |
|---|---:|
| Notification Service | `8085` |
| RabbitMQ AMQP | `5672` |
| RabbitMQ Management UI | `15672` |
| PostgreSQL | `5432` |
| MailHog SMTP | `1025` |
| MailHog Web UI | `8025` |
| Eureka, if enabled | `8761` |

---

# 5. Repository / Module Layout

Use this structure:

```text
notification-service/
├── pom.xml
├── Dockerfile
├── README.md
├── src/
│   ├── main/
│   │   ├── java/com/clinic/notification/
│   │   │   ├── NotificationServiceApplication.java
│   │   │   │
│   │   │   ├── config/
│   │   │   │   ├── RabbitMqConfig.java
│   │   │   │   ├── MailConfig.java
│   │   │   │   ├── SchedulingConfig.java
│   │   │   │   └── NotificationProperties.java
│   │   │   │
│   │   │   ├── controller/
│   │   │   │   └── NotificationController.java
│   │   │   │
│   │   │   ├── dto/
│   │   │   │   ├── event/
│   │   │   │   │   ├── AppointmentNotificationEvent.java
│   │   │   │   │   ├── AppointmentSnapshotDto.java
│   │   │   │   │   └── RecipientDto.java
│   │   │   │   ├── request/
│   │   │   │   │   └── RetryNotificationRequest.java
│   │   │   │   └── response/
│   │   │   │       ├── NotificationResponse.java
│   │   │   │       └── NotificationSummaryResponse.java
│   │   │   │
│   │   │   ├── entity/
│   │   │   │   ├── Notification.java
│   │   │   │   ├── NotificationTemplate.java
│   │   │   │   └── ProcessedEvent.java
│   │   │   │
│   │   │   ├── enumtype/
│   │   │   │   ├── AppointmentEventType.java
│   │   │   │   ├── NotificationChannel.java
│   │   │   │   ├── NotificationStatus.java
│   │   │   │   ├── NotificationType.java
│   │   │   │   └── RecipientType.java
│   │   │   │
│   │   │   ├── exception/
│   │   │   │   ├── InvalidNotificationEventException.java
│   │   │   │   ├── NotificationNotFoundException.java
│   │   │   │   ├── NotificationNotRetryableException.java
│   │   │   │   ├── TemplateNotFoundException.java
│   │   │   │   ├── EmailDeliveryException.java
│   │   │   │   └── GlobalExceptionHandler.java
│   │   │   │
│   │   │   ├── listener/
│   │   │   │   └── AppointmentEventListener.java
│   │   │   │
│   │   │   ├── mapper/
│   │   │   │   └── NotificationMapper.java
│   │   │   ├── repository/
│   │   │   │   ├── NotificationRepository.java
│   │   │   │   ├── NotificationTemplateRepository.java
│   │   │   │   └── ProcessedEventRepository.java
│   │   │   ├── scheduler/
│   │   │   │   ├── NotificationDeliveryScheduler.java
│   │   │   │   └── StuckNotificationRecoveryScheduler.java
│   │   │   ├── service/
│   │   │   │   ├── NotificationIngestionService.java
│   │   │   │   ├── NotificationDeliveryService.java
│   │   │   │   ├── NotificationStateService.java
│   │   │   │   ├── EmailSenderService.java
│   │   │   │   ├── TemplateService.java
│   │   │   │   └── NotificationQueryService.java
│   │   │   ├── service/impl/
│   │   │   │   ├── NotificationIngestionServiceImpl.java
│   │   │   │   ├── NotificationDeliveryServiceImpl.java
│   │   │   │   ├── NotificationStateServiceImpl.java
│   │   │   │   ├── SmtpEmailSenderService.java
│   │   │   │   ├── TemplateServiceImpl.java
│   │   │   │   └── NotificationQueryServiceImpl.java
│   │   │   └── valueobject/
│   │   │       ├── EmailAddress.java
│   │   │       ├── Recipient.java
│   │   │       ├── RenderedMessage.java
│   │   │       └── DeliveryResult.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-local.yml
│   │       ├── db/migration/
│   │       │   ├── V1__create_notification_tables.sql
│   │       │   ├── V2__create_indexes.sql
│   │       │   └── V3__seed_notification_templates.sql
│   │       └── logback-spring.xml
│   └── test/
│       └── java/com/clinic/notification/
│           ├── service/
│           ├── listener/
│           ├── repository/
│           └── integration/
└── .gitignore
```

---

# 6. Maven Dependencies

The exact versions should come from the root project.

Example dependencies:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-amqp</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-mail</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-thymeleaf</artifactId>
    </dependency>

    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>

    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-database-postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>

    <!-- Only if the project uses Eureka -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>

    <dependency>
        <groupId>org.springframework.amqp</groupId>
        <artifactId>spring-rabbit-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

If the project already uses Spring Security/JWT at every downstream service, add the same security dependencies and shared JWT configuration. Do not invent a second authentication model specifically for Notification Service.

---

# 7. Main Application

```java
package com.clinic.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
```

---

# 8. Domain Enums

## 8.1 `AppointmentEventType`

```java
package com.clinic.notification.enumtype;

public enum AppointmentEventType {
    APPOINTMENT_CREATED,
    APPOINTMENT_CANCELLED,
    APPOINTMENT_RESCHEDULED,
    APPOINTMENT_REMINDER
}
```

## 8.2 `NotificationType`

```java
package com.clinic.notification.enumtype;

public enum NotificationType {
    APPOINTMENT_CONFIRMATION,
    APPOINTMENT_CANCELLATION,
    APPOINTMENT_RESCHEDULED,
    APPOINTMENT_REMINDER
}
```

Mapping:

| Event | Notification type |
|---|---|
| `APPOINTMENT_CREATED` | `APPOINTMENT_CONFIRMATION` |
| `APPOINTMENT_CANCELLED` | `APPOINTMENT_CANCELLATION` |
| `APPOINTMENT_RESCHEDULED` | `APPOINTMENT_RESCHEDULED` |
| `APPOINTMENT_REMINDER` | `APPOINTMENT_REMINDER` |

## 8.3 `NotificationChannel`

```java
package com.clinic.notification.enumtype;

public enum NotificationChannel {
    EMAIL
}
```

## 8.4 `RecipientType`

```java
package com.clinic.notification.enumtype;

public enum RecipientType {
    PATIENT,
    DOCTOR
}
```

## 8.5 `NotificationStatus`

```java
package com.clinic.notification.enumtype;

public enum NotificationStatus {
    PENDING,
    PROCESSING,
    RETRY_SCHEDULED,
    SENT,
    PERMANENT_FAILURE
}
```

| Status | Meaning |
|---|---|
| `PENDING` | created and ready for first delivery |
| `PROCESSING` | claimed by one worker |
| `RETRY_SCHEDULED` | previous attempt failed; retry later |
| `SENT` | successfully handed to SMTP |
| `PERMANENT_FAILURE` | max delivery attempts exhausted |

---

# 9. Event Contract DTOs

## 9.1 Contract requirements

Every event MUST contain:

- unique `eventId`;
- `eventType`;
- `occurredAt`;
- `source`;
- appointment snapshot;
- patient recipient snapshot;
- doctor recipient snapshot.

The event should be self-contained enough for this service to create email content without REST calls.

## 9.2 `RecipientDto`

```java
package com.clinic.notification.dto.event;

import com.clinic.notification.enumtype.RecipientType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RecipientDto(
        @NotNull Long userId,
        @NotNull RecipientType recipientType,
        @NotBlank String displayName,
        @NotBlank @Email String email
) {}
```

## 9.3 `AppointmentSnapshotDto`

```java
package com.clinic.notification.dto.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record AppointmentSnapshotDto(
        @NotNull Long appointmentId,
        @NotNull LocalDate appointmentDate,
        @NotNull LocalTime startTime,
        LocalTime endTime,
        @NotBlank String clinicName,
        String roomName,
        String reason,
        String cancellationReason
) {}
```

Do not place full medical records in the event.

## 9.4 `AppointmentNotificationEvent`

```java
package com.clinic.notification.dto.event;

import com.clinic.notification.enumtype.AppointmentEventType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record AppointmentNotificationEvent(
        @NotNull UUID eventId,
        @NotNull AppointmentEventType eventType,
        @NotNull Instant occurredAt,
        @NotBlank String source,
        @Valid @NotNull AppointmentSnapshotDto appointment,
        @Valid @NotNull RecipientDto patient,
        @Valid @NotNull RecipientDto doctor
) {}
```

Example JSON:

```json
{
  "eventId": "927c3d89-22e2-4a7d-881a-123456789abc",
  "eventType": "APPOINTMENT_CREATED",
  "occurredAt": "2026-10-10T10:20:00Z",
  "source": "appointment-service",
  "appointment": {
    "appointmentId": 152,
    "appointmentDate": "2026-10-20",
    "startTime": "10:00:00",
    "endTime": "10:30:00",
    "clinicName": "MSS Clinic",
    "roomName": "Room 203",
    "reason": "Annual check-up",
    "cancellationReason": null
  },
  "patient": {
    "userId": 25,
    "recipientType": "PATIENT",
    "displayName": "Nguyen Van A",
    "email": "patient@example.com"
  },
  "doctor": {
    "userId": 7,
    "recipientType": "DOCTOR",
    "displayName": "Dr. Tran",
    "email": "doctor@example.com"
  }
}
```

---

# 10. Value Objects

## 10.1 `EmailAddress`

```java
package com.clinic.notification.valueobject;

import java.util.regex.Pattern;

public record EmailAddress(String value) {

    private static final Pattern SIMPLE_EMAIL =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public EmailAddress {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Email address must not be blank");
        }

        String normalized = value.trim().toLowerCase();

        if (!SIMPLE_EMAIL.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid email address: " + value);
        }

        value = normalized;
    }
}
```

## 10.2 `Recipient`

```java
package com.clinic.notification.valueobject;

import com.clinic.notification.enumtype.RecipientType;

public record Recipient(
        Long userId,
        RecipientType type,
        String displayName,
        EmailAddress email
) {}
```

## 10.3 `RenderedMessage`

```java
package com.clinic.notification.valueobject;

public record RenderedMessage(
        String subject,
        String htmlBody
) {}
```

## 10.4 `DeliveryResult`

```java
package com.clinic.notification.valueobject;

public record DeliveryResult(
        boolean success,
        String providerMessageId,
        String errorCode,
        String errorMessage
) {
    public static DeliveryResult success(String providerMessageId) {
        return new DeliveryResult(true, providerMessageId, null, null);
    }

    public static DeliveryResult failure(String errorCode, String errorMessage) {
        return new DeliveryResult(false, null, errorCode, errorMessage);
    }
}
```

---

# 11. Persistence Model

Use **database-per-service**. This service owns `notification_db`.

Use Flyway rather than relying on Hibernate to create production schema.

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

## 11.1 `Notification` entity

Fields:

| Field | Type | Notes |
|---|---|---|
| `id` | `Long` | DB identity |
| `eventId` | `UUID` | source event |
| `appointmentId` | `Long` | logical reference only |
| `recipientUserId` | `Long` | query/ownership |
| `recipientType` | enum | PATIENT / DOCTOR |
| `recipientName` | String | snapshot |
| `recipientEmail` | String | snapshot |
| `type` | enum | notification meaning |
| `channel` | enum | EMAIL |
| `subject` | String | rendered snapshot |
| `htmlBody` | TEXT | rendered snapshot |
| `status` | enum | delivery lifecycle |
| `attemptCount` | int | failed SMTP attempts |
| `nextAttemptAt` | Instant | due time |
| `lastAttemptAt` | Instant | nullable |
| `sentAt` | Instant | nullable |
| `lastErrorCode` | String | nullable |
| `lastErrorMessage` | String | nullable |
| `createdAt` | Instant | audit |
| `updatedAt` | Instant | audit |
| `version` | long | optimistic locking |

Unique key:

```text
(event_id, recipient_email, notification_type, channel)
```

Implementation:

```java
package com.clinic.notification.entity;

import com.clinic.notification.enumtype.*;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "notifications",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_notification_event_recipient_type_channel",
        columnNames = {
            "event_id",
            "recipient_email",
            "notification_type",
            "channel"
        }
    )
)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, columnDefinition = "uuid")
    private UUID eventId;

    @Column(name = "appointment_id", nullable = false)
    private Long appointmentId;

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false, length = 20)
    private RecipientType recipientType;

    @Column(name = "recipient_name", nullable = false, length = 200)
    private String recipientName;

    @Column(name = "recipient_email", nullable = false, length = 320)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "subject", nullable = false, length = 500)
    private String subject;

    @Column(name = "html_body", nullable = false, columnDefinition = "text")
    private String htmlBody;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private NotificationStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = 1000)
    private String lastErrorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Notification() {}

    public static Notification pending(
            UUID eventId,
            Long appointmentId,
            Long recipientUserId,
            RecipientType recipientType,
            String recipientName,
            String recipientEmail,
            NotificationType type,
            String subject,
            String htmlBody,
            Instant now
    ) {
        Notification n = new Notification();
        n.eventId = eventId;
        n.appointmentId = appointmentId;
        n.recipientUserId = recipientUserId;
        n.recipientType = recipientType;
        n.recipientName = recipientName;
        n.recipientEmail = recipientEmail;
        n.type = type;
        n.channel = NotificationChannel.EMAIL;
        n.subject = subject;
        n.htmlBody = htmlBody;
        n.status = NotificationStatus.PENDING;
        n.attemptCount = 0;
        n.nextAttemptAt = now;
        n.createdAt = now;
        n.updatedAt = now;
        return n;
    }

    public void markProcessing(Instant now) {
        this.status = NotificationStatus.PROCESSING;
        this.lastAttemptAt = now;
        this.updatedAt = now;
    }

    public void markSent(Instant now) {
        this.status = NotificationStatus.SENT;
        this.sentAt = now;
        this.nextAttemptAt = null;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
        this.updatedAt = now;
    }

    public void scheduleRetry(
            Instant nextAttemptAt,
            String errorCode,
            String errorMessage,
            Instant now
    ) {
        this.attemptCount++;
        this.status = NotificationStatus.RETRY_SCHEDULED;
        this.nextAttemptAt = nextAttemptAt;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = truncate(errorMessage, 1000);
        this.updatedAt = now;
    }

    public void markPermanentFailure(
            String errorCode,
            String errorMessage,
            Instant now
    ) {
        this.attemptCount++;
        this.status = NotificationStatus.PERMANENT_FAILURE;
        this.nextAttemptAt = null;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = truncate(errorMessage, 1000);
        this.updatedAt = now;
    }

    public void resetForManualRetry(Instant now) {
        this.status = NotificationStatus.RETRY_SCHEDULED;
        this.nextAttemptAt = now;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
        this.updatedAt = now;
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    // Add standard getters required by services/mappers.
}
```

Attempt-count convention:

- initial `0`;
- first failed SMTP attempt -> `1`;
- second failed SMTP attempt -> `2`;
- third failed SMTP attempt -> `3`, then `PERMANENT_FAILURE`;
- success does not increment failed-attempt count.

## 11.2 `ProcessedEvent`

```java
package com.clinic.notification.entity;

import com.clinic.notification.enumtype.AppointmentEventType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false, columnDefinition = "uuid")
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private AppointmentEventType eventType;

    @Column(name = "source", nullable = false, length = 100)
    private String source;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {}

    public ProcessedEvent(
            UUID eventId,
            AppointmentEventType eventType,
            String source,
            Instant occurredAt,
            Instant processedAt
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.source = source;
        this.occurredAt = occurredAt;
        this.processedAt = processedAt;
    }

    // getters
}
```

## 11.3 `NotificationTemplate`

```java
package com.clinic.notification.entity;

import com.clinic.notification.enumtype.*;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
    name = "notification_templates",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_template_type_recipient_channel",
        columnNames = {
            "notification_type",
            "recipient_type",
            "channel"
        }
    )
)
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false, length = 20)
    private RecipientType recipientType;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "subject_template", nullable = false, length = 500)
    private String subjectTemplate;

    @Column(name = "body_template", nullable = false, columnDefinition = "text")
    private String bodyTemplate;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationTemplate() {}

    // getters
}
```

---

# 12. Flyway Migrations

## `V1__create_notification_tables.sql`

```sql
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL,
    appointment_id BIGINT NOT NULL,
    recipient_user_id BIGINT NOT NULL,
    recipient_type VARCHAR(20) NOT NULL,
    recipient_name VARCHAR(200) NOT NULL,
    recipient_email VARCHAR(320) NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    html_body TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NULL,
    last_attempt_at TIMESTAMPTZ NULL,
    sent_at TIMESTAMPTZ NULL,
    last_error_code VARCHAR(100) NULL,
    last_error_message VARCHAR(1000) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uk_notification_event_recipient_type_channel
        UNIQUE (event_id, recipient_email, notification_type, channel)
);

CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    source VARCHAR(100) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE notification_templates (
    id BIGSERIAL PRIMARY KEY,
    notification_type VARCHAR(50) NOT NULL,
    recipient_type VARCHAR(20) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    subject_template VARCHAR(500) NOT NULL,
    body_template TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT uk_template_type_recipient_channel
        UNIQUE (notification_type, recipient_type, channel)
);
```

## `V2__create_indexes.sql`

```sql
CREATE INDEX idx_notification_delivery_due
    ON notifications(status, next_attempt_at);

CREATE INDEX idx_notification_recipient_user
    ON notifications(recipient_user_id, created_at DESC);

CREATE INDEX idx_notification_appointment
    ON notifications(appointment_id);

CREATE INDEX idx_notification_event
    ON notifications(event_id);

CREATE INDEX idx_notification_status
    ON notifications(status);
```

## `V3__seed_notification_templates.sql`

Seed eight template rows:

1. confirmation patient;
2. confirmation doctor;
3. cancellation patient;
4. cancellation doctor;
5. rescheduled patient;
6. rescheduled doctor;
7. reminder patient;
8. reminder doctor.

Example:

```sql
INSERT INTO notification_templates (
    notification_type,
    recipient_type,
    channel,
    subject_template,
    body_template,
    active,
    created_at,
    updated_at
)
VALUES (
    'APPOINTMENT_CONFIRMATION',
    'PATIENT',
    'EMAIL',
    'Appointment confirmed with [[${doctorName}]]',
    '<html>
       <body>
         <h2>Appointment Confirmed</h2>
         <p>Hello [[${recipientName}]],</p>
         <p>Your appointment with <strong>[[${doctorName}]]</strong> has been confirmed.</p>
         <p><strong>Date:</strong> [[${appointmentDate}]]</p>
         <p><strong>Time:</strong> [[${appointmentTime}]]</p>
         <p><strong>Clinic:</strong> [[${clinicName}]]</p>
         <p><strong>Room:</strong> [[${roomName}]]</p>
       </body>
     </html>',
    TRUE,
    NOW(),
    NOW()
);
```

---

# 13. RabbitMQ Topology

Use these names unless the whole project already has a naming convention.

```text
Main topic exchange:
clinic.events

Notification queue:
notification.appointment.q

Dead-letter exchange:
clinic.dlx

Dead-letter queue:
notification.appointment.dlq

DLQ routing key:
notification.appointment.dead
```

Inbound routing keys:

```text
appointment.created
appointment.cancelled
appointment.rescheduled
appointment.reminder
```

Topology:

```text
                        clinic.events
                      (topic exchange)
                            |
          +-----------------+-------------------+
          |                 |                   |
 appointment.created appointment.cancelled appointment.rescheduled
          |                 |                   |
          +-----------------+-------------------+
                            |
                  appointment.reminder
                            |
                            v
                notification.appointment.q
                            |
                            v
                  Notification Service
                            |
                      reject after retry
                            |
                            v
                        clinic.dlx
                            |
             notification.appointment.dead
                            |
                            v
               notification.appointment.dlq
```

## `RabbitMqConfig`

For Spring AMQP 4.x use `JacksonJsonMessageConverter`.

```java
package com.clinic.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String EVENTS_EXCHANGE = "clinic.events";
    public static final String NOTIFICATION_QUEUE = "notification.appointment.q";

    public static final String DLX = "clinic.dlx";
    public static final String DLQ = "notification.appointment.dlq";
    public static final String DLQ_ROUTING_KEY = "notification.appointment.dead";

    @Bean
    TopicExchange clinicEventsExchange() {
        return ExchangeBuilder.topicExchange(EVENTS_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DLX)
                .durable(true)
                .build();
    }

    @Bean
    Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue notificationDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    Binding createdBinding(
            Queue notificationQueue,
            TopicExchange clinicEventsExchange
    ) {
        return BindingBuilder.bind(notificationQueue)
                .to(clinicEventsExchange)
                .with("appointment.created");
    }

    @Bean
    Binding cancelledBinding(
            Queue notificationQueue,
            TopicExchange clinicEventsExchange
    ) {
        return BindingBuilder.bind(notificationQueue)
                .to(clinicEventsExchange)
                .with("appointment.cancelled");
    }

    @Bean
    Binding rescheduledBinding(
            Queue notificationQueue,
            TopicExchange clinicEventsExchange
    ) {
        return BindingBuilder.bind(notificationQueue)
                .to(clinicEventsExchange)
                .with("appointment.rescheduled");
    }

    @Bean
    Binding reminderBinding(
            Queue notificationQueue,
            TopicExchange clinicEventsExchange
    ) {
        return BindingBuilder.bind(notificationQueue)
                .to(clinicEventsExchange)
                .with("appointment.reminder");
    }

    @Bean
    Binding dlqBinding(
            Queue notificationDlq,
            DirectExchange deadLetterExchange
    ) {
        return BindingBuilder.bind(notificationDlq)
                .to(deadLetterExchange)
                .with(DLQ_ROUTING_KEY);
    }

    @Bean
    JacksonJsonMessageConverter rabbitJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
```

If the root project is on Spring AMQP 3.x, use the compatible converter from that generation while keeping the same event JSON contract.

---

# 14. Rabbit Listener Configuration

```yaml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:guest}
    password: ${RABBITMQ_PASSWORD:guest}

    listener:
      simple:
        acknowledge-mode: auto
        default-requeue-rejected: false
        prefetch: 20
        concurrency: 1
        max-concurrency: 4
        retry:
          enabled: true
          max-attempts: 3
          initial-interval: 1s
          multiplier: 2
          max-interval: 10s
```

Behavior:

- successful ingestion -> ACK;
- listener throws -> local listener retry;
- retries exhausted -> reject;
- because the queue declares a DLX and requeue is disabled -> RabbitMQ routes to DLQ.

Listener retry is for **event ingestion**, not email delivery.

---

# 15. Appointment Event Listener

```java
package com.clinic.notification.listener;

import com.clinic.notification.config.RabbitMqConfig;
import com.clinic.notification.dto.event.AppointmentNotificationEvent;
import com.clinic.notification.service.NotificationIngestionService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AppointmentEventListener {

    private final NotificationIngestionService ingestionService;

    public AppointmentEventListener(
            NotificationIngestionService ingestionService
    ) {
        this.ingestionService = ingestionService;
    }

    @RabbitListener(queues = RabbitMqConfig.NOTIFICATION_QUEUE)
    public void consume(AppointmentNotificationEvent event) {
        ingestionService.ingest(event);
    }
}
```

Keep listener logic thin.

---

# 16. Repositories

## `ProcessedEventRepository`

```java
public interface ProcessedEventRepository
        extends JpaRepository<ProcessedEvent, UUID> {
}
```

## `NotificationTemplateRepository`

```java
public interface NotificationTemplateRepository
        extends JpaRepository<NotificationTemplate, Long> {

    Optional<NotificationTemplate>
    findByNotificationTypeAndRecipientTypeAndChannelAndActiveTrue(
            NotificationType type,
            RecipientType recipientType,
            NotificationChannel channel
    );
}
```

## `NotificationRepository`

```java
public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientUserId(
            Long recipientUserId,
            Pageable pageable
    );

    Page<Notification> findByStatus(
            NotificationStatus status,
            Pageable pageable
    );

    List<Notification>
    findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Collection<NotificationStatus> statuses,
            Instant now
    );

    List<Notification>
    findTop100ByStatusAndLastAttemptAtLessThan(
            NotificationStatus status,
            Instant staleBefore
    );

    @Modifying
    @Transactional
    @Query("""
        update Notification n
           set n.status = :processing,
               n.lastAttemptAt = :now,
               n.updatedAt = :now
         where n.id = :id
           and n.status in :claimable
    """)
    int claimForProcessing(
            @Param("id") Long id,
            @Param("claimable") Collection<NotificationStatus> claimable,
            @Param("processing") NotificationStatus processing,
            @Param("now") Instant now
    );
}
```

Atomic claiming prevents two worker instances sending the same due row.

---

# 17. Template Service

Interface:

```java
public interface TemplateService {

    RenderedMessage render(
            NotificationType type,
            RecipientType recipientType,
            Map<String, Object> variables
    );
}
```

Configure a string template engine for DB-backed templates:

```java
@Configuration
public class MailConfig {

    @Bean
    TemplateEngine notificationTemplateEngine() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);

        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
```

Implementation:

```java
@Service
public class TemplateServiceImpl implements TemplateService {

    private final NotificationTemplateRepository templateRepository;
    private final TemplateEngine templateEngine;

    public TemplateServiceImpl(
            NotificationTemplateRepository templateRepository,
            TemplateEngine notificationTemplateEngine
    ) {
        this.templateRepository = templateRepository;
        this.templateEngine = notificationTemplateEngine;
    }

    @Override
    public RenderedMessage render(
            NotificationType type,
            RecipientType recipientType,
            Map<String, Object> variables
    ) {
        NotificationTemplate template =
                templateRepository
                    .findByNotificationTypeAndRecipientTypeAndChannelAndActiveTrue(
                        type,
                        recipientType,
                        NotificationChannel.EMAIL
                    )
                    .orElseThrow(() ->
                        new TemplateNotFoundException(type, recipientType)
                    );

        Context context = new Context();
        context.setVariables(variables);

        String subject = templateEngine.process(
                template.getSubjectTemplate(),
                context
        );

        String body = templateEngine.process(
                template.getBodyTemplate(),
                context
        );

        return new RenderedMessage(subject, body);
    }
}
```

---

# 18. Notification Ingestion Service

Interface:

```java
public interface NotificationIngestionService {
    void ingest(AppointmentNotificationEvent event);
}
```

Required behavior in **one DB transaction**:

1. validate event;
2. if `ProcessedEvent(eventId)` already exists, log duplicate and return success;
3. map event type -> notification type;
4. render patient message;
5. render doctor message;
6. persist two `Notification` jobs;
7. persist `ProcessedEvent`;
8. commit;
9. listener returns -> ACK.

`ProcessedEvent` must be committed in the same transaction as the jobs.

Mapping:

```java
private NotificationType mapType(AppointmentEventType eventType) {
    return switch (eventType) {
        case APPOINTMENT_CREATED ->
                NotificationType.APPOINTMENT_CONFIRMATION;
        case APPOINTMENT_CANCELLED ->
                NotificationType.APPOINTMENT_CANCELLATION;
        case APPOINTMENT_RESCHEDULED ->
                NotificationType.APPOINTMENT_RESCHEDULED;
        case APPOINTMENT_REMINDER ->
                NotificationType.APPOINTMENT_REMINDER;
    };
}
```

Template variables:

```text
recipientName
patientName
doctorName
appointmentId
appointmentDate
appointmentTime
appointmentEndTime
clinicName
roomName
reason
cancellationReason
```

Use `Clock` for server timestamps.

Persist UTC timestamps.

Recommended implementation outline:

```java
@Service
public class NotificationIngestionServiceImpl
        implements NotificationIngestionService {

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationRepository notificationRepository;
    private final TemplateService templateService;
    private final Clock clock;

    @Override
    @Transactional
    public void ingest(AppointmentNotificationEvent event) {

        validate(event);

        if (processedEventRepository.existsById(event.eventId())) {
            return;
        }

        NotificationType notificationType = mapType(event.eventType());
        Instant now = clock.instant();

        createNotification(event, event.patient(), notificationType, now);
        createNotification(event, event.doctor(), notificationType, now);

        processedEventRepository.save(
            new ProcessedEvent(
                event.eventId(),
                event.eventType(),
                event.source(),
                event.occurredAt(),
                now
            )
        );
    }

    private void createNotification(
            AppointmentNotificationEvent event,
            RecipientDto recipient,
            NotificationType type,
            Instant now
    ) {
        Map<String, Object> variables = buildVariables(event, recipient);

        RenderedMessage rendered =
                templateService.render(
                    type,
                    recipient.recipientType(),
                    variables
                );

        Notification notification =
                Notification.pending(
                    event.eventId(),
                    event.appointment().appointmentId(),
                    recipient.userId(),
                    recipient.recipientType(),
                    recipient.displayName(),
                    recipient.email().trim().toLowerCase(),
                    type,
                    rendered.subject(),
                    rendered.htmlBody(),
                    now
                );

        notificationRepository.save(notification);
    }
}
```

Malformed event -> `InvalidNotificationEventException` -> listener retries -> DLQ.

---

# 19. Email Sender

Interface:

```java
public interface EmailSenderService {

    DeliveryResult sendHtml(
            EmailAddress to,
            String recipientName,
            String subject,
            String htmlBody
    );
}
```

SMTP implementation:

```java
@Service
public class SmtpEmailSenderService
        implements EmailSenderService {

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    public SmtpEmailSenderService(
            JavaMailSender mailSender,
            NotificationProperties properties
    ) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public DeliveryResult sendHtml(
            EmailAddress to,
            String recipientName,
            String subject,
            String htmlBody
    ) {
        try {
            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(
                properties.getMail().getFromAddress(),
                properties.getMail().getFromName()
            );

            helper.setTo(to.value());
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(message);

            return DeliveryResult.success(null);

        } catch (Exception ex) {
            return DeliveryResult.failure(
                ex.getClass().getSimpleName(),
                ex.getMessage() == null
                    ? "Unknown email delivery error"
                    : ex.getMessage()
            );
        }
    }
}
```

---

# 20. Mail Configuration

Local MailHog:

```yaml
spring:
  mail:
    host: ${MAIL_HOST:localhost}
    port: ${MAIL_PORT:1025}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties:
      "[mail.smtp.auth]": false
      "[mail.smtp.starttls.enable]": false
      "[mail.smtp.connectiontimeout]": 5000
      "[mail.smtp.timeout]": 5000
      "[mail.smtp.writetimeout]": 5000

app:
  notification:
    mail:
      from-address: ${MAIL_FROM_ADDRESS:no-reply@clinic.local}
      from-name: ${MAIL_FROM_NAME:MSS Clinic}
```

Production-like SMTP uses environment variables and can enable auth/TLS.

Do not commit real credentials.

---

# 21. Notification Properties

```java
@ConfigurationProperties(prefix = "app.notification")
public class NotificationProperties {

    private String timezone = "Asia/Ho_Chi_Minh";
    private Delivery delivery = new Delivery();
    private Mail mail = new Mail();

    public static class Delivery {
        private int batchSize = 100;
        private int maxAttempts = 3;
        private Duration processingTimeout = Duration.ofMinutes(10);
        private List<Duration> retryDelays =
                List.of(
                    Duration.ofMinutes(1),
                    Duration.ofMinutes(5)
                );

        // getters/setters
    }

    public static class Mail {
        private String fromAddress;
        private String fromName;

        // getters/setters
    }

    // getters/setters
}
```

Defaults:

```yaml
app:
  notification:
    timezone: ${NOTIFICATION_TIMEZONE:Asia/Ho_Chi_Minh}
    delivery:
      scheduler-delay-ms: ${DELIVERY_SCHEDULER_DELAY_MS:5000}
      batch-size: ${DELIVERY_BATCH_SIZE:100}
      max-attempts: ${DELIVERY_MAX_ATTEMPTS:3}
      processing-timeout: ${DELIVERY_PROCESSING_TIMEOUT:10m}
      retry-delays:
        - 1m
        - 5m
```

---

# 22. Delivery Retry Policy

Recommended:

```text
failed SMTP attempt 1 -> RETRY_SCHEDULED, +1 minute
failed SMTP attempt 2 -> RETRY_SCHEDULED, +5 minutes
failed SMTP attempt 3 -> PERMANENT_FAILURE
```

SMTP retries are separate from Rabbit listener retries.

---

# 23. Notification State Service

Create a separate transaction-focused state component.

```java
public interface NotificationStateService {

    boolean claim(Long id);

    Notification getRequired(Long id);

    void markSent(Long id);

    void recordFailure(
            Long id,
            String errorCode,
            String errorMessage
    );

    int recoverStuck();

    void manualRetry(Long id);
}
```

Responsibilities:

- very short DB transactions;
- no SMTP/network I/O;
- state transition validation;
- retry backoff calculation;
- stuck-row recovery.

This avoids the common Spring self-invocation problem with internal `@Transactional` methods.

---

# 24. Delivery Service

Interface:

```java
public interface NotificationDeliveryService {
    void deliver(Long notificationId);
    void recoverStuckNotifications();
}
```

Algorithm:

```text
1. stateService.claim(id)
2. if false -> another worker got it -> return
3. reload notification
4. SMTP send (NO DB transaction held)
5. if success -> stateService.markSent(id)
6. if failure -> stateService.recordFailure(id, code, message)
```

Example:

```java
@Service
public class NotificationDeliveryServiceImpl
        implements NotificationDeliveryService {

    private final NotificationStateService stateService;
    private final EmailSenderService emailSender;

    @Override
    public void deliver(Long id) {

        if (!stateService.claim(id)) {
            return;
        }

        Notification n = stateService.getRequired(id);

        DeliveryResult result = emailSender.sendHtml(
            new EmailAddress(n.getRecipientEmail()),
            n.getRecipientName(),
            n.getSubject(),
            n.getHtmlBody()
        );

        if (result.success()) {
            stateService.markSent(id);
        } else {
            stateService.recordFailure(
                id,
                result.errorCode(),
                result.errorMessage()
            );
        }
    }

    @Override
    public void recoverStuckNotifications() {
        stateService.recoverStuck();
    }
}
```

---

# 25. Delivery Scheduler

```java
@Component
public class NotificationDeliveryScheduler {

    private final NotificationRepository repository;
    private final NotificationDeliveryService deliveryService;
    private final Clock clock;

    @Scheduled(
        fixedDelayString =
            "${app.notification.delivery.scheduler-delay-ms:5000}"
    )
    public void deliverDueNotifications() {

        List<Notification> due =
            repository
                .findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                    List.of(
                        NotificationStatus.PENDING,
                        NotificationStatus.RETRY_SCHEDULED
                    ),
                    clock.instant()
                );

        for (Notification notification : due) {
            deliveryService.deliver(notification.getId());
        }
    }
}
```

Never use an unbounded query.

---

# 26. Stuck Processing Recovery

Problem:

```text
worker claims job -> PROCESSING -> process crashes
```

A second scheduler runs every minute:

```java
@Component
public class StuckNotificationRecoveryScheduler {

    private final NotificationDeliveryService deliveryService;

    @Scheduled(fixedDelay = 60000)
    public void recover() {
        deliveryService.recoverStuckNotifications();
    }
}
```

State service finds:

```text
status = PROCESSING
lastAttemptAt < now - processingTimeout
```

and resets:

```text
status = RETRY_SCHEDULED
nextAttemptAt = now
lastErrorCode = RECOVERED_STUCK_PROCESSING
```

Do not increment failed SMTP attempts when recovering a crashed worker because SMTP outcome is unknown.

---

# 27. Query Service and REST API

Interface:

```java
public interface NotificationQueryService {

    Page<NotificationResponse> getForUser(
            Long userId,
            Pageable pageable
    );

    Page<NotificationResponse> getAll(
            NotificationStatus status,
            Pageable pageable
    );

    NotificationResponse getById(Long id);

    NotificationResponse retry(Long id);
}
```

Endpoints:

```http
GET  /api/v1/notifications/me
GET  /api/v1/notifications/{id}

GET  /api/v1/admin/notifications
POST /api/v1/admin/notifications/{id}/retry
```

Suggested access:

| Endpoint | Permission |
|---|---|
| `GET /notifications/me` | authenticated user |
| `GET /notifications/{id}` | owner/admin |
| admin list | ADMIN or RECEPTIONIST |
| manual retry | ADMIN |

The controller must not contain delivery business logic.

---

# 28. Response DTO

```java
public record NotificationResponse(
        Long id,
        UUID eventId,
        Long appointmentId,
        Long recipientUserId,
        RecipientType recipientType,
        String recipientName,
        String recipientEmail,
        NotificationType type,
        NotificationChannel channel,
        NotificationStatus status,
        String subject,
        int attemptCount,
        Instant nextAttemptAt,
        Instant lastAttemptAt,
        Instant sentAt,
        String lastErrorCode,
        String lastErrorMessage,
        Instant createdAt,
        Instant updatedAt
) {}
```

Do not include full HTML body in paginated list responses.

A detailed response may include it if the UI requires it.

---

# 29. Mapper

```java
@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
            n.getId(),
            n.getEventId(),
            n.getAppointmentId(),
            n.getRecipientUserId(),
            n.getRecipientType(),
            n.getRecipientName(),
            n.getRecipientEmail(),
            n.getType(),
            n.getChannel(),
            n.getStatus(),
            n.getSubject(),
            n.getAttemptCount(),
            n.getNextAttemptAt(),
            n.getLastAttemptAt(),
            n.getSentAt(),
            n.getLastErrorCode(),
            n.getLastErrorMessage(),
            n.getCreatedAt(),
            n.getUpdatedAt()
        );
    }
}
```

Do not expose entities directly.

---

# 30. Manual Retry

Only `PERMANENT_FAILURE` should normally be manually retried.

On manual retry:

```text
status = RETRY_SCHEDULED
nextAttemptAt = now
lastErrorCode = null
lastErrorMessage = null
```

For this course project, reset `attemptCount=0` so the manually retried notification receives a fresh automatic retry budget.

Keep this behavior inside the entity/state service, not the controller.

---

# 31. Exceptions

Create:

```text
InvalidNotificationEventException
TemplateNotFoundException
NotificationNotFoundException
NotificationNotRetryableException
EmailDeliveryException (optional if result-based sender remains)
```

REST errors use `@RestControllerAdvice`.

Example:

```json
{
  "timestamp": "2026-10-10T10:30:00Z",
  "status": 404,
  "error": "NOTIFICATION_NOT_FOUND",
  "message": "Notification 123 was not found",
  "path": "/api/v1/admin/notifications/123"
}
```

Never return stack traces.

---

# 32. Full `application.yml`

```yaml
server:
  port: ${SERVER_PORT:8085}

spring:
  application:
    name: notification-service

  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/notification_db}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    hikari:
      maximum-pool-size: ${DB_POOL_SIZE:10}
      minimum-idle: 2

  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        jdbc:
          time_zone: UTC

  flyway:
    enabled: true

  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:guest}
    password: ${RABBITMQ_PASSWORD:guest}
    listener:
      simple:
        acknowledge-mode: auto
        default-requeue-rejected: false
        prefetch: 20
        concurrency: 1
        max-concurrency: 4
        retry:
          enabled: true
          max-attempts: 3
          initial-interval: 1s
          multiplier: 2
          max-interval: 10s

  mail:
    host: ${MAIL_HOST:localhost}
    port: ${MAIL_PORT:1025}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties:
      "[mail.smtp.auth]": ${MAIL_SMTP_AUTH:false}
      "[mail.smtp.starttls.enable]": ${MAIL_STARTTLS:false}
      "[mail.smtp.connectiontimeout]": 5000
      "[mail.smtp.timeout]": 5000
      "[mail.smtp.writetimeout]": 5000

app:
  notification:
    timezone: ${NOTIFICATION_TIMEZONE:Asia/Ho_Chi_Minh}

    mail:
      from-address: ${MAIL_FROM_ADDRESS:no-reply@clinic.local}
      from-name: ${MAIL_FROM_NAME:MSS Clinic}

    delivery:
      scheduler-delay-ms: ${DELIVERY_SCHEDULER_DELAY_MS:5000}
      batch-size: ${DELIVERY_BATCH_SIZE:100}
      max-attempts: ${DELIVERY_MAX_ATTEMPTS:3}
      processing-timeout: ${DELIVERY_PROCESSING_TIMEOUT:10m}
      retry-delays:
        - 1m
        - 5m

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus

  endpoint:
    health:
      show-details: when_authorized

logging:
  level:
    root: INFO
    com.clinic.notification: INFO

eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka/}
```

Remove Eureka config/dependency if your team does not use Eureka.

---

# 33. Environment Variables

Document:

```text
SERVER_PORT

DB_URL
DB_USERNAME
DB_PASSWORD
DB_POOL_SIZE

RABBITMQ_HOST
RABBITMQ_PORT
RABBITMQ_USERNAME
RABBITMQ_PASSWORD

MAIL_HOST
MAIL_PORT
MAIL_USERNAME
MAIL_PASSWORD
MAIL_SMTP_AUTH
MAIL_STARTTLS
MAIL_FROM_ADDRESS
MAIL_FROM_NAME

NOTIFICATION_TIMEZONE

DELIVERY_SCHEDULER_DELAY_MS
DELIVERY_BATCH_SIZE
DELIVERY_MAX_ATTEMPTS
DELIVERY_PROCESSING_TIMEOUT

EUREKA_URL
```

---

# 34. Dockerfile

```dockerfile
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/notification-service.jar app.jar

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

---

# 35. Docker Compose for Local Infrastructure

```yaml
services:

  notification-db:
    image: postgres:17
    environment:
      POSTGRES_DB: notification_db
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5435:5432"
    volumes:
      - notification_db_data:/var/lib/postgresql/data

  rabbitmq:
    image: rabbitmq:4-management
    ports:
      - "5672:5672"
      - "15672:15672"
    environment:
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest

  mailhog:
    image: mailhog/mailhog
    ports:
      - "1025:1025"
      - "8025:8025"

volumes:
  notification_db_data:
```

If the root project already provides shared containers, reuse them.

---

# 36. Logging

Include:

```text
eventId
notificationId
appointmentId
recipientUserId
notificationType
```

Examples:

```text
INFO event_ingested eventId=... appointmentId=...
INFO notification_created notificationId=... eventId=...
INFO delivery_started notificationId=...
INFO delivery_sent notificationId=...
WARN delivery_retry notificationId=... attempt=1 nextAttemptAt=...
ERROR delivery_permanent_failure notificationId=... attempt=3
WARN duplicate_event_ignored eventId=...
ERROR invalid_event eventId=...
```

Never log:

- passwords;
- SMTP credentials;
- JWTs;
- full medical details;
- entire email bodies by default.

---

# 37. Observability

Expose:

```http
GET /actuator/health
```

Recommended counters:

```text
notification.events.ingested
notification.events.duplicate
notification.delivery.sent
notification.delivery.retry
notification.delivery.permanent_failure
notification.delivery.recovered_stuck
```

Allowed metric tags:

```text
notificationType
recipientType
```

Never use IDs/email addresses as metric tags.

---

# 38. Idempotency

RabbitMQ delivery is at-least-once.

Publishing the same `eventId` twice must result in:

```text
1 ProcessedEvent
2 Notifications maximum:
  patient + doctor
```

Database constraints are the last protection against races.

If concurrent duplicate ingestion causes a uniqueness violation specifically on `processed_events.event_id`, treat that event as already processed.

Do not swallow every `DataIntegrityViolationException`.

---

# 39. Email Delivery Guarantee

Ordinary SMTP cannot provide perfect exactly-once delivery.

Edge case:

```text
SMTP accepts message
service crashes before marking SENT
retry sends again
```

Document guarantee:

> Event ingestion and notification-job creation are idempotent. Physical email delivery is at-least-once under crash-edge conditions.

---

# 40. Reminder Ownership

The Notification Service must not search appointments to decide reminders.

Use:

```text
Appointment Service scheduler
        |
        v
find upcoming appointments
        |
        v
publish APPOINTMENT_REMINDER
        |
        v
RabbitMQ
        |
        v
Notification Service
```

---

# 41. Reschedule Contract Extension

If reschedule emails must show old vs new time, extend the event with:

```java
public record PreviousAppointmentSlotDto(
    LocalDate appointmentDate,
    LocalTime startTime
) {}
```

Then add:

```java
PreviousAppointmentSlotDto previousSlot
```

to the event.

Do not encode old values in generic strings.

---

# 42. Template Variables

## Confirmation

```text
recipientName
patientName
doctorName
appointmentDate
appointmentTime
appointmentEndTime
clinicName
roomName
reason
```

## Cancellation

```text
recipientName
patientName
doctorName
appointmentDate
appointmentTime
clinicName
roomName
cancellationReason
```

## Reschedule

```text
recipientName
patientName
doctorName
previousAppointmentDate
previousAppointmentTime
appointmentDate
appointmentTime
clinicName
roomName
```

## Reminder

```text
recipientName
patientName
doctorName
appointmentDate
appointmentTime
clinicName
roomName
```

---

# 43. Security

Use the same security architecture as the rest of the project.

Suggested policy:

| Endpoint | Role |
|---|---|
| `/actuator/health` | infrastructure/public as configured |
| `/api/v1/notifications/me` | authenticated |
| `/api/v1/notifications/{id}` | owner/admin |
| `/api/v1/admin/notifications/**` | ADMIN / RECEPTIONIST |
| manual retry | ADMIN |

RabbitMQ and MailHog remain internal.

---

# 44. Testing

## Unit tests

### Template service

- selects correct type/recipient template;
- renders variables;
- missing template throws.

### Ingestion service

- created event makes patient + doctor notifications;
- correct event mapping;
- duplicate event creates nothing new;
- malformed event throws;
- processed event and notifications share one transaction.

### Delivery service

- failed claim means no send;
- SMTP success -> `SENT`;
- first failure -> retry;
- second failure -> retry;
- max failure -> permanent failure;
- correct retry delay;
- manual retry resets state according to documented rule.

### Query service

- not found;
- ownership/role behavior;
- paging;
- retry restrictions.

## Repository tests

Prefer PostgreSQL/Testcontainers when possible.

Test:

- unique event/recipient/type/channel;
- processed event PK;
- due query;
- claim update;
- recipient pagination.

## Integration tests

Ideal:

```text
Testcontainers PostgreSQL
Testcontainers RabbitMQ
mock JavaMailSender or GreenMail
```

Main integration flow:

```text
publish appointment.created
        |
        v
listener
        |
        v
2 jobs persisted
        |
        v
delivery
        |
        v
2 email sends
        |
        v
2 SENT rows
```

Duplicate test:

```text
publish same event twice

processed_events count = 1
notifications count = 2
```

DLQ test:

```text
publish malformed event
wait for retries
DLQ count = 1
```

---

# 45. Manual QA / Demonstration

## Successful booking

1. start PostgreSQL;
2. start RabbitMQ;
3. start MailHog;
4. start Notification Service;
5. book appointment;
6. inspect RabbitMQ;
7. inspect DB notification rows;
8. open MailHog;
9. show patient email;
10. show doctor email;
11. show `SENT`.

## Idempotency

1. republish identical eventId;
2. confirm no extra notifications.

## SMTP failure

1. stop MailHog;
2. publish valid event;
3. show `RETRY_SCHEDULED`;
4. restart MailHog;
5. show later `SENT`.

## DLQ

1. publish invalid event;
2. show retries;
3. show message in `notification.appointment.dlq`.

---

# 46. Database Ownership

Strict rule:

```text
notification-service -> notification_db only
```

No SQL access to:

```text
auth_db
patient_db
doctor_db
appointment_db
```

IDs from other services are logical references, not DB foreign keys.

---

# 47. Time Handling

Persist system timestamps as:

```text
Instant
PostgreSQL TIMESTAMPTZ
UTC
```

Use `Clock.systemUTC()`.

Use configured `Asia/Ho_Chi_Minh` only for display formatting where necessary.

Never depend on host timezone implicitly.

---

# 48. Error Classification

Possible codes:

```text
SMTP_CONNECTION_ERROR
SMTP_AUTH_ERROR
INVALID_RECIPIENT
TEMPLATE_ERROR
UNKNOWN_EMAIL_ERROR
RECOVERED_STUCK_PROCESSING
```

V1 may retry all SMTP failures until `maxAttempts`.

Optional improvement:

- invalid recipient -> permanent immediately;
- connection/timeouts -> retry.

---

# 49. Transaction Boundaries

## Ingestion transaction

Includes:

```text
notification rows
processed_event row
```

Does not include SMTP.

## Delivery transactions

Use short transactions for:

```text
claim
mark SENT
schedule retry
mark permanent failure
recover stuck
manual retry
```

SMTP/network I/O occurs outside DB transactions.

---

# 50. Concurrency Requirements

Must remain safe under:

```text
multiple listener threads
multiple notification service instances
multiple delivery schedulers
```

Protection:

1. `processed_events.event_id` primary key;
2. notification unique constraint;
3. atomic claim update;
4. optimistic `@Version`;
5. stuck processing recovery.

---

# 51. Startup Behavior

Expected order:

1. datasource available;
2. Flyway migrations run;
3. JPA validates schema;
4. Rabbit beans declare exchange/queue/bindings;
5. listener starts;
6. schedulers start.

SMTP being down must not prevent service startup.

---

# 52. Agent Code-Quality Rules

The implementation agent MUST:

- use constructor injection;
- avoid field injection;
- keep controllers thin;
- keep Rabbit listeners thin;
- use DTOs at service boundaries;
- not expose entities directly;
- use `Clock`;
- use Flyway;
- use `ddl-auto=validate`;
- never commit secrets;
- persist enum names, not ordinals;
- keep SMTP outside the Rabbit listener;
- keep SMTP outside DB transactions;
- write idempotency tests;
- write retry-state tests;
- include migrations;
- include Docker/local setup;
- update README.

---

# 53. Implementation Order

## Phase 1 — skeleton

1. Spring Boot project;
2. dependencies;
3. application class;
4. config;
5. PostgreSQL;
6. Flyway.

## Phase 2 — persistence

7. enums;
8. entities;
9. repositories;
10. repository tests.

## Phase 3 — templates

11. seed templates;
12. Thymeleaf string resolver;
13. template service;
14. template tests.

## Phase 4 — Rabbit ingestion

15. event DTOs;
16. topology;
17. JSON converter;
18. listener;
19. ingestion service;
20. idempotency tests;
21. DLQ test.

## Phase 5 — email delivery

22. value objects;
23. email sender;
24. MailHog;
25. state service;
26. delivery service;
27. delivery scheduler;
28. stuck recovery;
29. retry tests.

## Phase 6 — REST

30. DTOs;
31. mapper;
32. query service;
33. controller;
34. exception handler;
35. security integration.

## Phase 7 — operations

36. Actuator;
37. logging/metrics;
38. Dockerfile;
39. compose integration;
40. README;
41. end-to-end demo.

---

# 54. Acceptance Criteria

## Functional

- [ ] consumes `appointment.created`;
- [ ] consumes `appointment.cancelled`;
- [ ] consumes `appointment.rescheduled`;
- [ ] consumes `appointment.reminder`;
- [ ] creates patient notification;
- [ ] creates doctor notification;
- [ ] renders correct template;
- [ ] sends HTML email to MailHog;
- [ ] stores notification history;
- [ ] persists `SENT`;
- [ ] retries SMTP failure;
- [ ] stops after max attempts;
- [ ] exposes permanent failure;
- [ ] manual retry works;
- [ ] duplicate event creates no duplicate jobs;
- [ ] malformed event reaches DLQ;
- [ ] stuck `PROCESSING` jobs recover.

## Architecture

- [ ] separate notification database;
- [ ] no cross-service DB access;
- [ ] no Patient/Doctor REST calls to compose email;
- [ ] listener does not send SMTP;
- [ ] ingestion is idempotent;
- [ ] SMTP does not occur inside DB transaction.

## Quality

- [ ] Flyway;
- [ ] constructor injection;
- [ ] unit tests;
- [ ] integration test;
- [ ] duplicate event test;
- [ ] retry tests;
- [ ] health endpoint;
- [ ] Docker support;
- [ ] no committed secrets.

---

# 55. Final Architecture

```text
┌──────────────────────┐
│ Appointment Service  │
└──────────┬───────────┘
           │ lifecycle events
           ▼
┌──────────────────────┐
│      RabbitMQ        │
│    clinic.events     │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────────────────────────────┐
│           NOTIFICATION SERVICE               │
│                                              │
│  AppointmentEventListener                    │
│           │                                  │
│           ▼                                  │
│  NotificationIngestionService                │
│      │                     │                 │
│      ▼                     ▼                 │
│ TemplateService       ProcessedEvent         │
│      │                idempotency            │
│      ▼                     │                 │
│ Notification jobs ─────────┘                 │
│      │                                       │
│      ▼                                       │
│ notification_db                             │
│      │                                       │
│      ▼                                       │
│ DeliveryScheduler                            │
│      │                                       │
│      ▼                                       │
│ DeliveryService                              │
│      │                                       │
│      ▼                                       │
│ EmailSenderService                           │
└──────┼───────────────────────────────────────┘
       │
       ▼
┌──────────────────────┐
│   MailHog / SMTP     │
└──────────┬───────────┘
           │
      ┌────┴────┐
      ▼         ▼
   Patient    Doctor
```

Rabbit ingestion failure:

```text
notification.appointment.q
        |
      retry x3
        |
        v
    clinic.dlx
        |
        v
notification.appointment.dlq
```

SMTP failure:

```text
Notification
    |
    +--> RETRY_SCHEDULED
    |
    +--> RETRY_SCHEDULED
    |
    +--> PERMANENT_FAILURE
```

---

# 56. Agent Execution Contract

Treat these as non-negotiable unless the project owner changes them:

1. Service is event-driven.
2. RabbitMQ is the broker.
3. Exchange: `clinic.events`.
4. Queue: `notification.appointment.q`.
5. Routing keys:
   - `appointment.created`
   - `appointment.cancelled`
   - `appointment.rescheduled`
   - `appointment.reminder`
6. Ingestion and SMTP delivery are separate.
7. `ProcessedEvent` provides event idempotency.
8. `Notification` is durable delivery state.
9. V1 channel is EMAIL only.
10. MailHog is default local mail sink.
11. PostgreSQL is service storage.
12. Flyway owns schema.
13. No foreign service DB access.
14. Contact and appointment snapshots arrive in events.
15. Both patient and doctor are notified.
16. SMTP retries are persisted and application-managed.
17. Rabbit listener retry is for ingestion only.
18. Exhausted ingestion failures go to DLQ.
19. REST APIs are query/admin functions, not the delivery path.
20. Do not add Kafka, Redis, Elasticsearch, or extra infrastructure without a requirement.

---

# 57. Optional V2 — Do Not Build Before V1 Is Complete

Possible future additions:

- SMS;
- push notifications;
- notification preferences;
- multilingual templates;
- template admin UI;
- attachments;
- provider delivery/bounce webhooks;
- distributed rate limiting;
- digest notifications;
- quiet hours;
- richer audit trail;
- shared outbox/inbox framework.

These are intentionally not part of V1.

---

# 58. Local Run Checklist

```text
1. Start notification PostgreSQL
2. Start RabbitMQ
3. Start MailHog
4. Start Eureka if required
5. Start notification-service
6. Check /actuator/health
7. Check queue notification.appointment.q
8. Check DLQ notification.appointment.dlq
9. Publish valid appointment.created JSON
10. Verify 2 Notification rows
11. Verify 2 MailHog emails
12. Verify rows become SENT
13. Publish same eventId again
14. Verify row counts do not increase
```

---

# 59. Framework Notes

- Spring Boot supports RabbitMQ through `spring-boot-starter-amqp`.
- `@RabbitListener` is the standard listener mechanism.
- Listener retries must be explicitly configured.
- Exhausted/rejected messages can be routed to a dead-letter exchange/queue.
- Spring Boot auto-configures `JavaMailSender` when the mail starter and `spring.mail.host` are present.
- Configure SMTP connection/read/write timeouts.
- Spring AMQP 4.x uses the Jackson 3-based `JacksonJsonMessageConverter`.
- If the project runs an older compatible Spring generation, adjust framework-specific class names but preserve this architecture and these service contracts.

---

**End of specification.**
