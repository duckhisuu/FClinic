import { apiClient as authClient } from '../appointment/api';

export const authApi = {
  getPatientCapabilities: () => authClient.get('/api/v1/auth/me/patient-capabilities'),
  login: async (username, password) => {
    return await authClient.post('/api/v1/auth/login', { username, password });
  },

  register: async (data) => {
    return await authClient.post('/api/v1/auth/register', data);
  },

  getMe: async () => {
    return await authClient.get('/api/v1/auth/me');
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

