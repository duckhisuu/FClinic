# 06 — Các điểm cần chốt khi ghép hệ thống

Nghiệp vụ Appointment triển khai theo Report. Các điểm dưới đây là thông tin bàn giao, không phải yêu cầu sửa service khác trong mỗi lần sửa Appointment.

| Mục Report | Appointment hiện có | Việc cần từ nhóm liên quan |
| --- | --- | --- |
| D-02/D-03 | 60phút, published đúng doctor, same-doctor/future-start và DB unique | Doctor cung cấp published-slot/immutable offers theo schema |
| D-04/05/06 | Không code thêm security; chỉ dùng Principal container nếu có | Security hiện có enforce identity/self/scope trước lookup và replay; cấp credential provider |
| D-08 | Durable pending enrichment, configurable recipient adapter | Chốt exact route và nguồn account/email; schema adapter ở02 là đề xuất rõ ràng |
| D-09 | Configurable positive lead; blank thì chưa schedule | Nghiệp vụ/Notification chọn lead, cadence; không tự mặc định24h |
| D-15 | Fresh UUID schema có migration; không convert Long/PENDING | Plan cutover/backup/restore chung nếu có dữ liệu cũ |
| D-16 | Chỉ doctor/hour capacity | Chưa thêm patient/hour unique hoặc tự cấm patient đặt hai bác sĩ |
| D-17 | Nhận biết terminal states; không có endpoint outcome | Định nghĩa actor/path/body/cutoff/exception trước khi thêm workflow |
| D-18 | Strict PublishedSlot; slot missing404→business non-bookable409 tại consumer | Doctor chốt missing-slot code/provider404, không tự đặt code404 của provider |
| D-19 | Page/history và2minimum read endpoints được mô tả/OpenAPI riêng | Patient/Notification thống nhất projection route, security và actor roles audit |
| D-10/11/22 | Event schema đúng, frozen payload, confirm/retry, reminder version | Notification dedup/retry/template/context persistent/SMTP theo contract, không dùng reasonForVisit |
| Gateway | Không thay route/CORS/config của nhóm khác | Owner nối v2 và headers; chưa gọi đây là real E2E |

## Những gì không được suy ra từ component pass

Provider HTTP dùng doubles không chứng minh Auth/Patient/Doctor thật đã compatible. ACK RabbitMQ không chứng minh email đã gửi. Hai minimum endpoints hiện có không chứng minh Patient profile lịch sử đã sử dụng chúng. Contract JSON cùng hash không chứng minh issuer/audience/service credentials và scope đã được nhóm security cấu hình.

Các bản copy Report giữ nguyên metadata PROPOSED/NOT IMPLEMENTED lịch sử; implementation hiện tại mô tả riêng ở01/04 và OpenAPI nghiệp vụ. Không sửa Report hoặc tài liệu của Patient để biến trạng thái lịch sử thành bằng chứng mới.

## Tiêu chí bàn giao

Appointment chịu trách nhiệm business invariants, local transaction/history/idempotency/outbox, migration mới và UI Appointment. Mỗi provider chịu trách nhiệm triển khai API/event consumer của mình đúng contract đã bàn giao. Nếu cần đổi contract, ghi version/compatibility và cập nhật tài liệu trước khi đổi cả hai phía; không thêm fallback im lặng theo source hiện tại của provider.
