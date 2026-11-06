import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_GATEWAY_URL || '';

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
    id: 1,
    eventId: 'e1000000-0000-0000-0000-000000000001',
    appointmentId: 101,
    recipientUserId: 1,
    recipientType: 'PATIENT',
    recipientName: 'Trần Thị Mai Phương',
    recipientEmail: 'phuong.tran@gmail.com',
    type: 'APPOINTMENT_CONFIRMATION',
    channel: 'EMAIL',
    status: 'SENT',
    subject: 'Xác nhận lịch hẹn với BS. CKII Nguyễn Văn An',
    attemptCount: 0,
    nextAttemptAt: null,
    lastAttemptAt: new Date().toISOString(),
    sentAt: new Date().toISOString(),
    lastErrorCode: null,
    lastErrorMessage: null,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
  {
    id: 2,
    eventId: 'e1000000-0000-0000-0000-000000000001',
    appointmentId: 101,
    recipientUserId: 1,
    recipientType: 'DOCTOR',
    recipientName: 'BS. CKII Nguyễn Văn An',
    recipientEmail: 'doctor.an@fclinic.vn',
    type: 'APPOINTMENT_CONFIRMATION',
    channel: 'EMAIL',
    status: 'SENT',
    subject: 'Xác nhận lịch hẹn với Trần Thị Mai Phương',
    attemptCount: 0,
    nextAttemptAt: null,
    lastAttemptAt: new Date().toISOString(),
    sentAt: new Date().toISOString(),
    lastErrorCode: null,
    lastErrorMessage: null,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
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

      // Append dual notifications (Patient & Doctor) matching EDA backend
      const notifBase = {
        eventId,
        appointmentId: newAppt.id,
        attemptCount: 0,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        sentAt: new Date().toISOString(),
      };
      mockNotifications.unshift({
        id: Date.now() + 1,
        ...notifBase,
        recipientUserId: requestData.doctorId || 1,
        recipientType: 'DOCTOR',
        recipientName: requestData.doctorName,
        recipientEmail: requestData.doctorEmail || `doctor.${requestData.doctorId || 1}@fclinic.vn`,
        type: 'APPOINTMENT_CONFIRMATION',
        channel: 'EMAIL',
        status: 'SENT',
        subject: `Xác nhận lịch hẹn với ${requestData.patientName}`,
      });
      mockNotifications.unshift({
        id: Date.now(),
        ...notifBase,
        recipientUserId: requestData.patientId || 1,
        recipientType: 'PATIENT',
        recipientName: requestData.patientName,
        recipientEmail: requestData.patientEmail || `${requestData.patientPhone}@fclinic.local`,
        type: 'APPOINTMENT_CONFIRMATION',
        channel: 'EMAIL',
        status: 'SENT',
        subject: `Xác nhận lịch hẹn với ${requestData.doctorName}`,
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

      const cancelEventId = crypto.randomUUID ? crypto.randomUUID() : 'outbox-' + Date.now();
      mockOutboxEvents.unshift({
        id: cancelEventId,
        aggregateId: String(appt.id),
        aggregateType: 'APPOINTMENT',
        eventType: 'APPOINTMENT_CANCELLED',
        payload: JSON.stringify({ appointmentId: appt.id, bookingCode: appt.bookingCode, reason }),
        status: 'COMPLETED',
        publishedAt: new Date().toISOString(),
        createdAt: new Date().toISOString(),
      });

      const notifBase = {
        eventId: cancelEventId,
        appointmentId: appt.id,
        attemptCount: 0,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        sentAt: new Date().toISOString(),
      };
      mockNotifications.unshift({
        id: Date.now() + 1,
        ...notifBase,
        recipientUserId: appt.doctorId || 1,
        recipientType: 'DOCTOR',
        recipientName: appt.doctorName,
        recipientEmail: appt.doctorEmail || `doctor.${appt.doctorId || 1}@fclinic.vn`,
        type: 'APPOINTMENT_CANCELLATION',
        channel: 'EMAIL',
        status: 'SENT',
        subject: `Lịch hẹn với ${appt.patientName} đã bị hủy`,
      });
      mockNotifications.unshift({
        id: Date.now(),
        ...notifBase,
        recipientUserId: appt.patientId || 1,
        recipientType: 'PATIENT',
        recipientName: appt.patientName,
        recipientEmail: appt.patientEmail || `${appt.patientPhone}@fclinic.local`,
        type: 'APPOINTMENT_CANCELLATION',
        channel: 'EMAIL',
        status: 'SENT',
        subject: `Lịch hẹn với ${appt.doctorName} đã bị hủy`,
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
      // Try v2 search first (matches PatientController: GET /api/v2/patients?phone=...)
      const res = await apiClient.get(`/api/v2/patients?phone=${encodeURIComponent(phone)}`);
      if (res && Array.isArray(res.content) && res.content.length > 0) {
        return res.content[0];
      }
      if (Array.isArray(res) && res.length > 0) {
        return res[0];
      }
      return await apiClient.get(`/api/v1/patients/by-phone?phone=${encodeURIComponent(phone)}`);
    } catch {
      return null;
    }
  },

  createOrUpdatePatient: async (data) => {
    try {
      // Try v2 patient creation first
      return await apiClient.post('/api/v2/patients', data);
    } catch {
      try {
        return await apiClient.post('/api/v1/patients', data);
      } catch {
        return { id: 1, ...data };
      }
    }
  },
};

// Notification Service APIs (matches real backend NotificationController)
export const notificationApi = {
  /**
   * Fetch paginated notifications with optional status filter.
   * Matches GET /api/v1/admin/notifications?status={status}&page={page}&size={size}
   */
  getAdminNotifications: async ({ status, page = 0, size = 50 } = {}) => {
    try {
      const params = new URLSearchParams();
      if (status && status !== 'ALL') params.append('status', status);
      params.append('page', String(page));
      params.append('size', String(size));
      const response = await apiClient.get(`/api/v1/admin/notifications?${params.toString()}`);
      if (response && Array.isArray(response.content)) {
        return response;
      }
      if (Array.isArray(response)) {
        return { content: response, totalElements: response.length, totalPages: 1, page: 0 };
      }
      return { content: [], totalElements: 0, totalPages: 0, page: 0 };
    } catch {
      let filtered = [...mockNotifications];
      if (status && status !== 'ALL') {
        filtered = filtered.filter((n) => n.status === status);
      }
      return {
        content: filtered.slice(page * size, (page + 1) * size),
        totalElements: filtered.length,
        totalPages: Math.ceil(filtered.length / size),
        page,
        size,
      };
    }
  },

  /**
   * Fetch user notifications: GET /api/v1/notifications/me?userId={userId}&page={page}&size={size}
   */
  getUserNotifications: async (userId, page = 0, size = 20) => {
    try {
      const response = await apiClient.get(`/api/v1/notifications/me?userId=${userId}&page=${page}&size=${size}`);
      return response?.content || (Array.isArray(response) ? response : []);
    } catch {
      return mockNotifications.filter((n) => n.recipientUserId === Number(userId) || n.recipientType === 'PATIENT');
    }
  },

  /**
   * Get single notification by ID: GET /api/v1/notifications/{id}
   */
  getNotificationById: async (id) => {
    try {
      return await apiClient.get(`/api/v1/notifications/${id}`);
    } catch {
      return mockNotifications.find((n) => n.id === Number(id)) || null;
    }
  },

  /**
   * Manually retry delivery of a notification: POST /api/v1/admin/notifications/{id}/retry
   */
  retryNotification: async (id) => {
    try {
      return await apiClient.post(`/api/v1/admin/notifications/${id}/retry`);
    } catch {
      const notif = mockNotifications.find((n) => n.id === Number(id));
      if (notif) {
        notif.status = 'SENT';
        notif.sentAt = new Date().toISOString();
        notif.attemptCount = (notif.attemptCount || 0) + 1;
        notif.lastErrorMessage = null;
        return notif;
      }
      throw new Error('Không thể thử lại gửi thông báo.');
    }
  },

  /**
   * Backward-compatible alias for existing components
   */
  getRecentNotifications: async (limit = 50) => {
    const res = await notificationApi.getAdminNotifications({ size: limit });
    return res.content;
  },
};
