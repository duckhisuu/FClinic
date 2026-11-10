# Doctor UUID source verification

**DOCTOR UUID SOURCE: MISSING REMOTE. Real integration: BLOCKED.**

## Provenance

Kiểm tra trực tiếp Git objects đã fetch từ origin/datdt `67158fe64c8df2aae40c1d24e5159498c9cfff57`, không dựa vào tên commit UI. Tất cả năm remote heads đã đối chiếu bằng ls-remote. git grep UUID/uuid trong doctor-service trên bao/main/datdt/nam/appoiment không có kết quả; git log --all -G 'UUID|uuid' -- doctor-service không có kết quả. PR heads được liệt kê thêm trong remote-pull-refs.log; không xác nhận source chưa fetch hoặc chưa được owner push.

History Doctor reachable: b6554cb2dce6a9277b5beccb3a7ce275330d679f (27/10/2026), 3984842433ac72825335cbae87b8b34b43fdd402 (08/10/2026), 238e1dd36cb759df444cf9441ef0012f0d12d2b5 (29/09/2026). Commit datdt mới nhất chỉ bổ sung Doctor UI; không có Doctor UUID commit reachable trên các remote branches/tags hiện có. Không thể chỉ định SHA/branch UUID chưa được publish; owner phải cung cấp ref và SHA chính xác.

Doctor service tree remote và sau merge đều `09841b149f7a639b2e2bb018d250031ee61c6fe6`; mọi kết luận dưới đây áp dụng cả remote và source sau merge.

## Kiểm tra bắt buộc

Các path Java tương đối với ClinicAppointmentBE/services/doctor-service/src/main/java/com/fclinic/doctorservice/.

| Thành phần | Source trực tiếp | Kết quả remote / sau merge |
|---|---|---|
| DoctorResponse.id | api/dto/DoctorResponse.java:4 | Long / Long |
| DoctorController path IDs | api/controller/DoctorController.java:48,63,73,83 | @PathVariable Long id trên detail, slots, schedule, create slot |
| TimeSlotResponse.id/doctorId | api/dto/TimeSlotResponse.java:7–8 | Long / Long cho cả hai |
| Schedule doctorId | api/dto/DoctorScheduleResponse.java:7 | Long / Long |
| Doctor domain | domain/aggregate/Doctor.java:7,86–87 | Long id/getId/setId |
| Slot domain | domain/model/TimeSlot.java:8–9 | Long id, doctorId |
| Repository | domain/repository/{DoctorRepository,TimeSlotRepository}.java; infrastructure/persistence/SpringDataJpa*Repository.java:10–11 | Long lookup; JpaRepository<Entity,Long> |
| Schema mapping | infrastructure/entity/JpaDoctorEntity.java:11,18–20; JpaTimeSlotEntity.java:11,18–23 | doctors.id và doctor_time_slots.id dùng Long + IDENTITY; doctor_id dùng Long |
| Database config | src/main/resources/application.yml:16 | ddl-auto: update; không có Doctor SQL schema/migration file trong resources |
| UUID migrations | doctor-remote-database-resources.log và inventory | Không có migration UUID Doctor/slot |
| Slot contract | DoctorController.java:60–90; TimeSlotResponse.java | Chỉ public list/create slots theo Long; không có /internal/v1/doctors/{doctorUuid}/slots/{slotUuid} |

DB schema được kiểm tra qua JPA mapping và toàn bộ resources từ remote, đối chiếu source sau merge. Long/IDENTITY trên PostgreSQL thể hiện mapping numeric/BIGINT theo Hibernate; đây là suy luận từ mapping, không phải kết quả introspect database live. Không có kết nối DB được cung cấp/khởi chạy cho bước này; **schema vật lý runtime chưa được xác minh**. Không có bằng chứng DB UUID để công bố.

## Consumer mismatch

- Patient DoctorReferenceDecoder và proposed patient-doctor-reference/2 yêu cầu canonical UUIDv4, exact resource ID, version marker, machine authentication, envelope/error contract. Remote Doctor không đáp ứng UUID; chưa có bằng chứng version/auth này.
- Appointment HttpAppointmentReferenceAdapter dùng UUID cho doctorId/slotId; gọi GET /api/v1/doctors/{uuid}, GET /api/v1/doctors/{uuid}/slots?date=..., GET /internal/v1/doctors/{uuid}/slots/{slotUuid}. parseSlot cần id/doctorId UUID, slotDate/startTime/endTime và internal published boolean. Remote Doctor dùng Long và thiếu internal published-slot endpoint.
- Shared contract/schema file cũ trong docs không thay thế implementation remote. UUID migrations của Appointment/Patient không phải UUID migration Doctor.
- Doctor UI hiện còn mock numeric IDs, Number(doctorId), phép tính số để tạo mock slots. Không đổi sang UUID và không tạo mapping/fallback Long-to-UUID. Tab demo không truyền IDs vào Booking.

## Yêu cầu chính xác gửi Doctor owner (bản nháp, chưa gửi)

Vui lòng push phiên bản Doctor UUID lên một remote branch riêng và cung cấp branch/ref + full commit SHA. Hiện origin/datdt=67158fe64c8df2aae40c1d24e5159498c9cfff57 vẫn là Long. Nếu phiên bản đã tồn tại ở máy khác, hãy push đúng source đó; không chỉ gửi screenshot/UI commit. Cần bao gồm:

1. DoctorResponse.id, DoctorController tất cả path variables, DoctorScheduleResponse.doctorId, TimeSlotResponse.id/doctorId và mọi command/view/domain/port/repository/entity/mapper dùng UUID đồng nhất end-to-end; canonical UUIDv4 đáp ứng consumer hiện tại.
2. Migration PostgreSQL có version, Doctor-owned authoritative mapping/cutover cho dữ liệu cũ và FK slots.doctor_id; DDL fresh database + migration tests. Không tự sinh ID của service khác, không numeric fallback hoặc client conversion. Cung cấp schema dump đã loại credentials và kết quả kiểm chứng kiểu cột/PK/FK sau migration.
3. Doctor public detail/list/schedule/slot create/list payloads UUID; bổ sung protected internal published-slot endpoint mà Appointment đang gọi, kiểm tra đúng ownership doctorId/slotId và published facts. Không tự suy ra missing endpoint hay missing-slot code: owner xác nhận contract.
4. Patient reference contract patient-doctor-reference/2: X-FClinic-Reference-Contract header/marker, machine-auth credentials riêng, exact 200/404 envelope; từ chối absent/wrong credentials và version trước disclosure. Cung cấp quyết định auth/contract tương thích với Appointment service-token calls.
5. Test valid UUIDv4, numeric/variant/noncanonical/wrong-resource rejection, inactive Doctor, slot ownership/unpublished/missing, credential/version denial, schema fresh/upgrade; vector UUID thật + build SHA/artifact digest để chạy real-provider checks.
6. Doctor UI nhận UUID như opaque string; bỏ numeric assumptions trong phiên bản owner, phân biệt dữ liệu demo và kết quả backend thật. Không tuyên bố booking thành công bằng fallback mock.

Evidence chính: doctor-remote-id-evidence.log, doctor-after-id-evidence.log, doctor-all-remote-uuid-search.log, doctor-uuid-history-search.log, doctor-all-history.log, doctor-remote-database-{resources,config}.log, doctor-remote-entity.log, slot-remote-entity.log, doctor-remote-controller.log, slot-remote-response.log.
