# Next phase handoff

**READY FOR PHASE 2: BLOCKED — Doctor UUID source MISSING REMOTE.**

## Điểm tiếp tục

- Clone: `D:/FClinic_SafeSync/phase1-main-doctor-20261106`
- Branch: `integration/fclinic-patient-doctor-demo`
- Main merge: `ce52ee7a68c7f83dc4e967c73bbd5728b126077a`
- Datdt merge: `ca24c22f6cc1cb9e500d8c7039ee2ab204017558`
- Final HEAD/report commit: phase1-evidence/final-ledger.json và git log -1.
- Không có push/PR/promotion lên main hoặc bao. Original và clone Appointment cũ không thay đổi.

## Đã hoàn tất

Hai merge lịch sử đầy đủ; main backend/Patient tests PASS, datdt backend/Patient tests PASS, npm ci/build PASS. Tab Quản lý bác sĩ (demo) nhận UI mới; luồng Patient/Appointment hiện tại giữ nguyên. Chọn slot trong tab demo chỉ hiện thông báo, không dùng ID numeric/mock để đặt lịch UUID.

## Điều kiện mở Phase 2

1. Doctor owner push đúng phiên bản UUID + cung cấp full SHA/ref, migrations/schema evidence, protected slot API và contract tests theo DOCTOR_UUID_SOURCE_VERIFICATION.md. Bản yêu cầu đã chuẩn bị ở đó, chưa gửi cho ai. Không tự chuyển Long thành UUID hoặc tạo fallback.
2. Fetch lại vào clone mới, review Doctor diff từng file rồi xác minh DTO/controller/domain/repository/schema/migrations/slots bằng SHA và artifact thật; không dùng commit UI làm bằng chứng UUID.
3. Chốt các contract/auth liên service: Patient Doctor reference v2; Doctor internal published-slot endpoint; Appointment service token; Auth/WorkScope UUID authorities. Kiểm tra routing FE/Gateway: src/appointment/api.js vẫn gọi /api/v2/patients trong khi Patient controller UUID dùng /api/v3/patients. Đây là mismatch có sẵn cần owner review; Phase 1 không đổi hoặc tạo alias.
4. Rà soát Notification event/recipient IDs và envelope/auth giữa providers, chạy các real-provider gates với UUID thật và binary provenance; không công bố E2E chỉ từ reactor mặc định.
5. Sau contract approval mới nối DoctorSchedulePage selection vào Booking và loại numeric assumptions/mock success theo source owner; chạy lại 293 Patient tests, full reactor, npm ci/build và demo thật.

## Evidence và cách đọc

Năm report ở root clone. phase1-evidence chứa SHA/remote logs, main/datdt incoming và reviewed diff, danh sách paths đầy đủ TSV, merge commit parents/history, Maven/npm logs + XML + counts, source Doctor remote/sau merge và manifest original before/after. Evidence local được .git/info/exclude, không nằm trong commit báo cáo. final-ledger.json ghi HEAD cuối, kiểm chứng ancestry và checksum evidence.

Không thao tác original để tiếp tục tích hợp; không đụng 15 conflicts Appointment cũ; không reset/clean/stash, không force push, không tự push bao/main.
