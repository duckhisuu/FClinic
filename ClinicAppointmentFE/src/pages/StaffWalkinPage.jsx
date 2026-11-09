import React, { useState } from 'react';
import { PatientSelection } from '../appointment/PatientSelection';

export function StaffWalkinPage({ onSelectPatientForBooking, onManageProfiles }) {
  const [patient, setPatient] = useState(null);
  return <>
    <div className="page-heading"><div><h1>Tiếp nhận tại quầy</h1><p>Chọn hồ sơ Patient để đặt lịch trong phạm vi được phân công.</p></div></div>
    <PatientSelection canSearch onSelect={setPatient} />
    {patient && <section className="panel"><p>Đã chọn {patient.fullName} · {patient.patientCode}</p><button className="button primary" disabled={patient.status !== 'ACTIVE'} onClick={() => onSelectPatientForBooking(patient)}>Chọn bác sĩ và đặt lịch</button></section>}
    <section className="panel"><h2>Hồ sơ bệnh nhân</h2><p>Hồ sơ mới và liên kết tài khoản được quản lý ở phân hệ Patient theo quyền được cấp.</p><button className="button secondary" onClick={onManageProfiles}>Mở quản lý bệnh nhân</button></section>
  </>;
}