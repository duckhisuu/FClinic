# Original preservation

**ORIGINAL DATA PRESERVED: YES.** Không reset/clean/stash/checkout/pull/merge hay ghi source/index/branch trong original. Giữ nguyên đường dẫn original, dùng sibling primary mới.

## Before/after

- Original HEAD: `a6bf54e0edc6fa513e07b7e91a5ddaeefd1a58c9`; branch main.
- Index SHA256: `73c602f75f6ab8b81d0779964dd6f2ac8c3f680d9c9a72cfe8e6d3386ee9d184`.
- HEAD, branch, index, porcelain status, refs, toàn bộ manifest before/after giống hệt.
- 153937 file ngoài .git được hash, gồm tracked/untracked/ignored/configs/generated files; không bỏ qua node_modules/target/dist trong backup.
- Trước promotion: 1371 tracked, 27 untracked, 152539 ignored. Một tracked modification: ClinicAppointmentBE/services/appointment-service/src/test/java/com/fclinic/appointmentservice/reference/RealAppointmentRuntime.java.

## Bản sao độc lập

`D:/Hoc_tap/26_FALL/MSS301/Project_Group/FClinic_Promotion_Backup_20261106/original-worktree` chứa đủ 153937 file, 10163235589 bytes. Robocopy exit 1, FAILED=0; checksum từng file khớp manifest. Không /MIR, không delete/move, không copy .git như repository hoạt động. Metadata HEAD/index/config/packed-refs được lưu riêng ở git-metadata; lịch sử refs/commits lưu original-history.bundle.

Original commit local-only a6bf54e0edc6fa513e07b7e91a5ddaeefd1a58c9 có 1306 paths trong commit; không gộp tùy tiện vào integration. Có thể truy cập bằng branch codex/preservation-original-main-20261106 trong primary hoặc bundle. Working/index binary patches và inventory danh sách đầy đủ lưu trong preflight.

## Local-only source và collision

1068 paths tracked trong original không có ở integration; 21 paths untracked chỉ có original. Chúng vẫn ở original, full backup và original Git history tương ứng; không được bật trong primary source chỉ để tránh làm hỏng baseline. Hai source ActualJarReadiness.java/ActualJarReadinessTest.java và thay đổi RealAppointmentRuntime.java được bảo toàn để review riêng, không bị bỏ/xóa.

Có một collision nội dung với untracked local evidence: ClinicAppointmentBE/services/patient-service/evidence/patient-post-sync-verification.json. Giữ bản integration tại primary; giữ bản local nguyên trạng tại original/backup. Không ghi đè hay trộn hai nguồn evidence. 0 Git conflicts phát sinh.

## Cấu hình riêng

9 ignored IDE config files ngoài build output được phát hiện và sao lưu; không tự áp dụng vào primary:

- `.idea/.gitignore`
- `.idea/compiler.xml`
- `.idea/encodings.xml`
- `.idea/FClinic.iml`
- `.idea/jarRepositories.xml`
- `.idea/misc.xml`
- `.idea/modules.xml`
- `.idea/vcs.xml`
- `.idea/workspace.xml`

Không phát hiện .env/.npmrc local ngoài generated output trong inventory. Toàn bộ ignored files vẫn được copy, gồm archives/logs và configs nằm trong build outputs. Không in nội dung credentials vào report và không commit bản backup/config riêng.

## SafeSync

HEAD/branch/index/status/refs giữ nguyên. 17 file pending Phase 2 (modified/untracked) được copy riêng và checksum khớp vào safesync-pending; các log Phase 2 lưu safesync-phase2-evidence. Phase 2 chưa hoàn tất; lần unit-test cuối có compile failure ở test mới, không được dùng làm baseline đã verify. Không sửa/commit/đưa chúng vào primary trong Phase 1.5.

Evidence: promotion-evidence/preservation-verification.json; preflight/original-full-{before,after}/; backup-verification.json; original-worktree-copy.log; original-history.bundle và safesync-history.bundle.
