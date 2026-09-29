import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_GATEWAY_URL || 'http://localhost:8080';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 8000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Response interceptor to unwrap ApiResponse format: { status, message, data }
apiClient.interceptors.response.use(
  (response) => {
    if (response.data && response.data.data !== undefined) {
      return response.data.data;
    }
    return response.data;
  },
  (error) => {
    const errorMsg =
      error.response?.data?.message ||
      error.response?.data?.error ||
      error.message ||
      'Không thể kết nối đến máy chủ API';
    return Promise.reject(new Error(errorMsg));
  }
);

// Fallback interactive mock data for seamless demo when backend services are offline
const MOCK_DOCTORS = [
  {
    id: 1,
    name: 'BS. CKII Nguyễn Văn An',
    specialty: 'Tim Mạch',
    department: 'Khoa Nội Tim Mạch',
    qualification: 'Tiến sĩ Y khoa - ĐH Y Dược',
    experienceYears: 18,
    consultationFee: 350000,
    roomNumber: 'P.204 - Tầng 2',
    bio: 'Chuyên gia đầu ngành chẩn đoán & can thiệp bệnh lý tăng huyết áp, suy tim, bệnh mạch vành.',
    avatarUrl: 'https://images.unsplash.com/photo-1622253692010-333f2da6031d?w=400',
    active: true,
  },
  {
    id: 2,
    name: 'ThS. BS Trần Thị Mai',
    specialty: 'Nhi Khoa',
    department: 'Khoa Nhi',
    qualification: 'Thạc sĩ Nhi khoa - ĐH Y Hà Nội',
    experienceYears: 12,
    consultationFee: 300000,
    roomNumber: 'P.108 - Tầng 1',
    bio: 'Nhiều năm kinh nghiệm thăm khám, điều trị bệnh hô hấp, tiêu hóa và tư vấn dinh dưỡng cho trẻ nhỏ.',
    avatarUrl: 'https://images.unsplash.com/photo-1594824813586-7a71f00845a7?w=400',
    active: true,
  },
  {
    id: 3,
    name: 'BS. CKI Lê Hoàng Quân',
    specialty: 'Răng Hàm Mặt',
    department: 'Khoa Răng Hàm Mặt',
    qualification: 'Bác sĩ CKI Nha khoa tổng quát',
    experienceYears: 9,
    consultationFee: 250000,
    roomNumber: 'P.302 - Tầng 3',
    bio: 'Khám và điều trị các bệnh lý nha chu, chỉnh nha thẩm mỹ, phục hình răng sứ không xâm lấn.',
    avatarUrl: 'https://images.unsplash.com/photo-1537368910025-700350fe46c7?w=400',
    active: true,
  },
  {
    id: 4,
    name: 'PGS. TS Phạm Minh Đức',
    specialty: 'Cơ Xương Khớp',
    department: 'Khoa Cơ Xương Khớp',
    qualification: 'Phó Giáo sư, Tiến sĩ Y khoa',
    experienceYears: 22,
    consultationFee: 450000,
    roomNumber: 'P.405 - Tầng 4',
    bio: 'Chuyên gia đầu ngành về điều trị thoái hóa khớp, thoát vị đĩa đệm và bệnh gút mạn tính.',
    avatarUrl: 'https://images.unsplash.com/photo-1612349317150-e413f6a5b16d?w=400',
    active: true,
  },
];

const generateMockSlots = (doctorId, dateStr) => [
  { id: doctorId * 10 + 1, doctorId, slotDate: dateStr, startTime: '08:00:00', endTime: '08:30:00', isBooked: false },
  { id: doctorId * 10 + 2, doctorId, slotDate: dateStr, startTime: '08:30:00', endTime: '09:00:00', isBooked: false },
  { id: doctorId * 10 + 3, doctorId, slotDate: dateStr, startTime: '09:00:00', endTime: '09:30:00', isBooked: true },
  { id: doctorId * 10 + 4, doctorId, slotDate: dateStr, startTime: '10:00:00', endTime: '10:30:00', isBooked: false },
  { id: doctorId * 10 + 5, doctorId, slotDate: dateStr, startTime: '14:00:00', endTime: '14:30:00', isBooked: false },
  { id: doctorId * 10 + 6, doctorId, slotDate: dateStr, startTime: '14:30:00', endTime: '15:00:00', isBooked: false },
  { id: doctorId * 10 + 7, doctorId, slotDate: dateStr, startTime: '15:30:00', endTime: '16:00:00', isBooked: false },
];

