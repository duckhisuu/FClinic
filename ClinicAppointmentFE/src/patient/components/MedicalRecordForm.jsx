import React, { useId, useMemo } from 'react';
import { Save, ClipboardPlus, RotateCcw } from 'lucide-react';
import { localMinute, medicalPayload, shortId } from '../model';
import { useEditor } from '../hooks/useEditor';
import { Blocker, ErrorNotice, Field } from './Shared';
export function MedicalRecordForm({ record, patient, preview, previewDoctorId = '', doctorId = '', allowed: authorized = false, onSave, onCancel, onReload, onDirty, onBusy }) {
  const prefix = useId(); const allowed = (preview || (authorized && !!doctorId)) && (record || patient.status === 'ACTIVE');
  const initial = useMemo(() => ({ doctorId: record?.doctorId || (preview ? previewDoctorId : doctorId), appointmentId: record?.appointmentId || '', visitDate: localMinute(record?.visitDate || new Date(Date.now() - 60000).toISOString()), symptoms: record?.symptoms || '', diagnosis: record?.diagnosis || '', treatment: record?.treatment || '', notes: record?.notes || '', changeReason: '' }), [record, preview, previewDoctorId, doctorId]);
  const form = useEditor(initial, { onDirty, onBusy, onSave }); const { values: v, change } = form;
  const submit = e => { e.preventDefault(); if (allowed) form.submit(medicalPayload(v, record)); };
  return <form className="pt-card pt-form" onSubmit={submit} noValidate>
    <div className="pt-card-heading"><span className="pt-icon-tile"><ClipboardPlus size={21} /></span><div><h2>{record ? 'Cập nhật bệnh án' : 'Ghi nhận lần khám'}</h2><p>{patient.fullName} · {shortId(patient.id)}{record && ` · Version ${record.version}`}</p></div></div>
    {!preview && !allowed && <Blocker>Backend xác minh bác sĩ, quyền clinical và VALID_VISIT cho từng lần lưu.</Blocker>}
    {preview && !record && patient.status !== 'ACTIVE' && <Blocker>Hồ sơ đang ngừng hoạt động, không thể tạo bệnh án mới.</Blocker>}
    <ErrorNotice error={form.error} onReload={onReload} />
    {form.issues.version && <p role="alert" className="pt-field-error">{form.issues.version}</p>}
    {Object.values(form.issues).some(Boolean) && <p role="alert" className="pt-field-error">Vui lòng kiểm tra các trường được đánh dấu.</p>}
    <fieldset disabled={!allowed || form.disabled}><div className="pt-form-grid">
      <Field id={`${prefix}-doctorId`} label="Doctor UUID (Patient v3)" required issue={form.issues.doctorId} hint={preview ? 'UUID synthetic dùng riêng cho Preview.' : 'Chỉ nhận danh tính bác sĩ đáng tin cậy; không dùng ID Long.'}><input value={v.doctorId} readOnly={!preview || !!record} onChange={e => change('doctorId', e.target.value)} /></Field>
      <Field id={`${prefix}-appointmentId`} label="Appointment UUID" issue={form.issues.appointmentId} hint="Có thể để trống. Không đồng nghĩa đã được cấp VALID_VISIT."><input value={v.appointmentId} readOnly={!!record} onChange={e => change('appointmentId', e.target.value)} /></Field>
      <Field id={`${prefix}-visitDate`} label="Thời điểm khám" required issue={form.issues.visitDate} hint="Giờ Việt Nam (UTC+07:00), không ở tương lai."><input type="datetime-local" value={v.visitDate} onChange={e => change('visitDate', e.target.value)} /></Field>
      {record && <Field id={`${prefix}-changeReason`} label="Lý do chỉnh sửa" required issue={form.issues.changeReason} hint="Tối đa 1.000 ký tự; bắt buộc cho mỗi lần cập nhật."><input value={v.changeReason} onChange={e => change('changeReason', e.target.value)} /></Field>}
      {[['symptoms', 'Triệu chứng', 2000, true], ['diagnosis', 'Chẩn đoán', 2000, true], ['treatment', 'Điều trị', 4000, false], ['notes', 'Ghi chú', 4000, false]].map(([name, label, max, required]) => <Field key={name} id={`${prefix}-${name}`} label={label} required={required} issue={form.issues[name]} hint={`${[...v[name]].length.toLocaleString('vi-VN')} / ${max.toLocaleString('vi-VN')} ký tự`}><textarea rows={4} value={v[name]} onChange={e => change(name, e.target.value)} /></Field>)}
    </div></fieldset>
    <div className="pt-form-footer"><span>{preview ? 'Xác nhận mô phỏng, không gửi tới backend.' : 'Chỉ xác nhận lưu khi backend trả kết quả thành công.'}</span><div><button type="button" className="pt-button pt-secondary" disabled={form.busy} onClick={onCancel}>Hủy</button><button type="button" className="pt-icon-button" aria-label="Hoàn tác bệnh án" disabled={form.busy} onClick={form.reset}><RotateCcw size={17} /></button><button className="pt-button pt-primary" disabled={!allowed || form.disabled}><Save size={17} />{form.busy ? 'Đang lưu…' : record ? 'Lưu bệnh án' : 'Tạo bệnh án'}</button></div></div>
  </form>;
}
