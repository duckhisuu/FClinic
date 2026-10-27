# 02 — Provider / consumer contracts

Tất cả HTTP thành công dùng đúng một wrapper `{status,message,data}`. Lỗi nghiệp vụ dùng `{status,message,data:null,code,fieldErrors}`. Không chấp nhận raw/wrapped tùy ý hoặc aliases để chữa mismatch. Timeout đề xuất Report đã áp dụng: connect1s, từng response tối đa2s, tổng chain validation5s, body tối đa256KiB; không tự retry POST/PUT hoặc retry nghiệp vụ.

| Boundary | Provider → consumer | Contract cần giữ |
| --- | --- | --- |
| B-02 | Patient → Appointment | GET /api/v2/patients/{patientId}; full Patient14 fields, UUID/code/ACTIVE; [schema](contracts/patient-response.schema.json), [mẫu](fixtures/patient-response.json) |
| B-03 profile | Doctor → Appointment | GET /api/v1/doctors/{doctorId}; đủ Doctor11 fields, id đúng/active/phí hợp lệ; [schema](contracts/doctor-response.schema.json) |
| B-03 slot | Doctor → Appointment | GET /internal/v1/doctors/{doctorId}/slots/{slotId}; đúng PublishedSlot, bắt buộc published boolean; [schema](contracts/published-slot-response.schema.json), [mẫu](fixtures/slot-response.json) |
| B-03 offer | Doctor → Appointment | GET /api/v1/doctors/{doctorId}/slots?date=YYYY-MM-DD; [schema](contracts/offered-slots-response.schema.json), [mẫu](fixtures/doctor-slots-response.json). isBooked không quyết định capacity |
| B-05 | Appointment → Patient | GET /api/v2/appointments/{id}/medical-reference; data đúng id,patientId,doctorId,status; extension D-19 được mô tả trong OpenAPI nghiệp vụ |
| Reminder validation | Appointment → Notification | GET /api/v2/appointments/{id}/reminder-reference; data có trạng thái/version/slot/date/start/end/timezone để đối chiếu original event; không clinical fields |
| B-09 | Appointment → React/Gateway | API /api/v2/appointments, cancel/reschedule/availability/history, [OpenAPI](contracts/appointment-business.openapi.json) |
| Lifecycle events | Appointment → Notification | clinic.events + fclinic.appointment.notifications; bốn schemaVersion2 events; xem04 |
| D-08 | Nguồn recipient tin cậy → Appointment | Report chưa cho route. Adapter URL configurable POST patientId/doctorId, data đúng2 recipient; [đề xuất schema](contracts/recipient-resolution.schema.json). Phải thống nhất với provider, không nói endpoint đã có |

## Trường hợp provider lỗi

Patient404 → PATIENT_NOT_FOUND404, inactive → PATIENT_INACTIVE409. Doctor404 → DOCTOR_NOT_FOUND404, inactive → DOCTOR_INACTIVE409. Published=false, sai bác sĩ, giờ không đúng chuẩn hoặc đã bắt đầu → SLOT_NOT_BOOKABLE409. Slot cùng doctor/date/hour đã được giữ → SLOT_ALREADY_BOOKED409.

Report D-18 biết provider trả404 cho slot thiếu/sai bác sĩ nhưng chưa đặt tên code. Ở consumer Appointment hiện quy đổi việc không có published offer sang SLOT_NOT_BOOKABLE409; đây là lựa chọn xử lý phía Appointment, không ép Doctor dùng code này cho404. Hai bên cần chốt exact missing-slot code trước contract signoff. Response sai schema, thiếu dữ liệu, credential bị từ chối hoặc timeout → DEPENDENCY_UNAVAILABLE503, không ghi booking mới.

## Security ngoài phạm vi thay đổi

Report yêu cầu original USER bearer, trusted mapping/self/scoped STAFF, assigned DOCTOR reads và SERVICE least privilege. Nhóm security/Auth giữ trách nhiệm này; module không thêm verifier/filter/role/scope. APPOINTMENT_SERVICE_TOKEN chỉ nhận credential đã được cơ chế hiện có cấp để gọi provider. Không chuyển bearer của trình duyệt thành machine credential và không thêm endpoint trong service của nhóm khác.

Patient có tài liệu consumer profile lịch sử dùng đường dẫn/header/auth khác. Không sửa Patient để ép chạy. Nhóm tích hợp đối chiếu profile đó với contract v2 ở đây rồi thống nhất route/projection/credential; chưa có chứng cứ real B-05 integration của implementation này.
