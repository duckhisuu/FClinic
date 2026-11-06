// Entirely synthetic. Not derived from people or backend data. Imported only in explicit development preview.
export const fixtureId = number => `11111111-1111-4111-8111-${String(number).padStart(12, '0')}`;
export const PREVIEW_DOCTOR = fixtureId(900);
const names = ['Nguyễn Minh An', 'Trần Ngọc Linh', 'Lê Hoàng Nam', 'Phạm Thu Hà', 'Võ Gia Hân', 'Đặng Minh Khôi', 'Bùi Thanh Mai', 'Đỗ Quốc Huy', 'Hồ Anh Thư', 'Dương Hải Yến', 'Ngô Tuấn Kiệt', 'Vũ Bảo Ngọc'];
export function makeFixtures() {
  const timestamp = new Date(Date.now() - 86400000 * 3).toISOString();
  const patients = Array.from({ length: 24 }, (_, i) => ({ id: fixtureId(i + 1), patientCode: `PAT-${fixtureId(i + 1)}`, fullName: `${names[i % names.length]}${i >= 12 ? ' (minh họa)' : ''}`, phoneNumber: `+8490000${String(i + 1).padStart(4, '0')}`, email: `preview${i + 1}@example.test`, dateOfBirth: `${1985 + i % 20}-05-${String(i % 20 + 1).padStart(2, '0')}`, gender: i % 2 ? 'FEMALE' : 'MALE', address: `Địa chỉ minh họa số ${i + 1} — dữ liệu tổng hợp`, status: i % 7 === 3 ? 'INACTIVE' : 'ACTIVE', version: 0, createdAt: timestamp, updatedAt: timestamp, createdBy: 'preview-staff', updatedBy: 'preview-staff' }));
  const records = Array.from({ length: 14 }, (_, i) => ({ id: fixtureId(100 + i), patientId: patients[0].id, doctorId: PREVIEW_DOCTOR, appointmentId: null, visitDate: new Date(Date.now() - (i + 2) * 86400000).toISOString(), symptoms: 'Triệu chứng minh họa để kiểm tra giao diện.', diagnosis: ['Theo dõi sức khỏe tổng quát (minh họa)', 'Tái khám định kỳ (minh họa)', 'Đánh giá triệu chứng (minh họa)'][i % 3], treatment: 'Nội dung điều trị minh họa, không phải hướng dẫn y khoa.', notes: 'Bản ghi synthetic — chỉ phục vụ UI Preview.', version: 0, createdAt: timestamp, updatedAt: timestamp, createdBy: 'preview-doctor', updatedBy: 'preview-doctor' }));
  return { patients, records };
}
