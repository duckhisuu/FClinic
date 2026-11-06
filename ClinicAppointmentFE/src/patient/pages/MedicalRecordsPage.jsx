import React, { useState } from 'react';
import { ArrowUpRight, Plus, ClipboardList } from 'lucide-react';
import { usePatient } from '../hooks/usePatients';
import { useMedicalRecords } from '../hooks/useMedicalRecords';
import { dateText, initials, shortId } from '../model';
import { Blocker, PageHeading, PageState, Pagination, StatusBadge } from '../components/Shared';
export function PatientContext({ patient }) { return <div className="pt-context"><span className="pt-avatar">{initials(patient.fullName)}</span><div><strong>{patient.fullName}</strong><p>{patient.phoneNumber} · {dateText(patient.dateOfBirth)}</p></div><StatusBadge status={patient.status} /></div>; }
export function MedicalRecordsPage({ api, patientId, revision, navigate, preview }) {
  const patient = usePatient(api, patientId, revision);
  const [params, setParams] = useState({ page: 0, size: 10, sort: 'visitDate,desc' });
  const resource = useMedicalRecords(api, patientId, params, revision); const data = resource.data;
  return <>
    <PageHeading title="Bệnh án bệnh nhân" description="Danh sách lần khám và nội dung được lưu theo từng bản ghi." onBack={() => navigate({ name: 'profile', patientId })} action={<button className="pt-button pt-primary" disabled={!preview || patient.data?.status !== 'ACTIVE'} onClick={() => navigate({ name: 'createRecord', patientId })}><Plus size={17} />Tạo bệnh án</button>} />
    <PageState resource={patient}>{patient.data && <PatientContext patient={patient.data} />}</PageState>
    {!preview && <Blocker>Đọc bệnh án cần quyền clinical READ hoặc Patient tự đọc hồ sơ của mình. Tạo/sửa đang chờ trusted Doctor identity và WorkScope, không gọi Doctor/Appointment trong phase UI này.</Blocker>}
    {!patient.loading && !patient.error && <section className="pt-card"><div className="pt-record-list-head"><div className="pt-section-title"><ClipboardList size={19} /><h2>Các lần khám</h2><span className="pt-count">{data?.totalElements ?? '—'}</span></div><select aria-label="Sắp xếp bệnh án" value={params.sort} onChange={e => setParams(p => ({ ...p, page: 0, sort: e.target.value }))}><option value="visitDate,desc">Lần khám mới nhất</option><option value="visitDate,asc">Lần khám sớm nhất</option></select></div>
      <PageState resource={resource} empty={data?.content.length === 0} emptyTitle="Chưa có bệnh án" emptyText="Các lần khám được cấp quyền sẽ hiển thị ở đây.">{data && <div className="pt-medical-list">{data.content.map(r => <article key={r.id} className="pt-medical-row"><span className="pt-visit-date"><strong>{dateText(r.visitDate).slice(0, 5)}</strong><small>{new Date(r.visitDate).getFullYear()}</small></span><div><button className="pt-record-title" onClick={() => navigate({ name: 'record', patientId, recordId: r.id })}>{r.diagnosis}</button><p className="pt-clamp">{r.treatment || 'Chưa ghi nhận điều trị'}</p><div className="pt-row-meta"><span title={r.doctorId}>Doctor UUID: {shortId(r.doctorId)}</span><span>Version {r.version}</span><span>Cập nhật {dateText(r.updatedAt, true)}</span></div></div><button className="pt-icon-button" aria-label={`Xem bệnh án ${r.id}`} onClick={() => navigate({ name: 'record', patientId, recordId: r.id })}><ArrowUpRight size={18} /></button></article>)}</div>}</PageState>
      <Pagination data={data} onPage={page => setParams(p => ({ ...p, page }))} onSize={size => setParams(p => ({ ...p, size, page: 0 }))} />
    </section>}
  </>;
}
