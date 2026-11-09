import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_GATEWAY_URL || '';

const authClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 90000,
  headers: {
    'Content-Type': 'application/json',
  },
});

authClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('fclinic_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

authClient.interceptors.response.use(
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
      'Không thể kết nối đến máy chủ xác thực';
    return Promise.reject(new Error(errorMsg));
  }
);

export const authApi = {
  login: async (username, password) => {
    return await authClient.post('/api/v1/auth/login', { username, password });
  },

  register: async (data) => {
    return await authClient.post('/api/v1/auth/register', data);
  },

  getMe: async () => {
    return await authClient.get('/api/v1/auth/me');
  },

  linkPatientProfile: async (patientId) => {
    return await authClient.put('/api/v1/auth/me/patient-profile', { patientId });
  },

  refresh: async (refreshToken) => {
    return await authClient.post('/api/v1/auth/refresh', { refreshToken });
  },

  logout: async (refreshToken) => {
    try {
      return await authClient.post('/api/v1/auth/logout', { refreshToken });
    } catch {
      return null;
    }
  },

  forgotPassword: async (email) => {
    return await authClient.post('/api/v1/auth/forgot-password', { email });
  },

  resetPassword: async (email, otp, newPassword) => {
    return await authClient.post('/api/v1/auth/reset-password', { email, otp, newPassword });
  },

  changePassword: async (oldPassword, newPassword) => {
    return await authClient.post('/api/v1/auth/change-password', { oldPassword, newPassword });
  },

  // Staff endpoints
  staffLookupPatient: async (query) => {
    return await authClient.get(`/api/v1/auth/staff/patients/lookup?query=${encodeURIComponent(query)}`);
  },

  staffCreatePatient: async (data) => {
    return await authClient.post('/api/v1/auth/staff/patients', data);
  },

  // Admin endpoints
  adminGetUsers: async ({ keyword, role, active } = {}) => {
    const params = new URLSearchParams();
    if (keyword) params.append('keyword', keyword);
    if (role && role !== 'ALL') params.append('role', role);
    if (active !== undefined && active !== null && active !== 'ALL') params.append('active', String(active));
    return await authClient.get(`/api/v1/auth/admin/users?${params.toString()}`);
  },

  adminCreateUser: async (data) => {
    return await authClient.post('/api/v1/auth/admin/users', data);
  },

  adminToggleUserStatus: async (id, active) => {
    return await authClient.patch(`/api/v1/auth/admin/users/${id}/status?active=${active}`);
  },

  adminUpdateUserRole: async (id, role) => {
    return await authClient.patch(`/api/v1/auth/admin/users/${id}/role`, { role });
  },

  adminGetRoles: async () => {
    return await authClient.get('/api/v1/auth/admin/roles');
  },
};

