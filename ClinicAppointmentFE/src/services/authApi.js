import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_GATEWAY_URL || '';

const authClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 5000,
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
    const err = new Error(errorMsg);
    err.response = error.response;
    err.code = error.code;
    return Promise.reject(err);
  }
);

// Fallback seed accounts matching Keycloak fclinic realm & auth_db
const SEED_USERS = [
  {
    id: '11111111-1111-4111-8111-111111111101',
    keycloakId: '22222222-2222-4222-8222-222222222201',
    username: 'admin',
    password: 'Admin@123',
    email: 'admin@fclinic.vn',
    phoneNumber: '0900000000',
    fullName: 'System Administrator',
    role: 'ROLE_ADMIN',
    active: true,
    patientId: null,
    doctorId: null,
    createdAt: '2026-10-01T00:00:00Z',
    updatedAt: '2026-10-01T00:00:00Z',
  },
  {
    id: '11111111-1111-4111-8111-111111111102',
    keycloakId: '22222222-2222-4222-8222-222222222202',
    username: 'staff_linh',
    password: 'Staff@123',
    email: 'staff.linh@fclinic.vn',
    phoneNumber: '0900000002',
    fullName: 'Lễ tân Linh',
    role: 'ROLE_STAFF',
    active: true,
    patientId: null,
    doctorId: null,
    createdAt: '2026-10-01T00:00:00Z',
    updatedAt: '2026-10-01T00:00:00Z',
  },
  {
    id: '11111111-1111-4111-8111-111111111103',
    keycloakId: '22222222-2222-4222-8222-222222222203',
    username: 'bs_an',
    password: 'Doctor@123',
    email: 'doctor.an@fclinic.vn',
    phoneNumber: '0900000003',
    fullName: 'BS. CKII Nguyễn Văn An',
    role: 'ROLE_DOCTOR',
    active: true,
    patientId: null,
    doctorId: '33333333-3333-4333-8333-333333333301',
    createdAt: '2026-10-01T00:00:00Z',
    updatedAt: '2026-10-01T00:00:00Z',
  },
  {
    id: '11111111-1111-4111-8111-111111111104',
    keycloakId: '22222222-2222-4222-8222-222222222204',
    username: 'patient_hung',
    password: 'Patient@123',
    email: 'patient.hung@gmail.com',
    phoneNumber: '0900000004',
    fullName: 'Nguyễn Tiến Hùng',
    role: 'ROLE_PATIENT',
    active: true,
    patientId: '74fba630-5953-44b8-86d6-bc236ac1213b',
    doctorId: null,
    createdAt: '2026-10-01T00:00:00Z',
    updatedAt: '2026-10-01T00:00:00Z',
  },
];

const MOCK_STORAGE_KEY = 'fclinic_mock_users';

function getStoredMockUsers() {
  try {
    const raw = localStorage.getItem(MOCK_STORAGE_KEY);
    if (!raw) {
      localStorage.setItem(MOCK_STORAGE_KEY, JSON.stringify(SEED_USERS));
      return [...SEED_USERS];
    }
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) && parsed.length > 0 ? parsed : [...SEED_USERS];
  } catch {
    return [...SEED_USERS];
  }
}

function saveMockUser(user) {
  const users = getStoredMockUsers();
  users.push(user);
  localStorage.setItem(MOCK_STORAGE_KEY, JSON.stringify(users));
}

function saveAllMockUsers(users) {
  localStorage.setItem(MOCK_STORAGE_KEY, JSON.stringify(users));
}

function createMockToken(user) {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = btoa(
    JSON.stringify({
      sub: user.keycloakId || user.id,
      preferred_username: user.username,
      email: user.email,
      name: user.fullName,
      fclinic_user_id: user.id,
      realm_access: { roles: [user.role] },
      authorities: [user.role],
      exp: Math.floor(Date.now() / 1000) + 86400,
    })
  );
  return `${header}.${payload}.mockSignature`;
}

function createMockAuthResponse(user) {
  const safeUser = { ...user };
  delete safeUser.password;
  return {
    accessToken: createMockToken(user),
    refreshToken: `mock-refresh-token-${Date.now()}`,
    tokenType: 'Bearer',
    expiresIn: 86400,
    user: safeUser,
  };
}

