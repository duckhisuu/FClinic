import React from 'react';
import { Pencil, ClipboardList } from 'lucide-react';
import { usePatient } from '../hooks/usePatients';
import { useMedicalRecord } from '../hooks/useMedicalRecords';
import { dateText } from '../model';
import { MetaGrid, PageHeading, PageState, Blocker } from '../components/Shared';
import { MedicalRecordForm } from '../components/MedicalRecordForm';
import { PatientContext } from './MedicalRecordsPage';
export function MedicalRecordDetailPage({ api, patientId, recordId, editing, revision, navigate, finish, preview, onDirty, onBusy }) {
  const patient = usePatient(api, patientId, revision); const resource = useMedicalRecord(api, patientId, recordId, revision); const record = resource.data;
  return <><PageHeading title={editing ? 'Chỉnh sửa bệnh án' : 'Chi tiết bệnh án'} description="Nội dung khám và thông tin truy vết của bản ghi." onBack={() => navigate({ name: 'records', patientId })} action={!editing && record && <button className="pt-button pt-secondary" disabled={!preview} onClick={() => navigate({ name: 'editRecord', patientId, recordId })}><Pencil size={16} />Chỉnh sửa bệnh án</button>} />
    <PageState resource={patient}>{patient.data && <PatientContext patient={patient.data} />}</PageState>
    {!preview && <Blocker>Clinical mutation chưa được cấp authority trong phase này. Nội dung chỉ được đọc khi backend cho phép.</Blocker>}
    {!patient.loading && !patient.error && <PageState resource={resource}>{record && (editing ? <MedicalRecordForm record={record} patient={patient.data} preview={preview} onDirty={onDirty} onBusy={onBusy} onCancel={() => navigate({ name: 'record', patientId, recordId })} onReload={() => navigate({ name: 'record', patientId, recordId }, true)} onSave={async body => { const saved = await api.updateRecord(patientId, recordId, body); finish({ name: 'record', patientId, recordId: saved.id }, 'Đã cập nhật bệnh án.'); }} /> : <section className="pt-card pt-pad"><div className="pt-section-title"><ClipboardList size={20} /><h2>Nội dung lần khám</h2><span className="pt-count">Version {record.version}</span></div><MetaGrid entries={[
      ['Thời điểm khám', dateText(record.visitDate, true)], ['Doctor UUID (Patient v3)', <span className="pt-code">{record.doctorId}</span>], ['Appointment UUID', record.appointmentId || 'Không gắn lịch hẹn'], ['Medical Record UUID', <span className="pt-code">{record.id}</span>],
    ]} /><div className="pt-clinical-grid">{[['Triệu chứng', record.symptoms], ['Chẩn đoán', record.diagnosis], ['Điều trị', record.treatment], ['Ghi chú', record.notes]].map(([label, text]) => <section key={label}><h3>{label}</h3><p>{text || 'Chưa ghi nhận'}</p></section>)}</div><div className="pt-section-title pt-divider"><h2>Thông tin cập nhật</h2></div><MetaGrid entries={[
      ['Tạo lúc', dateText(record.createdAt, true)], ['Người tạo', record.createdBy], ['Cập nhật lúc', dateText(record.updatedAt, true)], ['Người cập nhật', record.updatedBy],
    ]} /></section>)}</PageState>}
  </>;
}
