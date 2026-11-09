import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { AppointmentHistory } from '../appointment/AppointmentHistory';
import { DoctorSchedulePage } from './DoctorSchedulePage';

export function DoctorDashboardPage() {
  const { user } = useAuth();
  const [tab, setTab] = useState('appointments');
  if (!user?.doctorId) return <section className="panel"><h1>Lịch khám bác sĩ</h1><p role="alert">Tài khoản chưa liên kết hồ sơ bác sĩ. Vui lòng liên hệ quản trị để liên kết.</p></section>;
  return <><div className="segmented"><button className={tab === 'appointments' ? 'active' : ''} onClick={() => setTab('appointments')}>Lịch hẹn phụ trách</button>
    <button className={tab === 'schedule' ? 'active' : ''} onClick={() => setTab('schedule')}>Lịch làm việc</button></div>
    {tab === 'appointments' ? <AppointmentHistory key={user.id} actorSubject={user.id} doctorId={user.doctorId} role="ROLE_DOCTOR" /> : <DoctorSchedulePage />}</>;
}