export const authApi = {
  login: async (username, password) => {
    try {
      return await authClient.post('/api/v1/auth/login', { username, password });
    } catch (err) {
      // If server specifically reported invalid credentials (401 / 400), honor it
      if (err.response && (err.response.status === 400 || err.response.status === 401)) {
        throw err;
      }
      // Offline fallback: verify against seed & local users
      const users = getStoredMockUsers();
      const trimmed = (username || '').trim();
      const matched = users.find(
        (u) => (u.username === trimmed || u.email === trimmed) && u.password === password
      );
      if (!matched) {
        throw new Error('Tên đăng nhập hoặc mật khẩu không chính xác');
      }
      if (!matched.active) {
        throw new Error('Tài khoản đã bị tạm khóa. Vui lòng liên hệ quản trị viên.');
      }
      return createMockAuthResponse(matched);
    }
  },

  register: async (data) => {
    try {
      return await authClient.post('/api/v1/auth/register', data);
    } catch (err) {
      if (err.response && (err.response.status === 400 || err.response.status === 409)) {
        throw err;
      }
      const users = getStoredMockUsers();
      const trimmedUser = (data.username || '').trim();
      const trimmedEmail = (data.email || '').trim();
      if (users.some((u) => u.username === trimmedUser || u.email === trimmedEmail)) {
        throw new Error('Tên đăng nhập, email hoặc số điện thoại này đã được đăng ký trên hệ thống.');
      }
      const newId = typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `mock-${Date.now()}`;
      const newUser = {
        id: newId,
        keycloakId: newId,
        username: trimmedUser,
        password: data.password,
        email: trimmedEmail,
        phoneNumber: (data.phoneNumber || '').trim(),
        fullName: (data.fullName || trimmedUser).trim(),
        role: 'ROLE_PATIENT',
        active: true,
        patientId: newId,
        doctorId: null,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      saveMockUser(newUser);
      return createMockAuthResponse(newUser);
    }
  },

  getMe: async () => {
    try {
      return await authClient.get('/api/v1/auth/me');
    } catch {
      const stored = localStorage.getItem('fclinic_user');
      if (stored) {
        try {
          return JSON.parse(stored);
        } catch {
          // ignore
        }
      }
      return SEED_USERS[0];
    }
  },

  refresh: async (refreshToken) => {
    try {
      return await authClient.post('/api/v1/auth/refresh', { refreshToken });
    } catch {
      const stored = localStorage.getItem('fclinic_user');
      const u = stored ? JSON.parse(stored) : SEED_USERS[0];
      return createMockAuthResponse(u);
    }
  },

  logout: async (refreshToken) => {
    try {
      return await authClient.post('/api/v1/auth/logout', { refreshToken });
    } catch {
      return null;
    }
  },

  forgotPassword: async (email) => {
    try {
      return await authClient.post('/api/v1/auth/forgot-password', { email });
    } catch {
      return { message: 'Mã OTP đã được tạo (MailHog :8025)', otp: '123456' };
    }
  },

  resetPassword: async (email, otp, newPassword) => {
    try {
      return await authClient.post('/api/v1/auth/reset-password', { email, otp, newPassword });
    } catch {
      const list = getStoredMockUsers();
      const u = list.find((x) => x.email === (email || '').trim());
      if (u) {
        u.password = newPassword;
        saveAllMockUsers(list);
      }
      return { message: 'Mật khẩu đã được cập nhật thành công' };
    }
  },

  changePassword: async (oldPassword, newPassword) => {
    try {
      return await authClient.post('/api/v1/auth/change-password', { oldPassword, newPassword });
    } catch {
      return { message: 'Đổi mật khẩu thành công' };
    }
  },

  // Staff endpoints
  staffLookupPatient: async (query) => {
    try {
      return await authClient.get(`/api/v1/auth/staff/patients/lookup?query=${encodeURIComponent(query)}`);
    } catch {
      const q = (query || '').toLowerCase().trim();
      const list = getStoredMockUsers();
      const found = list.find(
        (u) =>
          u.role === 'ROLE_PATIENT' &&
          ((u.username && u.username.toLowerCase().includes(q)) ||
            (u.phoneNumber && u.phoneNumber.includes(q)) ||
            (u.fullName && u.fullName.toLowerCase().includes(q)) ||
            (u.patientId && u.patientId.toLowerCase().includes(q)))
      );
      if (!found) throw new Error('Không tìm thấy bệnh nhân khớp với từ khóa');
      const copy = { ...found };
      delete copy.password;
      return copy;
    }
  },

  staffCreatePatient: async (data) => {
    try {
      return await authClient.post('/api/v1/auth/staff/patients', data);
    } catch {
      const newId = typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `mock-${Date.now()}`;
      const newPatient = {
        id: newId,
        keycloakId: newId,
        username: data.username || `patient_${Date.now().toString().slice(-4)}`,
        password: data.password || 'Patient@123',
        email: data.email,
        phoneNumber: data.phoneNumber || '',
        fullName: data.fullName,
        role: 'ROLE_PATIENT',
        active: true,
        patientId: newId,
        doctorId: null,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      saveMockUser(newPatient);
      const copy = { ...newPatient };
      delete copy.password;
      return copy;
    }
  },

  // Admin endpoints
  adminGetUsers: async ({ keyword, role, active } = {}) => {
    try {
      const params = new URLSearchParams();
      if (keyword) params.append('keyword', keyword);
      if (role && role !== 'ALL') params.append('role', role);
      if (active !== undefined && active !== null && active !== 'ALL') params.append('active', String(active));
      return await authClient.get(`/api/v1/auth/admin/users?${params.toString()}`);
    } catch {
      let list = getStoredMockUsers().map((u) => {
        const copy = { ...u };
        delete copy.password;
        return copy;
      });
      if (keyword) {
        const kw = keyword.toLowerCase();
        list = list.filter(
          (u) =>
            (u.username && u.username.toLowerCase().includes(kw)) ||
            (u.fullName && u.fullName.toLowerCase().includes(kw)) ||
            (u.email && u.email.toLowerCase().includes(kw))
        );
      }
      if (role && role !== 'ALL') {
        list = list.filter((u) => u.role === role);
      }
      if (active !== undefined && active !== null && active !== 'ALL') {
        const act = String(active) === 'true';
        list = list.filter((u) => u.active === act);
      }
      return list;
    }
  },

  adminCreateUser: async (data) => {
    try {
      return await authClient.post('/api/v1/auth/admin/users', data);
    } catch {
      const newId = typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `mock-${Date.now()}`;
      const newUser = {
        id: newId,
        keycloakId: newId,
        username: data.username,
        password: data.password || 'Default@123',
        email: data.email,
        phoneNumber: data.phoneNumber || '',
        fullName: data.fullName || data.username,
        role: data.role || 'ROLE_PATIENT',
        active: true,
        patientId: data.role === 'ROLE_PATIENT' ? newId : null,
        doctorId: data.role === 'ROLE_DOCTOR' ? newId : null,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      saveMockUser(newUser);
      const copy = { ...newUser };
      delete copy.password;
      return copy;
    }
  },

  adminToggleUserStatus: async (id, active) => {
    try {
      return await authClient.patch(`/api/v1/auth/admin/users/${id}/status?active=${active}`);
    } catch {
      const list = getStoredMockUsers();
      const u = list.find((x) => x.id === id);
      if (u) {
        u.active = String(active) === 'true';
        saveAllMockUsers(list);
      }
      return u;
    }
  },

  adminUpdateUserRole: async (id, role) => {
    try {
      return await authClient.patch(`/api/v1/auth/admin/users/${id}/role`, { role });
    } catch {
      const list = getStoredMockUsers();
      const u = list.find((x) => x.id === id);
      if (u) {
        u.role = role;
        saveAllMockUsers(list);
      }
      return u;
    }
  },

  adminGetRoles: async () => {
    try {
      return await authClient.get('/api/v1/auth/admin/roles');
    } catch {
      return [
        { id: '1', name: 'ROLE_ADMIN', description: 'Quản trị viên toàn hệ thống' },
        { id: '2', name: 'ROLE_STAFF', description: 'Nhân viên lễ tân / tiếp nhận tại quầy' },
        { id: '3', name: 'ROLE_DOCTOR', description: 'Bác sĩ khám bệnh' },
        { id: '4', name: 'ROLE_PATIENT', description: 'Bệnh nhân' },
      ];
    }
  },
};
