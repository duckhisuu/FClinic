# Kết quả kiểm thử UI

Ngày hoàn tất: 07/11/2026 (Asia/Saigon). Chạy tại primary, không dùng test outputs SafeSync.

| Gate | Kết quả |
|---|---|
| npm ci | PASS, exit 0, lockfile reproducible |
| Vitest 4.1.12 + RTL | PASS: 35 tests, 5 files, 0 failed/skipped |
| npm run build | PASS, Vite 6.4.3; production bundle không chứa fixtures |
| Maven Patient verify -am | PASS: 213 surefire + 80 failsafe = 293, 0 failures/errors/skipped |
| Actual browser Preview | PASS: profile create/edit/version 1; medical create/edit/version 1; navigation, Live missing-session blocker |
| Production preview guard | PASS: `?patientPreview=1` không bật Preview; không có switch/banner synthetic, create disabled khi thiếu session |

## Automated coverage

- Model (10): required fields, lengths theo Unicode code point, phone normalization, date/email/enum checks, unsafe version, UUID-only Doctor reference, exact create/PUT body và changeReason.
- API (17): session injection/missing/failing session, tám v3 bindings, 400/401/403/404/409/422/500/503, malformed envelopes, mismatched resource ownership, request allowlist, unsafe integer, timeout, uncertain mutation, no retry/fallback; Preview in-memory isolation.
- Resource hook (1): abort/sequence guard; kể cả provider bỏ qua AbortSignal, response cũ không ghi đè.
- UI (6): search/page/page-size, loading/empty/error, profile create/read/update, records list/detail/create/edit, Live switch isolation, clinical disabled, duplicate-submit/conflict/reload.
- Shared navigation (1): Patient cùng các entry Doctor Directory/Schedule, Booking, Appointment History, Notification Drawer. Leaf modules được mock cho navigation test; source blob của các module gốc được kiểm Git riêng.

## Browser QA thực tế

In-app browser tại dev URL 5173, production preview 4173. Đã nhập dữ liệu synthetic qua UI, chờ acknowledgment trước kiểm kết quả. Đã đo document clientWidth = scrollWidth tại cả 1366×768, 1920×1080, 390×844. Header/sidebar giữ nguyên, bổ sung nav cuộn ngang mobile. Screenshots ở `screenshots/`; một số ảnh full-page, các ảnh danh sách desktop/mobile cuối dùng explicit viewport clip. `browser-qa.json` trong local evidence ghi kích thước đo và assertions.

Không gọi genuine Patient/Auth/WorkScope trong Live vì provider session chưa được cung cấp. API tests dùng fetch doubles; browser Preview dùng adapter synthetic; Maven dùng PostgreSQL disposable và các authority fixtures vốn có. Không tuyên bố genuine clinical E2E PASS.

## Evidence và giới hạn

`patient-ui-evidence/frontend-tests.json`, `frontend-tests.log`, `npm-ci.log`, `react-build.log`, `patient-regression.log/json`, `browser-qa.json`, `final-ledger.json` nằm local và được exclude khỏi Git. Sau build kiểm không có fixture literals trong dist. Backend source và các màn cũ không đổi.

Npm audit còn 5 advisory trong dependency tree có sẵn (3 moderate, 2 high); không force-upgrade Vite/Router trong scope này. Bộ Vitest mới dùng 4.1.12 tương thích Vite 6/Node 24, không giữ critical advisory của phiên bản test runner cũ đã khảo sát. Trước npm ci phải dừng Vite trên Windows để tránh file lock esbuild; gate cuối đã PASS sau khi dừng riêng server do tác vụ tạo.
