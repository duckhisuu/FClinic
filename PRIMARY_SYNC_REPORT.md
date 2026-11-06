# Phase 1.5 — Safe primary promotion

Ngày 06/11/2026 (Asia/Saigon). **PROMOTION: COMPLETE** tới clean primary workspace kế bên. Không thay thế/rename project gốc.

## Điểm làm việc mới

- Primary: `D:/Hoc_tap/26_FALL/MSS301/Project_Group/FClinic_Primary`
- Branch phát triển: `feature/patient-react-ui`, tạo trực tiếp từ source HEAD `121ccc6fc9934ce684f024534d715a6629d5d973` sau các gate PASS.
- Integration branch local: `integration/fclinic-patient-doctor-demo`, vẫn trỏ source HEAD.
- HEAD cuối trên feature chỉ thêm năm report; xem promotion-evidence/final-ledger.json. Production source không đổi sau test.
- Original: `D:/Hoc_tap/26_FALL/MSS301/Project_Group/FClinic`, giữ nguyên cả dirty state.
- Full preservation: `D:/Hoc_tap/26_FALL/MSS301/Project_Group/FClinic_Promotion_Backup_20261106`.

## Git-native promotion

SafeSync integration ref vẫn là `121ccc6fc9934ce684f024534d715a6629d5d973`; working branch thực tế là codex/phase2-patient-doctor-long, có thay đổi Phase 2 chưa commit. Clone bằng git clone --no-hardlinks --branch integration/fclinic-patient-doctor-demo từ SafeSync; không lấy dirty working tree, không cherry-pick lại Patient commits. Import origin/bao/main/datdt snapshots từ refs SafeSync bằng local Git fetch; không fetch/merge thêm code từ GitHub. Sau import, xóa remote safesync ở primary; origin trỏ https://github.com/duckhisuu/FClinic.git. Objects độc lập, không alternates.

Lịch sử giữ nguyên:

| Commit | SHA | Ý nghĩa |
|---|---|---|
| Base bao | add0b540ef483bc8a295de5500b7bee6462d4aac | điểm bắt đầu integration |
| Main merge | ce52ee7a68c7f83dc4e967c73bbd5728b126077a | parent main dbe7623d62f4f94f11378d9a47fb0787efd7b0eb |
| Datdt merge | ca24c22f6cc1cb9e500d8c7039ee2ab204017558 | parent datdt 67158fe64c8df2aae40c1d24e5159498c9cfff57 |
| Phase 1 source | 121ccc6fc9934ce684f024534d715a6629d5d973 | báo cáo Phase 1 |

Ancestry bao/main/datdt -> source được kiểm chứng bằng merge-base --is-ancestor. Không merge lên main/bao và không push.

## Source/local changes

Source tracked của primary khớp hoàn toàn integration HEAD trước test. Patient tree và Appointment UI tree đồng nhất; DoctorSchedulePage blob đúng origin/datdt. Chênh original HEAD -> source: 1372 files changed, 27591 insertions(+), 103962 deletions(-). Chi tiết phân loại: PROMOTED_SOURCE_DIFF.md và promotion-evidence/source-comparison.{json,tsv}.

Original có một commit local-only a6bf54e0edc6fa513e07b7e91a5ddaeefd1a58c9, giữ ở nhánh codex/preservation-original-main-20261106 trong primary và original-history.bundle. Giữ đầy đủ working changes trong bản sao toàn bộ 153937 file (10163235589 bytes), SHA256 tất cả khớp; không tự overlay code cũ vào baseline.

## Kiểm chứng

Patient 293 PASS; full reactor 314 PASS và 9 projects SUCCESS; npm ci/build PASS. Không xóa/disable/skip test. 0 Git unmerged paths. Build đúng clean Phase 1 source, không chứa Phase 2 đang dang dở.

**READY FOR REACT DEVELOPMENT: YES** về workspace và build. Chưa chứng nhận clinical integration/demo E2E. Doctor Long đã được quyết định giữ nguyên; yêu cầu chuyển Doctor UUID trong báo cáo Phase 1 là lịch sử, không phải hướng phát triển tiếp theo.
