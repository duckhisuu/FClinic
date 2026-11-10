import React, { useEffect, useMemo, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { createPatientApi } from '../patient/api/patientApi';
import { PatientForm } from '../patient/components/PatientForm';
import { PatientOnboardingPage } from './PatientOnboardingPage';
import '../patient/patient.css';

export function ProfilePage({ onBeforeLeave }) {
  const { user } = useAuth();
  const [patient, setPatient] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const [revision, setRevision] = useState(0);
  const [editing, setEditing] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const api = useMemo(() => createPatientApi({ getAccessToken: () => localStorage.getItem('fclinic_token') }), []);
  useEffect(() => {
    onBeforeLeave?.(() => !busy && (!dirty || window.confirm('Bạn có thay đổi chưa lưu. Rời màn hình?')));
    return () => onBeforeLeave?.(null);
  }, [dirty, busy, onBeforeLeave]);
  useEffect(() => {
    if (user?.role !== 'ROLE_PATIENT' || !user?.patientId) return;
    const controller = new AbortController(); setLoading(true); setError(null); setPatient(null);
    api.getOwnPatient(controller.signal).then(value => { if (!controller.signal.aborted) setPatient(value); })
      .catch(err => { if (!controller.signal.aborted) setError(err); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [api, user?.id, user?.patientId, user?.role, revision]);
  return <section className="pt-module" aria-label="Thông tin cá nhân">
    <div className="page-heading"><div><h1>Thông tin cá nhân</h1><p>Thông tin tài khoản và hồ sơ bệnh nhân của bạn.</p></div></div>
    <section className="panel"><h2>Tài khoản</h2><dl className="pt-form-grid">
      <div><dt>Họ và tên</dt><dd>{user?.fullName || '—'}</dd></div>
      <div><dt>Tên đăng nhập</dt><dd>{user?.username || '—'}</dd></div>
      <div><dt>Email tài khoản</dt><dd>{user?.email || '—'}</dd></div>
      <div><dt>Số điện thoại</dt><dd>{user?.phoneNumber || '—'}</dd></div>
    </dl></section>
    {user?.role === 'ROLE_PATIENT' && !user.patientId ? <PatientOnboardingPage /> : user?.role === 'ROLE_PATIENT' && <>
      {notice && <p role="status" className="notice success">{notice}</p>}
      {loading && <p role="status">Đang tải hồ sơ…</p>}
      {error && <div role="alert"><p>{error.message}</p><button className="button secondary" onClick={() => setRevision(n => n + 1)}>Thử lại</button></div>}
      {patient && (editing ? <PatientForm key={`${patient.id}:${patient.version}:${revision}`} patient={patient} selfService allowed
        onDirty={setDirty} onBusy={setBusy} onCancel={() => { if (!dirty || window.confirm('Bỏ các thay đổi chưa lưu?')) { setEditing(false); setDirty(false); } }}
        onReload={() => { setDirty(false); setRevision(n => n + 1); }} onSave={async body => {
          const saved = await api.updatePatient(patient.id, body); setPatient(saved); setEditing(false); setDirty(false); setNotice('Đã cập nhật hồ sơ cá nhân.');
        }} /> : <section className="panel"><h2>Hồ sơ bệnh nhân</h2><dl className="pt-form-grid">
          <div><dt>Mã hồ sơ</dt><dd>{patient.patientCode}</dd></div><div><dt>Họ và tên</dt><dd>{patient.fullName}</dd></div>
          <div><dt>Số điện thoại liên hệ</dt><dd>{patient.phoneNumber}</dd></div><div><dt>Email liên hệ</dt><dd>{patient.email || '—'}</dd></div>
          <div><dt>Ngày sinh</dt><dd>{patient.dateOfBirth || '—'}</dd></div><div><dt>Giới tính</dt><dd>{{ MALE: 'Nam', FEMALE: 'Nữ', OTHER: 'Khác', UNKNOWN: 'Chưa cung cấp' }[patient.gender]}</dd></div>
          <div><dt>Địa chỉ</dt><dd>{patient.address || '—'}</dd></div>
        </dl><button className="button primary" onClick={() => { setNotice(''); setEditing(true); }}>Chỉnh sửa hồ sơ của tôi</button>
        </section>)}
    </>}
  </section>;
}
