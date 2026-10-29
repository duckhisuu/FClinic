# 22 — Patient / Notification dependency audit R3

**NO DIRECT PATIENT ↔ NOTIFICATION INTEGRATION CONTRACT IN SELECTED IMPLEMENTATION.**

Patient production Java, resources and POM contain no NotificationClientPort, Notification adapter/HTTP client, AMQP dependency, Rabbit listener or notification/event publisher. The four production external boundaries are IdentityVerifierPort, WorkScopePort, DoctorClientPort and AppointmentClientPort; persistence/revision ports are local storage concerns.

Selected Notification integration consumes Appointment-owned APPOINTMENT_BOOKED_V2. Its patientId/name/phone snapshot does not make Patient the event producer or establish a direct Patient integration contract. No Patient-created/updated event consumer was found in Notification source. P-FINAL does not approve an unimplemented direct Patient notification dependency; recipient authority/delivery is separate work.

Classification: direct contract NONE; no Patient Notification adapter is missing within selected scope. No adapter/publisher/consumer/listener/config was added or changed. No N1/N2 profile, broker runtime or Appointment→RabbitMQ→Notification flow was executed. Docker availability is not a Patient acceptance gate.
