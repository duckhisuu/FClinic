# 01 — Nghiệp vụ và ranh giới Appointment

## Quy tắc nguồn

Shared target trong Report được dùng làm baseline theo yêu cầu dự án. Patient là UUID string lowercase; Appointment/Doctor/slot là số nguyên JSON từ 1 đến 9007199254740991. Không chuyển Patient sang Long, không chọn id1 hoặc snapshot giả khi provider lỗi.

Create chỉ có patientId, doctorId, slotId, reasonForVisit tùy chọn. Patient phải ACTIVE; Doctor phải active; published slot thuộc đúng bác sĩ, bắt đầu trong tương lai, đúng giờ và dài 60 phút. Ngày/giờ nghiệp vụ dùng Asia/Saigon, timestamp dùng UTC/Z. Tên, điện thoại, chuyên khoa và phí do server lấy từ provider.

Create → CONFIRMED. Hủy → CANCELLED. Đổi lịch vẫn CONFIRMED, tăng version; giữ bệnh nhân/bác sĩ/tên/điện thoại/chuyên khoa/phí đã đặt. Chỉ cho hủy/đổi trước giờ bắt đầu và khi version đúng. Không thêm cutoff 24h, phí phạt hoặc chuyển bác sĩ. CANCELLED/COMPLETED/NO_SHOW không nhận hủy/đổi. Không tự hoàn thành lịch khi Patient tạo hồ sơ khám. Workflow hoàn thành/vắng khám chưa có trong Report nên không tự tạo API.

## Capacity, transaction và replay

- Unique partial PostgreSQL theo doctor/date/start khi CONFIRMED quyết định occupancy; hai slotId cùng giờ vẫn tranh cùng chỗ. Availability chỉ là tham khảo trước khi commit.
- Patient/Doctor/published slot được kiểm tra qua HTTP trước local write. Không có cross-DB FK/join, HTTP trong transaction ghi hoặc hứa transaction phân tán.
- Appointment + history + operation_results + outbox intent cùng commit/rollback. Đổi sang giờ đã có người đặt không giải phóng giờ cũ.
- Idempotency scope là subject của Principal hiện có + operation + UUID key. Không có Principal thì dùng namespace anonymous; không tự khẳng định có danh tính/role đã xác minh. Resource path nằm trong hash nên cùng key đổi Appointment cũng xung đột.
- Body được trim/chuẩn hóa null và hash SHA-256 cùng method/path. Cùng nội dung trả nguyên HTTP status/body đã commit, trước kiểm tra version cũ. Cùng key khác nội dung →409; key đang chạy →409 OPERATION_IN_PROGRESS, không chờ một transaction dài.

## Schema DB mới

Flyway V1 tạo appointments, appointment_history, operation_results, outbox_events, reminder_publications. History/result/outbox giữ FK cục bộ RESTRICT; không hard-delete lịch sử. Intent/payload không có nội dung khám. Payload cuối dùng text để giữ nguyên byte qua retry.

Đây là fresh-v2 migration, không convert dữ liệu Long/PENDING cũ hoặc bật baseline-on-migrate. Migration/cutover dữ liệu cũ là công việc riêng. Không thêm patient/date/hour uniqueness khi Report chưa chọn chính sách này.

## Source để đọc

Bắt đầu ở [mapping use case](08_USE_CASE_ARCHITECTURE.md). Business booking giữ file application/usecase/AppointmentApplicationService.java, các use case khác tách riêng trong cùng package. Aggregate ở domain/aggregate/Appointment.java. JPA persistence ở infrastructure/adapter/JpaAppointmentRepositoryAdapter.java; history, operation result và lease SQL ở các adapter JDBC chuyên biệt. HTTP providers ở HttpAppointmentReferenceAdapter; publish/reminder đi qua input/output ports và OutboxRelayJob. API giữ api/controller, api/dto, api/exception. React giữ ClinicAppointmentFE/src/appointment.


Các query patientId/doctorId là bộ lọc nghiệp vụ, không phải authorization. History hiện ghi SYSTEM kèm subject nếu container có Principal; không dựng ROLE_PATIENT/STAFF từ header hoặc số điện thoại. Security không được triển khai thêm trong thay đổi này.
