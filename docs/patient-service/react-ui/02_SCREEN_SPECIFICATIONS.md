# Đặc tả bốn màn hình

## MH01 — Danh sách bệnh nhân: COMPLETE

Header Quản lý bệnh nhân, tổng kết quả trong scope/search hiện tại, tìm tên (`keyword`) hoặc số điện thoại đầy đủ (`phoneNumber`), bốn sort backend cho phép, page/size. Không gửi status filter hoặc parameter `phone` tự đặt. Bảng desktop có avatar tổng hợp, tên, mã PAT (rút gọn hiển thị; full value ở tooltip/profile), điện thoại, DOB, status và nút mở. Mobile chuyển thành card. Skeleton/empty/error/retry rõ ràng; AbortController + sequence guard chống stale search/page responses.

Tạo hồ sơ chỉ mở khi Preview hoặc host session cung cấp create hint. Live thiếu session hiển thị disconnected, không giả login/quyền. Bộ chọn tình huống chỉ xuất hiện trong Preview: normal/empty/error/slow/conflict.

## MH02 — Profile: COMPLETE

Hiển thị đầy đủ Patient UUID/code, fullName, DOB, enum gender, phone, email, address, ACTIVE/INACTIVE, version, timestamps và actors. Điều hướng tới bệnh án, back và breadcrumb. Create dùng sáu trường administrative; không gửi status/version/id. Edit complete PUT gồm đủ các nullable fields, status và version vừa đọc.

Required/Unicode length/phone/DOB/email/enum validation dùng model chung, không serialize response mù quáng. PUT clearing optional fields gửi null. Ngừng hoạt động cần xác nhận. Reset/cancel/navigation có dirty guard; đang submit không cho rời/chuyển mode hoặc gửi lặp. Backend fieldErrors hiển thị theo field. 409 VERSION_CONFLICT có reload action; không tự tăng version. Network/invalid success response không hiển thị success; kết quả chưa xác định khóa gửi lại và yêu cầu đọc lại dữ liệu.

## MH03 — Bệnh án list/detail: COMPLETE

Patient context luôn hiện để tránh nhập nhầm hồ sơ. List hiển thị ngày khám, Doctor UUID theo Patient v3, diagnosis/treatment summary, version/updated metadata, pagination và sort visitDate. Detail có toàn bộ clinical fields, ID tham chiếu, created/updated actors/timestamps. Không gọi Doctor Service, không suy tên Doctor từ ID. Read-only Live vẫn phải qua backend quyền đọc; STAFF không được tự cấp clinical READ.

## MH04 — Clinical editor: COMPLETE trong Preview; Live mutation bị khóa có chủ đích

Create: doctorId UUIDv4, appointmentId UUIDv4/null, visitDate có offset +07, symptoms/diagnosis bắt buộc, treatment/notes nullable. Preview dùng Doctor UUID synthetic, không chuyển từ Long. Edit: refs readonly và bị loại khỏi PUT; version lấy từ record, changeReason bắt buộc. Visit datetime cũ giữ nguyên độ chính xác nếu người dùng không sửa phút hiển thị.

Live có blocker trusted identity/clinical authority/VALID_VISIT; không tạo ID giả hoặc mặc định walk-in hợp lệ. Tạo mới với INACTIVE patient bị chặn; update bệnh án cũ vẫn có thể minh họa theo semantics backend hiện hành. Preview xác nhận rõ là mô phỏng và không gửi mutation tới backend.

## Trạng thái và responsive

Giao diện tiếng Việt, teal/emerald, card nền sáng, Lucide icons, semantic headings/labels, focus/breadcrumb, inline field errors, aria-live notices và reduced-motion skeleton. Đã kiểm UI thực tại viewport 1366×768, 1920×1080 và 390×844; không có document horizontal overflow. Sidebar mobile cuộn trong nav riêng. Không tuyên bố đã audit screen-reader hoặc genuine authorization E2E.
