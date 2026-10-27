# 07 — Kiểm chứng cấu trúc use case theo bản pull

Cập nhật 27/10/2026. Bản ghi máy đọc: [verification-summary.json](verification-summary.json). Xem [mapping kiến trúc](08_USE_CASE_ARCHITECTURE.md).

- Giữ đủ **37 file Java gốc**, không còn file gốc bị xóa. Có **10 input ports**, mỗi port có một implementation use case riêng. Application không import API DTO, HTTP/JDBC/JPA, Jackson hoặc RabbitTemplate; domain không phụ thuộc application/infrastructure/Spring/JPA.
- Maven clean/package đã qua với JPA/Hibernate thật. Aggregate và outbox sử dụng các JPA entity/repository/adapter gốc; không phải lớp Jpa đặt tên nhưng gọi lại AppointmentStore cũ. Bộ code gom AppointmentService/AppointmentStore trước đó đã bỏ.
- **53 nhóm assertion nghiệp vụ PASS** sau khi nối lại use case và JPA: minimal DTO/schema, snapshots, tranh chấp cùng giờ qua nhiều slotId, history/result/outbox fault rollback, exact replay, version, cancel/reschedule race, trạng thái, paging, bốn event schema, pending recipient, mandatory return, frozen payload/lease recovery và stale reminder.
- **4 ca published-slot contract PASS** trên artifact cuối: từ chối giờ thiếu giây, fractional time, unpublished; chấp nhận published hourly slot. Các ca lỗi không ghi appointments/history/results/outbox.
- **6 ca Redis/use-case PASS** trên artifact cuối: in-flight duplicate409, JPA persist, replay khi provider lỗi, DB capacity vẫn đúng, rollback khi outbox lỗi và nhả khóa sau lỗi. Redis là container riêng dùng test, đã xóa; không dùng Redis của dự án khác. PostgreSQL guard mặc định được kiểm trong53ca phía trên.
- PostgreSQL/RabbitMQ/Redis là dependency thật trong môi trường cô lập; HTTP providers là doubles tổng hợp. Các con số là nhóm assertion của runner tự viết, không phải số JUnit tests. Runner/test/evidence/env vẫn được gitignore theo yêu cầu.
- 656 file Report/service khác/infra/common được pin trước lượt sửa và không drift. Contract/JSON mẫu copy từ Report giữ SHA-256, các liên kết tài liệu được kiểm tra tồn tại.

Frontend không sửa trong lượt tái cấu trúc BE này. Bằng chứng browser đặt/đọc lại/đổi/hủy/history trước đó được giữ như evidence lịch sử; API DTO được đối chiếu schema lại trong regression hiện tại. Không gọi đây là browser run mới hoặc E2E với provider thật.

Security/Auth/role/scope, Gateway, Notification consumer đang triển khai và SMTP thật chưa được kiểm chứng ở đây. Các yêu cầu tích hợp vẫn nằm trong02/03/06, không sửa code của nhóm khác để đạt component pass.

Source snapshot SHA-256: `a5e6d5c0913ab631be2d82a1ad45c32aa45f4feb7473eb9d29d58cf544e48333`. JAR/hash và phạm vi của từng run ở JSON kèm theo. Các kết quả hiện tại thay cho kết quả của bản AppointmentService gom logic trước đó.
