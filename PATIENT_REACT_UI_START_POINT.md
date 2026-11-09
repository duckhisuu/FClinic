> Local integration 09/11/2026: xem [runbook hiện hành](docs/integration/LOCAL_RUNBOOK.md), [nghiệm thu Patient](docs/integration/PATIENT_ACCEPTANCE_TESTS.md) và [các gate còn mở](docs/integration/REMAINING_ISSUES.md). Patient Preview chỉ còn trong test harness; ứng dụng chính dùng Auth/API thật. Trạng thái toàn sản phẩm: PARTIAL.

# Patient React UI — start point

**READY FOR REACT DEVELOPMENT: YES — dùng primary mới dưới đây.**

- Workspace: `D:/Hoc_tap/26_FALL/MSS301/Project_Group/FClinic_Primary`
- Branch: `feature/patient-react-ui`
- Source baseline: `121ccc6fc9934ce684f024534d715a6629d5d973`
- Integration branch local vẫn ở baseline. Feature được tạo từ đúng baseline; HEAD cuối chỉ thêm năm report Phase 1.5.
- Không tiếp tục viết UI trong SafeSync hoặc project original đang dirty. Không có React implementation mới trong nhiệm vụ này.

PowerShell:

```powershell
Set-Location -LiteralPath 'D:\Hoc_tap\26_FALL\MSS301\Project_Group\FClinic_Primary'
git status --short --branch
git branch --show-current
Set-Location -LiteralPath ClinicAppointmentFE
npm run dev
```

## Source đã có

ClinicAppointmentFE/src/App.jsx giữ DoctorDirectory, Booking, AppointmentHistory và NotificationDrawer của integration, thêm DoctorSchedulePage vào tab Doctor demo. Không nối numeric/mock Doctor IDs vào booking UUID. Patient backend hiện hành tại ClinicAppointmentBE/services/patient-service, API /api/v3/patients, UUIDv4 Patient/MedicalRecord.

## Ràng buộc cho bước tiếp theo

Quyết định kiến trúc mới: Doctor/TimeSlot tiếp tục Long; Appointment UUID và contract Doctor UUID của Appointment giữ nguyên. Các yêu cầu chuyển Doctor sang UUID trong docs Phase 1 là lịch sử đã bị quyết định này thay thế. Patient↔Doctor Long Phase 2 đang dang dở, không được promotion, chưa có contract v4 sẵn sàng trong baseline này. Không dùng code pending SafeSync như code đã verify.

Trước UI medical flow thật, hoàn thiện Patient-owned Long contract/data model/auth binding theo Phase 2 trên primary trong một branch backend riêng. Không suy Long từ Auth UUID/subject, không fake UUID, không bypass WorkScope/VALID_VISIT. Profile UI có thể bắt đầu với contract /api/v3 hiện tại; header Bearer và các scope vẫn bắt buộc. GET/search dùng keyword/phoneNumber, page/size và typed error envelope; tham chiếu PatientController/PatientInput/OpenAPI trong service để chốt field. Không tự dùng /api/v2/patients vì adapter Appointment cũ vẫn có route mismatch đó.

Giữ React IDs dạng string khi cần toàn bộ miền Long; không Number/cast/hash để che khác biệt ID. Phân biệt public Doctor lookup với trusted clinical reference. Chỉ gọi genuine medical demo PASS khi Auth/WorkScope binding thật được kiểm chứng.

## Local artifacts

Original local-only source/configs/commits đã được giữ trong original và D:/Hoc_tap/26_FALL/MSS301/Project_Group/FClinic_Promotion_Backup_20261106, cùng branch codex/preservation-original-main-20261106 ở primary. Không tự overlay chúng. promotion-evidence/final-ledger.json ghi HEAD cuối, merge anchors, checksums và tests.
