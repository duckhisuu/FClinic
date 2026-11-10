import React, { useMemo, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { authApi } from '../services/authApi';
import { createPatientApi } from '../patient/api/patientApi';
import { PatientForm } from '../patient/components/PatientForm';
import '../patient/patient.css';

export function PatientOnboardingPage({ onComplete }) {
  const { user, updateUserSession } = useAuth();
  const [revision, setRevision] = useState(0);
  const [pendingPatient, setPendingPatient] = useState(null);
  const api = useMemo(() => createPatientApi({ getAccessToken: () => localStorage.getItem('fclinic_token') }), []);
  const initial = useMemo(() => ({ fullName: user?.fullName || '', phoneNumber: user?.phoneNumber || '', email: user?.email || '' }), [user]);
  return <section className="pt-module" aria-label="Khởi tạo hồ sơ cá nhân">
    <div className="page-heading"><div><span className="eyebrow">CHÀO MỪNG BẠN ĐẾN VỚI FCLINIC</span>
      <h1>Hoàn tất hồ sơ để đặt lịch khám</h1>
      <p>Bạn chỉ cần khởi tạo một hồ sơ cho tài khoản của mình. Bạn có thể cập nhật thông tin cá nhân tại /profile.</p>
    </div></div>
    {pendingPatient && <p role="status" className="notice">Hồ sơ {pendingPatient.patientCode} đã được lưu. Nếu liên kết thất bại, bấm Tạo hồ sơ để thử lại liên kết hồ sơ này.</p>}
    <PatientForm key={revision} initialValues={initial} selfService fieldsDisabled={!!pendingPatient} allowed={user?.role === 'ROLE_PATIENT'}
      onReload={() => setRevision(value => value + 1)}
      onSave={async body => {
        // Creation is idempotent per verified subject, so retries cannot create another profile.
        const patient = pendingPatient || await api.createPatient(body);
        setPendingPatient(patient);
        const updated = await authApi.linkPatientProfile(patient.id);
        if (!updateUserSession(updated)) throw new Error('Phiên đăng nhập đã thay đổi. Vui lòng đăng nhập lại.');
        onComplete?.(patient);
      }} />
  </section>;
}
