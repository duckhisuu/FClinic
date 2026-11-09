// Existing screens share the canonical live HTTP adapter. No offline success.
import { apiClient, doctorApi as doctors, appointmentApi as appointments, patientApi as patients } from '../appointment/api';
export { apiClient };
export const doctorApi = { ...doctors, getDoctorById: id => apiClient.get('/api/v1/doctors/' + id), createDoctor: body => apiClient.post('/api/v1/doctors', body) };
const unsupported = () => Promise.reject(new Error('Vui lòng sử dụng luồng Quản lý lịch hẹn hiện hành.'));
export const appointmentApi = {
 getAllAppointments: () => appointments.list({ page: 0, size: 20 }).then(p => p.content),
 getAppointmentById: appointments.detail,
 getAppointmentsByPatient: patientId => appointments.list({ patientId, page: 0, size: 20 }).then(p => p.content),
 getAppointmentByCode: unsupported, getAppointmentsByPhone: unsupported, bookAppointment: unsupported, cancelAppointment: unsupported,
 getRecentOutboxEvents: () => apiClient.get('/api/v1/outbox/events'),
};
export const patientApi = {
 getPatientByPhone: phoneNumber => patients.search({ query: phoneNumber, page: 0, size: 10 }).then(p => p.content[0] || null),
 createOrUpdatePatient: unsupported,
};
export const notificationApi = {
 getAdminNotifications: (params = {}) => apiClient.get('/api/v1/admin/notifications', { params }),
 getUserNotifications: () => Promise.reject(new Error('Notification chưa hỗ trợ danh tính UUID; chưa thể tải thông báo cá nhân an toàn.')),
 getNotificationById: id => apiClient.get('/api/v1/notifications/' + id),
 retryNotification: id => apiClient.post('/api/v1/admin/notifications/' + id + '/retry'),
 getRecentNotifications: size => notificationApi.getAdminNotifications({ size }).then(p => p.content),
};
