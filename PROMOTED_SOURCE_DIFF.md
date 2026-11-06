# Promoted source diff

Source SHA: `121ccc6fc9934ce684f024534d715a6629d5d973`. So sánh với Git object integration, không so với dirty Phase 2 working tree SafeSync.

## Original working tree so với integration

| Phân loại | Paths |
|---|---:|
| eol-only | 108 |
| content-different | 143 |
| ignored-only | 152539 |
| new-in-integration | 185 |
| original-tracked-only | 1068 |
| local-only-untracked | 21 |
| identical | 58 |

identical = bytes khớp; eol-only = khác CRLF/LF nhưng UTF-8 text giống; content-different = khác nội dung; new-in-integration = path không có trong original worktree. original-tracked-only/local-only-untracked/ignored-only là dữ liệu giữ ở original+backup, không phải file đã bị xóa từ original. Các nhóm là phân loại union paths, không phải diff Git commit.

Git diff original HEAD -> integration: 1372 files changed, 27591 insertions(+), 103962 deletions(-). Full paths/diff: promotion-evidence/original-head-to-integration-files.tsv và original-head-to-integration.diff. Một commit original-only được giữ riêng, không cherry-pick Patient.

## Local collision decision

- Path: ClinicAppointmentBE/services/patient-service/evidence/patient-post-sync-verification.json.
- Original untracked content khác integration tracked content.
- Primary giữ bản integration đã publish trong source; original/backup giữ bản local khác. Không đưa evidence cũ lên ghi đè evidence mới.
- Modified RealAppointmentRuntime.java là original-tracked-only; bảo toàn riêng, không tự áp dụng vào Appointment source.

## Primary so với source đã kiểm thử

- Toàn bộ tracked production/source files: no diff.
- Patient service tree: 4a1530f6dba784a63968460ecc5f8187bc435a12.
- Appointment UI src/appointment tree: 538553d732dbca205394bd1116981df578e93515.
- DoctorSchedulePage blob: 244cbf5358412cd16051cf21e5918657e39fd51a, khớp origin/datdt.
- App.jsx giữ phần ghép Phase 1: Doctor management tab riêng, Booking/AppointmentHistory/DoctorDirectory giữ nguyên.
- Sau các gates, chỉ thêm đúng năm báo cáo Phase 1.5 trên feature branch; không sửa code hoặc UI implementation.

Kiểm chứng máy đọc: promoted-source-verification.json, source-comparison.json/tsv, final-ledger.json. Baseline commit có trailing whitespace đã ghi ở Phase 1; không chỉnh published source vì promotion này.
