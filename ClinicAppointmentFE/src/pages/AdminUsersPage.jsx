import React, { useState, useEffect } from 'react';
import {
  Users,
  Search,
  ShieldCheck,
  Lock,
  Unlock,
  UserCheck,
  UserPlus,
  CheckCircle2,
  AlertCircle,
  RefreshCw,
  Edit2,
  X
} from 'lucide-react';
import { authApi } from '../services/authApi';

export function AdminUsersPage() {
  const [users, setUsers] = useState([]);
  const [roles, setRoles] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  // Filters
  const [keyword, setKeyword] = useState('');
  const [roleFilter, setRoleFilter] = useState('ALL');
  const [activeFilter, setActiveFilter] = useState('ALL');

  // Role edit modal
  const [selectedUser, setSelectedUser] = useState(null);
  const [newRole, setNewRole] = useState('');
  const [isUpdatingRole, setIsUpdatingRole] = useState(false);

  // Create User modal
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [createFullName, setCreateFullName] = useState('');
  const [createUsername, setCreateUsername] = useState('');
  const [createEmail, setCreateEmail] = useState('');
  const [createPhone, setCreatePhone] = useState('');
  const [createPassword, setCreatePassword] = useState('');
  const [createRole, setCreateRole] = useState('ROLE_PATIENT');
  const [isCreatingUser, setIsCreatingUser] = useState(false);

  // Dedicated error messages for modals so they display INSIDE the modal
  const [createErrorMsg, setCreateErrorMsg] = useState('');
  const [roleErrorMsg, setRoleErrorMsg] = useState('');

  const fetchUsers = async () => {
    setIsLoading(true);
    setErrorMsg('');
    try {
      const activeParam = activeFilter === 'ALL' ? undefined : activeFilter === 'true';
      const data = await authApi.adminGetUsers({
        keyword: keyword.trim() || undefined,
        role: roleFilter,
        active: activeParam,
      });
      setUsers(Array.isArray(data) ? data : []);
    } catch (err) {
      setErrorMsg(err.message || 'Không thể tải danh sách người dùng');
    } finally {
      setIsLoading(false);
    }
  };

  const fetchRoles = async () => {
    try {
      const rolesData = await authApi.adminGetRoles();
      setRoles(Array.isArray(rolesData) ? rolesData : []);
    } catch {
      // Fallback standard roles
      setRoles([
        { id: '1', roleName: 'ROLE_ADMIN' },
        { id: '2', roleName: 'ROLE_DOCTOR' },
        { id: '3', roleName: 'ROLE_PATIENT' },
        { id: '4', roleName: 'ROLE_STAFF' },
      ]);
    }
  };

  useEffect(() => {
    fetchUsers();
    fetchRoles();
  }, [roleFilter, activeFilter]);

  const handleToggleStatus = async (user) => {
    const nextStatus = !user.active;
    const confirmText = nextStatus
      ? `Bạn có chắc muốn MỞ KHÓA tài khoản "${user.username}"?`
      : `Bạn có chắc muốn KHÓA tài khoản "${user.username}"? Phiên đăng nhập Keycloak sẽ bị thu hồi ngay lập tức!`;
    if (!window.confirm(confirmText)) return;

    try {
      await authApi.adminToggleUserStatus(user.id, nextStatus);
      setSuccessMsg(`Đã ${nextStatus ? 'mở khóa' : 'khóa'} tài khoản ${user.username} thành công!`);
      fetchUsers();
    } catch (err) {
      setErrorMsg(err.message || 'Thao tác thay đổi trạng thái thất bại');
    }
  };

  const handleOpenRoleModal = (user) => {
    setSelectedUser(user);
    setNewRole(user.role);
    setRoleErrorMsg('');
  };

  const handleUpdateRole = async (e) => {
    e?.preventDefault();
    if (!selectedUser || !newRole) return;
    setIsUpdatingRole(true);
    setRoleErrorMsg('');
    try {
      await authApi.adminUpdateUserRole(selectedUser.id, newRole);
      setSuccessMsg(`Cập nhật vai trò cho ${selectedUser.username} thành "${newRole}" thành công!`);
      setSelectedUser(null);
      fetchUsers();
    } catch (err) {
      setRoleErrorMsg(err.message || 'Cập nhật vai trò thất bại');
    } finally {
      setIsUpdatingRole(false);
    }
  };

  const handleCreateUser = async (e) => {
    e?.preventDefault();
    setIsCreatingUser(true);
    setCreateErrorMsg('');
    try {
      await authApi.adminCreateUser({
        fullName: createFullName.trim(),
        username: createUsername.trim().toLowerCase(),
        email: createEmail.trim().toLowerCase(),
        phoneNumber: createPhone.trim() || undefined,
        password: createPassword,
        role: createRole,
      });
      setSuccessMsg(`Tạo tài khoản "${createUsername}" (${createRole}) thành công!`);
      setShowCreateModal(false);
      // Reset form
      setCreateFullName('');
      setCreateUsername('');
      setCreateEmail('');
      setCreatePhone('');
      setCreatePassword('');
      setCreateRole('ROLE_PATIENT');
      fetchUsers();
    } catch (err) {
      setCreateErrorMsg(err.message || 'Không thể tạo người dùng. Vui lòng kiểm tra lại thông tin.');
    } finally {
      setIsCreatingUser(false);
    }
  };

  const getRoleBadgeClass = (role) => {
    switch (role) {
      case 'ROLE_ADMIN': return 'badge-role admin';
      case 'ROLE_DOCTOR': return 'badge-role doctor';
      case 'ROLE_STAFF': return 'badge-role staff';
      default: return 'badge-role patient';
    }
  };

  return (
    <div className="module-page-container">
      <div className="module-header">
        <div className="module-title-group">
          <ShieldCheck size={28} className="module-icon" />
          <div>
            <h2>Quản trị Người dùng & Phân quyền Hệ thống</h2>
            <p>Quản lý tài khoản, đồng bộ Keycloak Identity Provider và phân quyền truy cập</p>
          </div>
        </div>
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          <button
            type="button"
            onClick={() => {
              setShowCreateModal(true);
              setCreateErrorMsg('');
              setErrorMsg('');
              setSuccessMsg('');
            }}
            className="btn-confirm"
            style={{ display: 'flex', alignItems: 'center', gap: '6px', padding: '8px 16px', borderRadius: '8px', background: '#0d9488', color: '#fff', fontWeight: 600, border: 'none', cursor: 'pointer' }}
          >
            <UserPlus size={16} /> Tạo người dùng mới
          </button>
          <button type="button" onClick={fetchUsers} className="btn-secondary">
            <RefreshCw size={16} /> Làm mới
          </button>
        </div>
      </div>

      {successMsg && (
        <div className="alert-box success mb-4">
          <CheckCircle2 size={16} />
          <span>{successMsg}</span>
        </div>
      )}
      {errorMsg && (
        <div className="alert-box error mb-4">
          <AlertCircle size={16} />
          <span>{errorMsg}</span>
        </div>
      )}

      {/* Filter Bar */}
      <div className="filter-bar">
        <div className="search-box">
          <Search size={18} className="search-icon" />
          <input
            type="text"
            placeholder="Tìm theo tên, username, email, SĐT..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && fetchUsers()}
          />
        </div>

        <div className="select-filters">
          <select value={roleFilter} onChange={(e) => setRoleFilter(e.target.value)}>
            <option value="ALL">Tất cả vai trò</option>
            <option value="ROLE_ADMIN">Quản trị viên (ADMIN)</option>
            <option value="ROLE_DOCTOR">Bác sĩ (DOCTOR)</option>
            <option value="ROLE_STAFF">Tiếp tân (STAFF)</option>
            <option value="ROLE_PATIENT">Bệnh nhân (PATIENT)</option>
          </select>

          <select value={activeFilter} onChange={(e) => setActiveFilter(e.target.value)}>
            <option value="ALL">Tất cả trạng thái</option>
            <option value="true">Đang hoạt động</option>
            <option value="false">Đã bị khóa</option>
          </select>

          <button type="button" onClick={fetchUsers} className="btn-primary">
            Lọc
          </button>
        </div>
      </div>

      {/* Users Table */}
      <div className="table-responsive-wrapper">
        <table className="fclinic-table">
          <thead>
            <tr>
              <th>Họ và tên</th>
              <th>Tài khoản</th>
              <th>Email & Số ĐT</th>
              <th>Vai trò</th>
              <th>Liên kết Hồ sơ</th>
              <th>Trạng thái</th>
              <th className="text-right">Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr>
                <td colSpan={7} className="text-center py-8 text-slate-500">
                  Đang tải dữ liệu người dùng từ hệ thống...
                </td>
              </tr>
            ) : users.length === 0 ? (
              <tr>
                <td colSpan={7} className="text-center py-8 text-slate-500">
                  Không tìm thấy người dùng nào phù hợp với bộ lọc.
                </td>
              </tr>
            ) : (
              users.map((u) => (
                <tr key={u.id} className={!u.active ? 'row-disabled' : ''}>
                  <td>
                    <strong>{u.fullName || 'Chưa cập nhật'}</strong>
                  </td>
                  <td>
                    <code>{u.username}</code>
                  </td>
                  <td>
                    <div>{u.email || '-'}</div>
                    <small className="text-slate-500">{u.phoneNumber || '-'}</small>
                  </td>
                  <td>
                    <span className={getRoleBadgeClass(u.role)}>{u.role}</span>
                  </td>
                  <td>
                    {u.patientId && (
                      <span className="badge-meta patient" title={u.patientId}>
                        Patient: {u.patientId.substring(0, 8)}...
                      </span>
                    )}
                    {u.doctorId && (
                      <span className="badge-meta doctor" title={u.doctorId}>
                        Doctor: {u.doctorId.substring(0, 8)}...
                      </span>
                    )}
                    {!u.patientId && !u.doctorId && <span className="text-slate-400">-</span>}
                  </td>
                  <td>
                    {u.active ? (
                      <span className="status-badge active">Hoạt động</span>
                    ) : (
                      <span className="status-badge locked">Đã khóa</span>
                    )}
                  </td>
                  <td className="text-right actions-cell">
                    <button
                      type="button"
                      className="btn-action edit"
                      title="Đổi vai trò"
                      onClick={() => handleOpenRoleModal(u)}
                    >
                      <Edit2 size={15} />
                    </button>
                    <button
                      type="button"
                      className={`btn-action ${u.active ? 'lock' : 'unlock'}`}
                      title={u.active ? 'Khóa tài khoản và thu hồi token' : 'Mở khóa tài khoản'}
                      onClick={() => handleToggleStatus(u)}
                    >
                      {u.active ? <Lock size={15} /> : <Unlock size={15} />}
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Role Update Modal */}
      {selectedUser && (
        <div className="modal-backdrop">
          <div className="forgot-modal">
            <div className="modal-header">
              <div className="modal-icon-badge">
                <UserCheck size={22} color="#0d9488" />
              </div>
              <h3>Phân quyền người dùng</h3>
              <p>Thay đổi vai trò hệ thống cho tài khoản <strong>{selectedUser.username}</strong></p>
            </div>

            {roleErrorMsg && (
              <div className="alert-box error" style={{ margin: '0 20px 14px', borderRadius: '8px' }}>
                <AlertCircle size={16} />
                <span>{roleErrorMsg}</span>
              </div>
            )}

            <form onSubmit={handleUpdateRole} className="modal-form">
              <div className="form-group">
                <label>Chọn vai trò mới</label>
                <select
                  value={newRole}
                  onChange={(e) => setNewRole(e.target.value)}
                  className="w-full p-2 border rounded-md"
                >
                  <option value="ROLE_PATIENT">ROLE_PATIENT (Bệnh nhân)</option>
                  <option value="ROLE_DOCTOR">ROLE_DOCTOR (Bác sĩ)</option>
                  <option value="ROLE_STAFF">ROLE_STAFF (Tiếp tân quầy)</option>
                  <option value="ROLE_ADMIN">ROLE_ADMIN (Quản trị viên)</option>
                </select>
              </div>

              <div className="modal-actions">
                <button
                  type="button"
                  className="btn-cancel"
                  onClick={() => setSelectedUser(null)}
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={isUpdatingRole}
                  className="btn-confirm"
                >
                  {isUpdatingRole ? 'Đang lưu...' : 'Lưu thay đổi'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Create User Modal */}
      {showCreateModal && (
        <div className="modal-backdrop">
          <div className="forgot-modal" style={{ maxWidth: '480px' }}>
            <div className="modal-header">
              <div className="modal-icon-badge">
                <UserPlus size={22} color="#0d9488" />
              </div>
              <h3>Thêm người dùng mới</h3>
              <p>Tạo tài khoản và đồng bộ trực tiếp vào Keycloak OIDC Identity Provider</p>
            </div>

            {createErrorMsg && (
              <div className="alert-box error" style={{ margin: '0 20px 14px', borderRadius: '8px' }}>
                <AlertCircle size={16} />
                <span>{createErrorMsg}</span>
              </div>
            )}

            <form onSubmit={handleCreateUser} className="modal-form">
              <div className="form-group">
                <label>Họ và tên <span style={{ color: '#ef4444' }}>*</span></label>
                <input
                  type="text"
                  required
                  placeholder="Ví dụ: Bác sĩ Nguyễn Văn C"
                  value={createFullName}
                  onChange={(e) => setCreateFullName(e.target.value)}
                  className="w-full p-2.5 border rounded-lg text-sm"
                />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                <div className="form-group">
                  <label>Tên đăng nhập <span style={{ color: '#ef4444' }}>*</span></label>
                  <input
                    type="text"
                    required
                    placeholder="username..."
                    value={createUsername}
                    onChange={(e) => setCreateUsername(e.target.value)}
                    className="w-full p-2.5 border rounded-lg text-sm"
                  />
                </div>

                <div className="form-group">
                  <label>Vai trò <span style={{ color: '#ef4444' }}>*</span></label>
                  <select
                    value={createRole}
                    onChange={(e) => setCreateRole(e.target.value)}
                    className="w-full p-2.5 border rounded-lg text-sm"
                  >
                    <option value="ROLE_PATIENT">Bệnh nhân (ROLE_PATIENT)</option>
                    <option value="ROLE_DOCTOR">Bác sĩ (ROLE_DOCTOR)</option>
                    <option value="ROLE_STAFF">Tiếp tân quầy (ROLE_STAFF)</option>
                    <option value="ROLE_ADMIN">Quản trị viên (ROLE_ADMIN)</option>
                  </select>
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                <div className="form-group">
                  <label>Email <span style={{ color: '#ef4444' }}>*</span></label>
                  <input
                    type="email"
                    required
                    placeholder="user@fclinic.vn"
                    value={createEmail}
                    onChange={(e) => setCreateEmail(e.target.value)}
                    className="w-full p-2.5 border rounded-lg text-sm"
                  />
                </div>

                <div className="form-group">
                  <label>Số điện thoại</label>
                  <input
                    type="tel"
                    placeholder="0912345678"
                    value={createPhone}
                    onChange={(e) => setCreatePhone(e.target.value)}
                    className="w-full p-2.5 border rounded-lg text-sm"
                  />
                </div>
              </div>

              <div className="form-group">
                <label>Mật khẩu khởi tạo <span style={{ color: '#ef4444' }}>*</span></label>
                <input
                  type="password"
                  required
                  placeholder="Tối thiểu 6 ký tự..."
                  value={createPassword}
                  onChange={(e) => setCreatePassword(e.target.value)}
                  className="w-full p-2.5 border rounded-lg text-sm"
                />
              </div>

              <div className="modal-actions">
                <button
                  type="button"
                  className="btn-cancel"
                  onClick={() => setShowCreateModal(false)}
                >
                  Hủy bỏ
                </button>
                <button
                  type="submit"
                  disabled={isCreatingUser}
                  className="btn-confirm"
                >
                  {isCreatingUser ? 'Đang tạo người dùng...' : 'Tạo tài khoản Keycloak'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