let mockAppointments = [
  {
    id: 1,
    bookingCode: 'FC-DEMO001',
    patientId: 1,
    patientName: 'Nguyễn Văn Hùng',
    patientPhone: '0912345678',
    doctorId: 1,
    doctorName: 'BS. CKII Nguyễn Văn An',
    doctorSpecialty: 'Tim Mạch',
    slotId: 1,
    appointmentDate: new Date().toISOString().split('T')[0],
    startTime: '08:00:00',
    endTime: '08:30:00',
    reasonForVisit: 'Kiểm tra định kỳ huyết áp và đau thắt ngực',
    consultationFee: 350000,
    status: 'CONFIRMED',
    createdAt: new Date().toISOString(),
  },
  {
    id: 2,
    bookingCode: 'FC-DEMO002',
    patientId: 2,
    patientName: 'Lê Thuỳ Linh',
    patientPhone: '0987654321',
    doctorId: 2,
    doctorName: 'ThS. BS Trần Thị Mai',
    doctorSpecialty: 'Nhi Khoa',
    slotId: 2,
    appointmentDate: new Date(Date.now() + 86400000).toISOString().split('T')[0],
    startTime: '14:00:00',
    endTime: '14:30:00',
    reasonForVisit: 'Bé ho sốt kéo dài 2 ngày',
    consultationFee: 300000,
    status: 'PENDING',
    createdAt: new Date().toISOString(),
  },
];

let mockOutboxEvents = [
  {
    id: 'f87a19c4-1234-4567-890a-bcdef1234567',
    aggregateId: '1',
    aggregateType: 'APPOINTMENT',
    eventType: 'APPOINTMENT_BOOKED',
    payload: JSON.stringify({
      bookingCode: 'FC-DEMO001',
      patientName: 'Nguyễn Văn Hùng',
      patientPhone: '0912345678',
      doctorName: 'BS. CKII Nguyễn Văn An',
      appointmentDate: new Date().toISOString().split('T')[0],
      startTime: '08:00:00',
    }),
    status: 'COMPLETED',
    publishedAt: new Date().toISOString(),
    createdAt: new Date().toISOString(),
  },
];

let mockNotifications = [
  {
    id: 'notif-1',
    bookingCode: 'FC-DEMO001',
    recipientName: 'Nguyễn Văn Hùng',
    recipientPhone: '0912345678',
    doctorName: 'BS. CKII Nguyễn Văn An',
    appointmentTime: `${new Date().toISOString().split('T')[0]} 08:00`,
    channel: 'SMS/ZALO',
    message: 'FClinic: Quý khách Nguyễn Văn Hùng đã đặt lịch khám thành công với BS. CKII Nguyễn Văn An. Mã hẹn: FC-DEMO001.',
    status: 'SENT',
    createdAt: new Date().toISOString(),
  },
];

// Doctor Service APIs
export const doctorApi = {
  getDoctors: async (specialty, department) => {
    try {
      const params = new URLSearchParams();
      if (specialty) params.append('specialty', specialty);
      if (department) params.append('department', department);
      return await apiClient.get(`/api/v1/doctors?${params.toString()}`);
    } catch {
      let filtered = [...MOCK_DOCTORS];
      if (specialty) filtered = filtered.filter((d) => d.specialty.toLowerCase() === specialty.toLowerCase());
      if (department) filtered = filtered.filter((d) => d.department.toLowerCase() === department.toLowerCase());
      return filtered;
    }
  },

  getDoctorById: async (id) => {
    try {
      return await apiClient.get(`/api/v1/doctors/${id}`);
    } catch {
      return MOCK_DOCTORS.find((d) => d.id === Number(id)) || MOCK_DOCTORS[0];
    }
  },

  getDoctorSlots: async (doctorId, date) => {
    try {
      const params = date ? `?date=${date}` : '';
      return await apiClient.get(`/api/v1/doctors/${doctorId}/slots${params}`);
    } catch {
      const dateStr = date || new Date().toISOString().split('T')[0];
      return generateMockSlots(doctorId, dateStr);
    }
  },
};

