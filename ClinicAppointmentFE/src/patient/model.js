export const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
export const genders = { UNKNOWN: 'Chưa xác định', MALE: 'Nam', FEMALE: 'Nữ', OTHER: 'Khác' };
export const statuses = { ACTIVE: 'Đang hoạt động', INACTIVE: 'Ngừng hoạt động' };
export const blankProfile = { fullName: '', phoneNumber: '', dateOfBirth: '', gender: 'UNKNOWN', email: '', address: '', status: 'ACTIVE' };
export function today() { return localMinute(new Date().toISOString()).slice(0, 10); }
export function localMinute(value) {
  if (!value || !Number.isFinite(Date.parse(value))) return '';
  const p = Object.fromEntries(new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).formatToParts(new Date(value)).map(x => [x.type, x.value]));
  return `${p.year}-${p.month}-${p.day}T${p.hour}:${p.minute}`;
}
export function dateText(value, time = false) {
  if (!value) return 'Chưa cập nhật';
  const d = new Date(value.length === 10 ? `${value}T12:00:00+07:00` : value);
  return Number.isNaN(+d) ? 'Ngày không hợp lệ' : d.toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric', ...(time ? { hour: '2-digit', minute: '2-digit' } : {}) });
}
export const shortId = id => id ? `${id.slice(0, 8)}…${id.slice(-6)}` : 'Không gắn lịch hẹn';
export const initials = name => name?.replace(/\([^)]*\)/g, '').split(' ').filter(Boolean).slice(-2).map(s => s[0]).join('') || 'BN';
export function phone(value) {
  if (typeof value !== 'string' || value.length > 40 || !/^[+0-9 ()-]+$/.test(value)) return null;
  let p = value.replace(/[ ()-]/g, '');
  if (/^0[0-9]{9}$/.test(p)) p = `+84${p.slice(1)}`;
  return /^\+[1-9][0-9]{7,14}$/.test(p) ? p : null;
}
function validDate(s) { return /^\d{4}-\d{2}-\d{2}$/.test(s) && Number.isFinite(Date.parse(s)) && new Date(`${s}T00:00:00Z`).toISOString().slice(0, 10) === s; }
function text(v, field, max, required, errors) {
  if (typeof v !== 'string' || [...v].length > max) { errors[field] = `Tối đa ${max} ký tự.`; return null; }
  const s = v.trim();
  if (!s && required) errors[field] = 'Vui lòng nhập thông tin này.';
  return s || null;
}
export function profilePayload(v, existing = null) {
  const errors = {};
  const body = {
    fullName: text(v.fullName, 'fullName', 255, true, errors),
    phoneNumber: phone(v.phoneNumber),
    email: text(v.email, 'email', 255, false, errors),
    dateOfBirth: v.dateOfBirth || null,
    gender: v.gender,
    address: text(v.address, 'address', 2000, false, errors),
  };
  if (!body.phoneNumber) errors.phoneNumber = 'Nhập số Việt Nam 10 chữ số hoặc số quốc tế hợp lệ.';
  if (body.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(body.email)) errors.email = 'Địa chỉ email không hợp lệ.';
  if (body.dateOfBirth && (!validDate(body.dateOfBirth) || body.dateOfBirth > today())) errors.dateOfBirth = 'Ngày sinh phải hợp lệ và không ở tương lai.';
  if (!Object.hasOwn(genders, body.gender)) errors.gender = 'Chọn giới tính hợp lệ.';
  if (existing) {
    body.status = v.status; body.version = existing.version;
    if (!Object.hasOwn(statuses, body.status)) errors.status = 'Chọn trạng thái hợp lệ.';
    if (!Number.isSafeInteger(body.version) || body.version < 0) errors.version = 'Version không thể xử lý an toàn. Vui lòng tải lại.';
  }
  return { body, errors };
}
export function medicalPayload(v, existing = null) {
  const errors = {};
  const visitDate = existing && localMinute(existing.visitDate) === v.visitDate ? existing.visitDate : `${v.visitDate}:00+07:00`;
  const body = { visitDate, symptoms: text(v.symptoms, 'symptoms', 2000, true, errors), diagnosis: text(v.diagnosis, 'diagnosis', 2000, true, errors), treatment: text(v.treatment, 'treatment', 4000, false, errors), notes: text(v.notes, 'notes', 4000, false, errors) };
  if (!v.visitDate || !validDate(v.visitDate.slice(0, 10)) || !Number.isFinite(Date.parse(visitDate)) || Date.parse(visitDate) > Date.now()) errors.visitDate = 'Thời điểm khám phải hợp lệ và không ở tương lai.';
  if (existing) {
    body.version = existing.version;
    body.changeReason = text(v.changeReason, 'changeReason', 1000, true, errors);
    if (!Number.isSafeInteger(body.version) || body.version < 0) errors.version = 'Version không thể xử lý an toàn. Vui lòng tải lại.';
  } else {
    body.doctorId = v.doctorId.trim(); body.appointmentId = v.appointmentId.trim() || null;
    if (!UUID.test(body.doctorId)) errors.doctorId = 'Doctor ID phải là UUIDv4 chữ thường theo Patient v3.';
    if (body.appointmentId && !UUID.test(body.appointmentId)) errors.appointmentId = 'Appointment ID phải là UUIDv4 hoặc để trống.';
  }
  return { body, errors };
}
