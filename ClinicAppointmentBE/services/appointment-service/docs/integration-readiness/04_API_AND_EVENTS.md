# 04 — API và events

[OpenAPI nghiệp vụ](contracts/appointment-business.openapi.json) giữ request/Appointment/Availability/Error schemas của [Report](contracts/report/appointment.openapi.json), bổ sung read models thuộc Appointment (D-19). Không bổ sung API login, outcome COMPLETED/NO_SHOW hoặc API sửa Doctor/Patient.

## Mutation

POST /api/v2/appointments: patientId UUID, doctorId/slotId số nguyên JSON, reasonForVisit optional/null. Thành công201, data full Appointment với snapshot server/version0/CONFIRMED.

PUT /{id}/cancel: version bắt buộc, reason optional/null tối đa500. PUT /{id}/reschedule: thêm slotId bắt buộc. Thành công200, version+1. Mỗi mutation có Idempotency-Key lowercase UUID và X-Request-Id optional UUID. Cùng key/body phải giữ nguyên qua mất kết nối, không đổi key chỉ vì timeout.

Xem các JSON thật trong [fixtures](fixtures/booking-request.json); ngày ví dụ là ngày cố định trong Report, khi chạy thật chọn slot tương lai từ provider, không dùng fixture để seed production.

## Read models thuộc Appointment

List nhận patientId/doctorId/date/status/page/size (default0/20,max100), trả content/page/size/totalElements/totalPages, sort ngày/giờ/id giảm dần. Đây là filter, không thay quyền truy cập.

History /{id}/history trả array id/action/version/oldDate/oldStartTime/newDate/newStartTime/reason/actorRole/createdAt. Projection medical /{id}/medical-reference chỉ id/patientId/doctorId/status. Projection reminder /{id}/reminder-reference thêm version/slotId/appointmentDate/startTime/endTime/timezone. Chúng không có lý do khám/phone. Endpoint detail full có clinical reason, không dùng full detail cho Notification.

Không có upsert/by-phone tạo hồ sơ, monitor outbox công khai hoặc fallback thành công giả. V1 đã retire410 trên Appointment direct port; Gateway owner nối route mới riêng.

## Event transport

| EventType | Routing key | Schema |
| --- | --- | --- |
| APPOINTMENT_CREATED | appointment.created | [created](contracts/events/created.event.schema.json) |
| APPOINTMENT_CANCELLED | appointment.cancelled | [cancelled](contracts/events/cancelled.event.schema.json) |
| APPOINTMENT_RESCHEDULED | appointment.rescheduled | [rescheduled](contracts/events/rescheduled.event.schema.json) |
| APPOINTMENT_REMINDER | appointment.reminder | [reminder](contracts/events/reminder.event.schema.json) |

Exchange topic durable clinic.events, queue durable fclinic.appointment.notifications. Content-Type application/json UTF-8, messageId bằng eventId; raw JSON object, không lồng thành JSON string hoặc phụ thuộc Java type header. Common fields: eventId UUID, eventType, schemaVersion2, occurredAt UTC, source appointment-service, appointmentId, aggregateVersion, bookingCode, patient/doctor snapshots, slot/schedule, timezone Asia/Saigon và recipients đúng2 PATIENT/DOCTOR. Reschedule thêm previousSlotId/date/start/end; reminder thêm reminderDueAt UTC.

Không có reasonForVisit, reason hủy/đổi, diagnosis, treatment, notes, fee hoặc clinical/audit body trong intent/event. Recipient email null được giữ null, không đoán email. Lấy recipient sau local commit; final payload đóng băng và giữ nguyên byte/eventId qua retry.

Outbox chỉ PUBLISHED sau broker ACK và không có mandatory return. Broker ACK không có nghĩa đã gửi email. Delivery at-least-once, consumer bắt buộc dedup. Reminder unique appointment/version/due; worker Appointment suppress stale status/version/schedule, Notification cần revalidate thêm tại thời điểm gửi vì có khoảng trễ sau publish.
