import React from 'react';
import { ClipboardList, Mail, MapPin, Pencil, Phone, UserRound } from 'lucide-react';
import { usePatient } from '../hooks/usePatients';
import { dateText, genders, initials } from '../model';
import { MetaGrid, PageHeading, PageState, StatusBadge } from '../components/Shared';
import { PatientForm } from '../components/PatientForm';
export function PatientDetailPage({ api, patientId, editing, revision, navigate, finish, canEdit, preview, onDirty, onBusy }) {
  const resource = usePatient(api, patientId, revision); const p = resource.data;
  return <>
    <PageHeading title={editing ? 'Chỉnh sửa hồ sơ' : 'Hồ sơ bệnh nhân'} description="Thông tin hành chính và lịch sử cập nhật hồ sơ." onBack={() => navigate({ name: 'list' })} action={!editing && p && <button className="pt-button pt-secondary" disabled={!canEdit(p.id)} onClick={() => navigate({ name: 'editPatient', patientId })}><Pencil size={16} />Chỉnh sửa hồ sơ</button>} />
    <PageState resource={resource}>{p && (editing ? <PatientForm patient={p} preview={preview} allowed={canEdit(p.id)} onDirty={onDirty} onBusy={onBusy} onCancel={() => navigate({ name: 'profile', patientId })} onReload={() => navigate({ name: 'profile', patientId }, true)} onSave={async body => { const saved = await api.updatePatient(p.id, body); finish({ name: 'profile', patientId: saved.id }, 'Đã cập nhật hồ sơ.'); }} /> : <>
      <div className="pt-profile-grid"><section className="pt-card pt-person"><span className="pt-avatar pt-avatar-large">{initials(p.fullName)}</span><h2>{p.fullName}</h2><StatusBadge status={p.status} /><p className="pt-code pt-full-code">{p.patientCode}</p><div className="pt-contact"><span><Phone size={16} />{p.phoneNumber}</span><span><Mail size={16} />{p.email || 'Chưa cập nhật email'}</span><span><MapPin size={16} />{p.address || 'Chưa cập nhật địa chỉ'}</span></div><button className="pt-button pt-primary" onClick={() => navigate({ name: 'records', patientId })}><ClipboardList size={17} />Xem bệnh án</button></section>
      <section className="pt-card pt-pad"><div className="pt-section-title"><UserRound size={19} /><h2>Thông tin hành chính</h2></div><MetaGrid entries={[
        ['Họ và tên', p.fullName], ['Ngày sinh', dateText(p.dateOfBirth)], ['Giới tính', genders[p.gender]], ['Số điện thoại', p.phoneNumber], ['Email', p.email], ['Địa chỉ', p.address], ['Trạng thái', <StatusBadge status={p.status} />], ['Version', String(p.version)], ['Patient UUID', <span className="pt-code">{p.id}</span>],
      ]} /><div className="pt-section-title pt-divider"><h2>Lịch sử cập nhật</h2></div><MetaGrid entries={[
        ['Tạo lúc', dateText(p.createdAt, true)], ['Người tạo', p.createdBy], ['Cập nhật lúc', dateText(p.updatedAt, true)], ['Người cập nhật', p.updatedBy],
      ]} /></section></div>
      <div className="pt-card pt-record-teaser"><span className="pt-icon-tile"><ClipboardList size={23} /></span><div><h2>Bệnh án của bệnh nhân</h2><p>Xem các lần khám, nội dung bệnh án và version của từng bản ghi.</p></div><button className="pt-button pt-secondary" onClick={() => navigate({ name: 'records', patientId })}>Mở danh sách bệnh án</button></div>
    </>)}</PageState>
  </>;
}
