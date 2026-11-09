import React, { useState } from 'react';
import { UserRound, Calendar, MapPin, Phone, User, CheckCircle2, AlertCircle, ArrowRight, Loader2 } from 'lucide-react';
import { patientApi } from '../appointment/api';
import { authApi } from '../services/authApi';
import { useAuth } from '../context/AuthContext';

export function PatientOnboardingPage({ onComplete }) {
  const { user, updateUserSession } = useAuth();

  const [fullName, setFullName] = useState(user?.fullName || '');
  const [phoneNumber, setPhoneNumber] = useState(user?.phoneNumber || '');
  const [dateOfBirth, setDateOfBirth] = useState('');
  const [gender, setGender] = useState('MALE');
  const [address, setAddress] = useState('');

  const [busy, setBusy] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrorMsg('');
    setSuccessMsg('');

    if (!fullName.trim()) {
      setErrorMsg('Vui lòng nhập họ và tên');
      return;
    }
    if (!phoneNumber.trim()) {
      setErrorMsg('Vui lòng nhập số điện thoại');
      return;
    }
    if (!dateOfBirth) {
      setErrorMsg('Vui lòng chọn ngày sinh');
      return;
    }

    setBusy(true);
    try {
      // 1. Tạo hồ sơ bệnh nhân mới bên patient-service
      const payload = {
        fullName: fullName.trim(),
        phoneNumber: phoneNumber.trim(),
        dateOfBirth: dateOfBirth,
        gender: gender,
        ...(address.trim() ? { address: address.trim() } : {}),
        ...(user?.email ? { email: user.email.trim() } : {}),
      };

      const patient = await patientApi.create(payload);

      // 2. Liên kết patientId vào tài khoản trong auth-service
      const updatedUser = await authApi.linkPatientProfile(patient.id);

      // 3. Cập nhật lại session trong AuthContext
      updateUserSession(updatedUser);

      setSuccessMsg(`Khởi tạo hồ sơ thành công! Mã hồ sơ của bạn là ${patient.patientCode || 'PAT-' + patient.id.slice(0, 8)}`);

      setTimeout(() => {
        if (onComplete) onComplete(patient);
      }, 1200);
    } catch (err) {
      setErrorMsg(err.message || 'Không thể tạo hồ sơ bệnh nhân. Vui lòng kiểm tra lại thông tin.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ maxWidth: '680px', margin: '2rem auto', padding: '0 1rem' }}>
      <div className="page-heading">
        <div>
          <span className="eyebrow">CHÀO MỪNG BẠN ĐẾN VỚI FCLINIC</span>
          <h1>Khởi tạo hồ sơ bệnh nhân</h1>
          <p>
            Vui lòng hoàn tất thông tin người khám bệnh để hệ thống kích hoạt quyền đặt lịch khám và quản lý hồ sơ y tế.
          </p>
        </div>
      </div>

      <section className="panel">
        {errorMsg && (
          <div className="login-alert error" style={{ marginBottom: '1.25rem', display: 'flex', alignItems: 'center', gap: '0.5rem', padding: '0.75rem 1rem', background: '#fef2f2', border: '1px solid #fecaca', borderRadius: '0.5rem', color: '#991b1b' }}>
            <AlertCircle size={18} />
            <span>{errorMsg}</span>
          </div>
        )}

        {successMsg && (
          <div className="login-alert success" style={{ marginBottom: '1.25rem', display: 'flex', alignItems: 'center', gap: '0.5rem', padding: '0.75rem 1rem', background: '#f0fdf4', border: '1px solid #bbf7d0', borderRadius: '0.5rem', color: '#166534' }}>
            <CheckCircle2 size={18} />
            <span>{successMsg}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
          <div>
            <label style={{ display: 'block', fontWeight: 600, marginBottom: '0.35rem', color: '#1e293b' }}>
              Họ và tên bệnh nhân <span style={{ color: '#dc2626' }}>*</span>
            </label>
            <div className="input-with-icon" style={{ position: 'relative' }}>
              <input
                type="text"
                required
                placeholder="Ví dụ: Nguyễn Văn An"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                disabled={busy}
                style={{ width: '100%', padding: '0.65rem 0.75rem', borderRadius: '0.5rem', border: '1px solid #cbd5e1', fontSize: '0.95rem' }}
              />
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div>
              <label style={{ display: 'block', fontWeight: 600, marginBottom: '0.35rem', color: '#1e293b' }}>
                Số điện thoại liên hệ <span style={{ color: '#dc2626' }}>*</span>
              </label>
              <input
                type="tel"
                required
                placeholder="0900000001"
                value={phoneNumber}
                onChange={(e) => setPhoneNumber(e.target.value)}
                disabled={busy}
                style={{ width: '100%', padding: '0.65rem 0.75rem', borderRadius: '0.5rem', border: '1px solid #cbd5e1', fontSize: '0.95rem' }}
              />
            </div>

            <div>
              <label style={{ display: 'block', fontWeight: 600, marginBottom: '0.35rem', color: '#1e293b' }}>
                Ngày sinh <span style={{ color: '#dc2626' }}>*</span>
              </label>
              <input
                type="date"
                required
                max={new Date().toISOString().slice(0, 10)}
                value={dateOfBirth}
                onChange={(e) => setDateOfBirth(e.target.value)}
                disabled={busy}
                style={{ width: '100%', padding: '0.65rem 0.75rem', borderRadius: '0.5rem', border: '1px solid #cbd5e1', fontSize: '0.95rem' }}
              />
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div>
              <label style={{ display: 'block', fontWeight: 600, marginBottom: '0.35rem', color: '#1e293b' }}>
                Giới tính
              </label>
              <select
                value={gender}
                onChange={(e) => setGender(e.target.value)}
                disabled={busy}
                style={{ width: '100%', padding: '0.65rem 0.75rem', borderRadius: '0.5rem', border: '1px solid #cbd5e1', fontSize: '0.95rem', background: '#fff' }}
              >
                <option value="MALE">Nam</option>
                <option value="FEMALE">Nữ</option>
                <option value="OTHER">Khác</option>
              </select>
            </div>

            <div>
              <label style={{ display: 'block', fontWeight: 600, marginBottom: '0.35rem', color: '#1e293b' }}>
                Email (từ tài khoản)
              </label>
              <input
                type="email"
                value={user?.email || ''}
                disabled
                style={{ width: '100%', padding: '0.65rem 0.75rem', borderRadius: '0.5rem', border: '1px solid #e2e8f0', background: '#f8fafc', color: '#64748b', fontSize: '0.95rem' }}
              />
            </div>
          </div>

          <div>
            <label style={{ display: 'block', fontWeight: 600, marginBottom: '0.35rem', color: '#1e293b' }}>
              Địa chỉ liên hệ
            </label>
            <textarea
              rows={3}
              placeholder="Nhập địa chỉ cư trú của bạn..."
              value={address}
              onChange={(e) => setAddress(e.target.value)}
              disabled={busy}
              style={{ width: '100%', padding: '0.65rem 0.75rem', borderRadius: '0.5rem', border: '1px solid #cbd5e1', fontSize: '0.95rem', resize: 'vertical' }}
            />
          </div>

          <div style={{ marginTop: '0.5rem', display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
            <button
              type="submit"
              className="button primary"
              disabled={busy}
              style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem', padding: '0.75rem 1.5rem', fontWeight: 600 }}
            >
              {busy ? (
                <>
                  <Loader2 size={17} className="spin" />
                  Đang khởi tạo hồ sơ...
                </>
              ) : (
                <>
                  Lưu & Tiếp tục đặt lịch
                  <ArrowRight size={17} />
                </>
              )}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}
