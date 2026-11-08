import React, { useState } from 'react';
import {
  UserSearch,
  UserPlus,
  CheckCircle2,
  AlertCircle,
  Phone,
  Mail,
  User,
  CalendarCheck,
  Building2
} from 'lucide-react';
import { authApi } from '../services/authApi';

export function StaffWalkinPage({ onSelectPatientForBooking }) {
  const [searchQuery, setSearchQuery] = useState('');
  const [isSearching, setIsSearching] = useState(false);
  const [searchResult, setSearchResult] = useState(null);
  const [searchError, setSearchError] = useState('');

  // Create form state
  const [fullName, setFullName] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [email, setEmail] = useState('');
  const [isCreating, setIsCreating] = useState(false);
  const [createSuccess, setCreateSuccess] = useState('');
  const [createError, setCreateError] = useState('');

  const handleSearch = async (e) => {
    e?.preventDefault();
    if (!searchQuery.trim()) return;
    setIsSearching(true);
    setSearchError('');
    setSearchResult(null);
    try {
      const patient = await authApi.staffLookupPatient(searchQuery.trim());
      setSearchResult(patient);
    } catch (err) {
      setSearchError('Không tìm thấy bệnh nhân với thông tin trên. Bạn có thể tạo hồ sơ mới bên dưới.');
    } finally {
      setIsSearching(false);
    }
  };

  const handleCreate = async (e) => {
    e?.preventDefault();
    setIsCreating(true);
    setCreateSuccess('');
    setCreateError('');
    try {
      const newPatient = await authApi.staffCreatePatient({
        fullName: fullName.trim(),
        phoneNumber: phoneNumber.trim(),
        email: email.trim() || undefined,
      });
      setCreateSuccess(`Đã tạo hồ sơ bệnh nhân thành công! Mã Bệnh nhân (UUID): ${newPatient.patientId || newPatient.id}`);
      setSearchResult(newPatient);
      setFullName('');
      setPhoneNumber('');
      setEmail('');
    } catch (err) {
      setCreateError(err.message || 'Tạo hồ sơ bệnh nhân thất bại. Vui lòng kiểm tra lại.');
    } finally {
      setIsCreating(false);
    }
  };

  return (
    <div className="module-page-container">
      <div className="module-header">
        <div className="module-title-group">
          <Building2 size={28} className="module-icon" />
          <div>
            <h2>Tiếp nhận bệnh nhân tại quầy</h2>
            <p>Tra cứu hoặc đăng ký hồ sơ bệnh nhân đến khám trực tiếp tại phòng khám</p>
          </div>
        </div>
      </div>

      <div className="walkin-grid">
        {/* Tra cứu nhanh */}
        <div className="card-box">
          <div className="card-box-header">
            <UserSearch size={20} />
            <h3>1. Tra cứu bệnh nhân</h3>
          </div>
          <p className="card-desc">Nhập số điện thoại, email hoặc tên đăng nhập của bệnh nhân:</p>

          <form onSubmit={handleSearch} className="search-form-row">
            <input
              type="text"
              placeholder="Nhập số điện thoại hoặc email..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="search-input"
            />
            <button type="submit" disabled={isSearching} className="btn-primary">
              {isSearching ? 'Đang tìm...' : 'Tra cứu'}
            </button>
          </form>

          {searchError && (
            <div className="alert-box error mt-4">
              <AlertCircle size={16} />
              <span>{searchError}</span>
            </div>
          )}

          {searchResult && (
            <div className="patient-result-card mt-4">
              <div className="result-badge">Đã tìm thấy hồ sơ</div>
              <h4>{searchResult.fullName || searchResult.username}</h4>
              <div className="result-details">
                <p><Phone size={14} /> <strong>SĐT:</strong> {searchResult.phoneNumber || 'Chưa cập nhật'}</p>
                <p><Mail size={14} /> <strong>Email:</strong> {searchResult.email || 'Chưa cập nhật'}</p>
                <p><User size={14} /> <strong>Patient ID:</strong> <code className="uuid-code">{searchResult.patientId || searchResult.id}</code></p>
              </div>
              {onSelectPatientForBooking && (
                <button
                  type="button"
                  className="btn-book-for-patient"
                  onClick={() => onSelectPatientForBooking(searchResult)}
                >
                  <CalendarCheck size={16} />
                  Đặt lịch khám cho bệnh nhân này
                </button>
              )}
            </div>
          )}
        </div>

        {/* Đăng ký tại quầy */}
        <div className="card-box">
          <div className="card-box-header">
            <UserPlus size={20} />
            <h3>2. Tạo hồ sơ bệnh nhân mới tại quầy</h3>
          </div>
          <p className="card-desc">Dành cho bệnh nhân mới chưa từng có tài khoản trên hệ thống:</p>

          {createSuccess && (
            <div className="alert-box success mb-4">
              <CheckCircle2 size={16} />
              <span>{createSuccess}</span>
            </div>
          )}
          {createError && (
            <div className="alert-box error mb-4">
              <AlertCircle size={16} />
              <span>{createError}</span>
            </div>
          )}

          <form onSubmit={handleCreate} className="create-patient-form">
            <div className="form-group">
              <label>Họ và tên bệnh nhân <span className="req">*</span></label>
              <input
                type="text"
                required
                placeholder="Ví dụ: Trần Văn Nam"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
              />
            </div>

            <div className="form-group">
              <label>Số điện thoại liên hệ <span className="req">*</span></label>
              <input
                type="tel"
                required
                placeholder="09xxxxxxxx"
                value={phoneNumber}
                onChange={(e) => setPhoneNumber(e.target.value)}
              />
            </div>

            <div className="form-group">
              <label>Địa chỉ Email (tùy chọn để nhận kết quả)</label>
              <input
                type="email"
                placeholder="patient@fclinic.vn"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </div>

            <button type="submit" disabled={isCreating} className="btn-success">
              <UserPlus size={16} />
              {isCreating ? 'Đang tạo hồ sơ...' : 'Tạo hồ sơ bệnh nhân tại quầy'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}

