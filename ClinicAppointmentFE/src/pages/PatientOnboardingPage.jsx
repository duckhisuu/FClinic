import React, { useEffect, useRef, useState } from 'react';
import { UserRound, Calendar, MapPin, Phone, User, CheckCircle2, AlertCircle, ArrowRight, Loader2 } from 'lucide-react';

import { authApi } from '../services/authApi';
import { useAuth } from '../context/AuthContext';
import { today } from '../appointment/ui';

export function PatientOnboardingPage({ onComplete }) {
  const { user, sessionKey, updateUserSession } = useAuth();

  const [fullName, setFullName] = useState(user?.fullName || '');
  const [phoneNumber, setPhoneNumber] = useState(user?.phoneNumber || '');
  const [dateOfBirth, setDateOfBirth] = useState('');
  const [gender, setGender] = useState('MALE');
  const [address, setAddress] = useState('');

  const [busy, setBusy] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [successMsg, setSuccessMsg] = useState('');
  const [restoring, setRestoring] = useState(true);
  const [ready, setReady] = useState(false);
  const [pending, setPending] = useState(null);
  const [revision, setRevision] = useState(0);
  const epoch = useRef(0);
  const running = useRef(false);
  const identity = useRef({ id: user?.id, sessionKey });
  identity.current = { id: user?.id, sessionKey };
  const sameSession = (id, key, ticket) => {
    let stored; try { stored = JSON.parse(localStorage.getItem('fclinic_user') || 'null'); } catch { return false; }
    return ticket === epoch.current && identity.current.id === id && identity.current.sessionKey === key
      && stored?.id === id && (localStorage.getItem('fclinic_session_id') || null) === (key || null);
  };

  useEffect(() => {
    const ticket = ++epoch.current, id = user?.id, key = sessionKey;
    running.current = false; setBusy(false); setRestoring(true); setReady(false);
    setPending(null); setErrorMsg(''); setSuccessMsg('');
    setFullName(user?.fullName || ''); setPhoneNumber(user?.phoneNumber || '');
    setDateOfBirth(''); setGender('MALE'); setAddress('');
    sessionStorage.removeItem('fclinic.pending.patient');
    authApi.getPatientOnboarding().then(state => {
      if (!sameSession(id, key, ticket)) return;
      if (state.user?.id !== id) throw new Error('Phiên tài khoản đã thay đổi. Vui lòng đăng nhập lại.');
      if (state.user.patientId) {
        if (updateUserSession(state.user) !== false) onComplete?.();
        return;
      }
      if (state.profile) {
        setPending(state.profile); setFullName(state.profile.fullName);
        setPhoneNumber(state.profile.phoneNumber); setDateOfBirth(state.profile.dateOfBirth);
        setGender(state.profile.gender); setAddress(state.profile.address || '');
      }
      setReady(true);
    }).catch(err => { if (sameSession(id, key, ticket)) setErrorMsg(err.message || 'Không thể tải tiến trình khởi tạo hồ sơ.'); })
      .finally(() => { if (sameSession(id, key, ticket)) setRestoring(false); });
    return () => { ++epoch.current; };
  }, [user?.id, sessionKey, revision]);

  const handleSubmit = async e => {
    e.preventDefault();
    if (running.current || !ready) return;
    const id = user?.id, key = sessionKey, ticket = epoch.current;
    if (!sameSession(id, key, ticket)) return;
    const payload = pending || {
      fullName: fullName.trim(), phoneNumber: phoneNumber.trim(), dateOfBirth,
      gender, address: address.trim() || null,
    };
    running.current = true; setBusy(true); setPending(payload); setErrorMsg(''); setSuccessMsg('');
    try {
      const updated = await authApi.onboardPatient(payload);
      if (!sameSession(id, key, ticket)) return;
      if (updated.id !== id || !updated.patientId) throw new Error('Máy chủ chưa xác nhận liên kết hồ sơ. Vui lòng thử lại.');
      setSuccessMsg('Đã hoàn tất hồ sơ. Bạn có thể đặt lịch khám.');
      if (updateUserSession(updated) !== false) onComplete?.();
    } catch (err) {
      if (!sameSession(id, key, ticket)) return;
      setErrorMsg(err.message || 'Chưa hoàn tất khởi tạo. Thử lại sẽ tiếp tục cùng hồ sơ.');
      if (err.status === 400) setPending(null);
    } finally {
      if (sameSession(id, key, ticket)) { running.current = false; setBusy(false); }
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
        {restoring && <p role="status">Đang kiểm tra tiến trình hồ sơ…</p>}
        {pending && !successMsg && <p className="notice">Tiến trình đã được giữ theo tài khoản. Thử lại sẽ tiếp tục cùng hồ sơ.</p>}
        {!busy && !restoring && (errorMsg || pending) && <button type="button" className="button secondary" onClick={() => setRevision(v => v + 1)}>Tải lại tiến trình</button>}
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
                disabled={busy || restoring || !!pending || !ready}
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
                disabled={busy || restoring || !!pending || !ready}
                style={{ width: '100%', padding: '0.65rem 0.75rem', borderRadius: '0.5rem', border: '1px solid #cbd5e1', fontSize: '0.95rem' }}
              />
              <small style={{ color: '#64748b', fontSize: '0.8rem', marginTop: '4px', display: 'block' }}>
                10 chữ số (VD: 0912345678 hoặc +84...)
              </small>
            </div>

            <div>
              <label style={{ display: 'block', fontWeight: 600, marginBottom: '0.35rem', color: '#1e293b' }}>
                Ngày sinh <span style={{ color: '#dc2626' }}>*</span>
              </label>
              <input
                type="date"
                required
                max={today()}
                value={dateOfBirth}
                onChange={(e) => setDateOfBirth(e.target.value)}
                disabled={busy || restoring || !!pending || !ready}
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
                disabled={busy || restoring || !!pending || !ready}
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
              disabled={busy || restoring || !!pending || !ready}
              style={{ width: '100%', padding: '0.65rem 0.75rem', borderRadius: '0.5rem', border: '1px solid #cbd5e1', fontSize: '0.95rem', resize: 'vertical' }}
            />
          </div>

          <div style={{ marginTop: '0.5rem', display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
            <button
              type="submit"
              className="button primary" disabled={busy || restoring || !ready}
              style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem', padding: '0.75rem 1.5rem', fontWeight: 600 }}
            >
              {busy ? (
                <>
                  <Loader2 size={17} className="spin" />
                  Đang khởi tạo hồ sơ...
                </>
              ) : (
                <>
                  {pending ? 'Tiếp tục khởi tạo hồ sơ' : 'Lưu & Tiếp tục đặt lịch'}
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