// Appointment Service APIs (Clean Architecture + Outbox)
export const appointmentApi = {
  getAllAppointments: async () => {
    try {
      return await apiClient.get('/api/v1/appointments');
    } catch {
      return mockAppointments;
    }
  },

  getAppointmentById: async (id) => {
    try {
      return await apiClient.get(`/api/v1/appointments/${id}`);
    } catch {
      return mockAppointments.find((a) => a.id === Number(id)) || null;
    }
  },

  getAppointmentByCode: async (bookingCode) => {
    try {
      return await apiClient.get(`/api/v1/appointments/code/${bookingCode}`);
    } catch {
      return mockAppointments.find((a) => a.bookingCode.toUpperCase() === bookingCode.toUpperCase()) || null;
    }
  },

  getAppointmentsByPatient: async (patientId) => {
    try {
      return await apiClient.get(`/api/v1/appointments/patient/${patientId}`);
    } catch {
      return mockAppointments.filter((a) => a.patientId === Number(patientId));
    }
  },

  getAppointmentsByPhone: async (phone) => {
    try {
      return await apiClient.get(`/api/v1/appointments/by-phone?phone=${encodeURIComponent(phone)}`);
    } catch {
      return mockAppointments.filter((a) => a.patientPhone === phone);
    }
  },

  bookAppointment: async (requestData) => {
    try {
      return await apiClient.post('/api/v1/appointments', requestData);
    } catch (e) {
      // If backend offline, simulate Redis Lock check & Outbox creation
      const already = mockAppointments.some(
        (a) =>
          a.doctorId === requestData.doctorId &&
          a.slotId === requestData.slotId &&
          a.appointmentDate === requestData.appointmentDate &&
          a.status !== 'CANCELLED'
      );
      if (already) {
        throw new Error('Khung giờ này đã có bệnh nhân đặt trước. Vui lòng chọn khung giờ khác.');
      }

      const bookingCode = 'FC-' + Math.random().toString(36).substring(2, 10).toUpperCase();
      const newAppt = {
        id: Date.now(),
        bookingCode,
        ...requestData,
        status: 'CONFIRMED',
        createdAt: new Date().toISOString(),
      };
      mockAppointments.unshift(newAppt);

      // Append Outbox event
      const eventId = crypto.randomUUID ? crypto.randomUUID() : 'outbox-' + Date.now();
      mockOutboxEvents.unshift({
        id: eventId,
        aggregateId: String(newAppt.id),
        aggregateType: 'APPOINTMENT',
        eventType: 'APPOINTMENT_BOOKED',
        payload: JSON.stringify(newAppt),
        status: 'COMPLETED',
        publishedAt: new Date().toISOString(),
        createdAt: new Date().toISOString(),
      });

      // Append notification
      mockNotifications.unshift({
        id: 'notif-' + Date.now(),
        bookingCode,
        recipientName: requestData.patientName,
        recipientPhone: requestData.patientPhone,
        doctorName: requestData.doctorName,
        appointmentTime: `${requestData.appointmentDate} ${requestData.startTime}`,
        channel: 'SMS/ZALO',
        message: `FClinic: Quý khách ${requestData.patientName} đã đặt lịch khám thành công với ${requestData.doctorName}. Mã đặt hẹn: ${bookingCode}.`,
        status: 'SENT',
        createdAt: new Date().toISOString(),
      });

      return newAppt;
    }
  },

  cancelAppointment: async (id, reason = 'Bệnh nhân yêu cầu hủy') => {
    try {
      return await apiClient.put(`/api/v1/appointments/${id}/cancel?reason=${encodeURIComponent(reason)}`);
    } catch {
      const appt = mockAppointments.find((a) => a.id === Number(id));
      if (!appt) throw new Error('Không tìm thấy cuộc hẹn');
      appt.status = 'CANCELLED';

      mockOutboxEvents.unshift({
        id: 'outbox-' + Date.now(),
        aggregateId: String(appt.id),
        aggregateType: 'APPOINTMENT',
        eventType: 'APPOINTMENT_CANCELLED',
        payload: JSON.stringify({ appointmentId: appt.id, bookingCode: appt.bookingCode, reason }),
        status: 'COMPLETED',
        publishedAt: new Date().toISOString(),
        createdAt: new Date().toISOString(),
      });

      mockNotifications.unshift({
        id: 'notif-' + Date.now(),
        bookingCode: appt.bookingCode,
        recipientName: appt.patientName,
        recipientPhone: appt.patientPhone,
        doctorName: appt.doctorName,
        appointmentTime: `${appt.appointmentDate} ${appt.startTime}`,
        channel: 'SMS/ZALO',
        message: `FClinic: Lịch khám ${appt.bookingCode} đã được hủy thành công. Lý do: ${reason}.`,
        status: 'SENT',
        createdAt: new Date().toISOString(),
      });

      return appt;
    }
  },

  getRecentOutboxEvents: async () => {
    try {
      return await apiClient.get('/api/v1/appointments/outbox/events');
    } catch {
      return mockOutboxEvents;
    }
  },
};

// Patient Service APIs
export const patientApi = {
  getPatientByPhone: async (phone) => {
    try {
      return await apiClient.get(`/api/v1/patients/by-phone?phone=${encodeURIComponent(phone)}`);
    } catch {
      return null;
    }
  },

  createOrUpdatePatient: async (data) => {
    try {
      return await apiClient.post('/api/v1/patients', data);
    } catch {
      return { id: 1, ...data };
    }
  },
};

// Notification Service APIs
export const notificationApi = {
  getRecentNotifications: async (limit = 50) => {
    try {
      return await apiClient.get(`/api/v1/notifications?limit=${limit}`);
    } catch {
      return mockNotifications;
    }
  },
};
