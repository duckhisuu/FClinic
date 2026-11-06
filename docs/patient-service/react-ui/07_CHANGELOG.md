# Changelog Patient React UI

Base: b14fa8af0ab8d3110eeb31356a4859acfb11839d, feature/patient-react-ui. Final SHA và danh sách file đầy đủ nằm trong local `patient-ui-evidence/final-ledger.json` và Git commit hiện tại.

## Implemented

- MH01 Patient List: search/pagination/sort, skeleton/empty/error/retry, status và conditional create.
- MH02 profile create/detail/edit, complete PUT, field validation, nullable clearing, optimistic conflict reload.
- MH03 records list/detail với Patient context, metadata và Doctor UUID literal.
- MH04 clinical create/edit Preview, required reason/version, immutable refs; Live clinical blocker.
- Explicit development Preview với fixtures/adapter bộ nhớ, scenario controls; Live tách biệt, không silent fallback hoặc fake authorization.
- API client bind tám endpoint v3, safe errors, response ownership, abort/timeout, mutation uncertainty.
- Shared integration tối thiểu trong App.jsx, v3 Vite proxy, nav mobile cuộn ngang; style riêng Patient.
- Vitest/RTL, 35 frontend tests; Patient regression 293 giữ nguyên; screenshots/runbook/binding matrix và backend gaps.

## Preserved

Không đổi Patient Backend hoặc migrations. Không đổi DoctorDirectory/DoctorSchedulePage/Booking/AppointmentHistory/NotificationDrawer hoặc shared services/api.js. Không sửa Doctor/Appointment/Notification/Gateway/Auth/WorkScope production. Không sửa original, không nhập SafeSync pending, không fetch/merge/push main/bao.

## Files

Module mới dưới ClinicAppointmentFE/src/patient gồm model/api/hooks/components/pages/preview/tests/CSS. Shared files đổi: package.json, package-lock.json (test-only dev dependencies), vite.config.js, src/App.jsx, src/index.css (mobile nav only), thêm vitest.config.js. ClinicAppointmentFE/.gitignore tạo ngoại lệ riêng cho Patient __tests__ để suite được versioned dù root ignore test directories; không sửa rule gốc hoặc backend. Tài liệu và screenshots dưới docs/patient-service/react-ui/. Không thêm runtime dependency mới.
