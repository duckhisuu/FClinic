# UUID — chỉ trong Appointment Service

Phạm vi hiện tại: chuyển ID trong Appointment sang UUID, giữ cấu trúc use case và các file gốc. Các thay đổi UUID/Eureka từng thực hiện ở service khác, common, Gateway, FE và cấu hình đã được phục hồi về bản pull. Các thành viên phụ trách sẽ tự cập nhật phần của mình.

## Các định danh của Appointment

ID appointment, history, operation result, outbox và reminder publication là UUID. Các khóa tham chiếu patientId, doctorId, slotId và recipient userId bên trong Appointment cũng là UUID; Patient vốn đã dùng UUID. Version, tổng bản ghi, pagination, retry count và thời gian chờ giữ kiểu số.

DTO/command/port/domain/JPA/JDBC và API Appointment đã đồng bộ kiểu này. Các so sánh UUID dùng giá trị (`equals`) để không nhầm hai object cùng ID. Quy tắc CONFIRMED, hủy/đổi trước giờ khám, cùng bác sĩ, slot 60 phút, optimistic version, idempotency và unique doctor/hour giữ nguyên.

## Hợp đồng bàn giao cho thành viên khác

- Create: `{patientId, doctorId, slotId, reasonForVisit?}` với các ID là chuỗi UUID.
- Cancel: `{version, reason?}`; reschedule: `{version, slotId, reason?}` với slotId UUID.
- Detail/history/medical-reference/reminder-reference trả ID UUID.
- Appointment cần Patient/Doctor/provider trả UUID theo hợp đồng; không chuyển ID số của implementation hiện tại thành UUID một cách ngầm định khi gọi HTTP.
- Lifecycle event mới dùng `schemaVersion: 3` vì các ID đã đổi kiểu. Consumer cần cập nhật đồng bộ trước khi nhận event UUID; queue/routing/config hiện có được giữ nguyên.

[OpenAPI Appointment UUID](contracts/appointment.openapi.json) và bốn event schema [created](contracts/created.event.schema.json), [cancelled](contracts/cancelled.event.schema.json), [rescheduled](contracts/rescheduled.event.schema.json), [reminder](contracts/reminder.event.schema.json) nằm trong thư mục này. Đây là contract của Appointment để nhóm khác nối tiếp, không phải xác nhận các service khác đã triển khai UUID.

## Patient đọc medical-reference

Nhóm đã thống nhất dùng HTTP nội bộ; phía Patient sẽ cập nhật client của mình. Collection URI của client Patient trỏ tới `http://<appointment-host>:8083/api/v2/appointments` (host/port theo môi trường), rồi gọi `GET /{appointmentId}/medical-reference` bằng UUID. Không thay đổi config hoặc security trong Appointment.

Appointment trả đúng một header `X-FClinic-Reference-Contract: patient-appointment-medical-reference/2` trên response của GET endpoint này, bao gồm 200, 404 và các lỗi do Appointment xử lý. Header được đặt trước validation để lỗi UUID hoặc X-Request-Id vẫn có phiên bản contract. Header chỉ mô tả contract, không xác thực hoặc cấp quyền. Không gắn header này cho API detail, reminder, các method khác hay v1 đã ngừng dùng.

Response 200 giữ envelope `{status, message, data}`; `data` chỉ gồm `{id, patientId, doctorId, status}` với ba ID là chuỗi UUID. Response 404 giữ `code: APPOINTMENT_NOT_FOUND`. Các trạng thái vẫn là `CONFIRMED`, `CANCELLED`, `COMPLETED`, `NO_SHOW`.

**Phía Patient cần đổi version kỳ vọng từ `/1` sang `/2`, dùng UUID cho request/decoder/lưu trữ và cho phép HTTP theo quyết định của nhóm.** `/1` là contract ID số cũ, không được gắn nhãn lên payload UUID. Appointment luôn công bố `/2`, không echo phiên bản do client gửi và không thương lượng hoặc chuyển về ID số. Việc cấp credential theo security hiện có do nhóm phụ trách; thay đổi này không bổ sung cơ chế security.

## Migration cục bộ

V1 được giữ nguyên. V2 chuyển các cột định danh/tham chiếu và dữ liệu liên quan trong **database Appointment**; không kết nối hoặc sửa database Patient/Doctor/Notification. Số cũ được ánh xạ ổn định bằng UUID v3 từ UTF-8 `fclinic:{loại}:{số}`, tương đương `UUID.nameUUIDFromBytes`. Owner service nguồn cần thống nhất cùng mapping khi còn dữ liệu tham chiếu cũ.

Migration giữ dữ liệu lịch sử và các liên kết cục bộ, không đổi version. Cần đối soát các request cũ và drain payload READY trước cutover; migration chặn nếu còn payload READY đã đóng băng. Không tái dùng idempotency key của body ID số cho body UUID. Consumer không được nhận lẫn backlog ID số với UUID mà chưa có kế hoạch tương thích.

Không đổi application.yml, env, POM hoặc config class để ép chạy theo implementation service khác. HTTP transport, credential handling và cơ chế security được trả về bản pull; task này không triển khai Eureka hoặc security mới.

## Kiểm chứng đúng phạm vi

Build chỉ Appointment và các dependency common sẵn có bằng Maven. Bài kiểm tra cục bộ dùng JAR Appointment và PostgreSQL tạm thật; Patient/Doctor là HTTP UUID contract doubles. Không chạy hoặc sửa service khác, không áp migration lên Docker DB đang dùng.

Kết quả hiện hành nằm trong [verification.json](verification.json). Các kết quả 278 test/14 ca liên thông của bản thay đổi rộng trước đây đã được rút khỏi tài liệu hiện hành vì source các service đó đã được phục hồi; không dùng chúng làm bằng chứng cho bản chỉ-Appointment này. Test mới được gitignore; test có sẵn của thành viên khác được phục hồi về bản pull.
