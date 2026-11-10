import React, { useId, useMemo } from 'react';
import { Save, RotateCcw, UserRound } from 'lucide-react';
import { blankProfile, genders, profilePayload, statuses, today } from '../model';
import { useEditor } from '../hooks/useEditor';
import { Blocker, ErrorNotice, Field } from './Shared';
export function PatientForm({ patient, initialValues, selfService = false, fieldsDisabled = false, allowed, preview, onSave, onCancel, onReload, onDirty, onBusy }) {
  const prefix = useId();
  const initial = useMemo(() => Object.fromEntries(Object.entries(blankProfile).map(([k, v]) => [k, patient?.[k] ?? initialValues?.[k] ?? v])), [patient, initialValues]);
  const form = useEditor(initial, { onDirty, onBusy, onSave }); const { values: v, change } = form;
  function submit(e) { e.preventDefault(); if (!allowed) return; if (patient?.status === 'ACTIVE' && v.status === 'INACTIVE' && !window.confirm('Ngừng hoạt động hồ sơ này? Hồ sơ sẽ không thể nhận bệnh án mới.')) return; form.submit(profilePayload(v, patient)); }
  const field = (name, label, type = 'text', extra = {}) => <Field key={name} id={`${prefix}-${name}`} label={label} issue={form.issues[name]} {...extra}><input type={type} value={v[name]} onChange={e => change(name, e.target.value)} {...(type === 'date' ? { max: today() } : {})} autoComplete="off" /></Field>;
  return <form className="pt-card pt-form" onSubmit={submit} noValidate>
    <div className="pt-card-heading"><span className="pt-icon-tile"><UserRound size={21} /></span><div><h2>{patient ? 'Cập nhật hồ sơ bệnh nhân' : 'Thông tin bệnh nhân mới'}</h2><p>Các trường có dấu * là bắt buộc.{patient && ` Đang chỉnh sửa version ${patient.version}.`}</p></div></div>
    {!allowed && <Blocker>Bạn chỉ được tạo và cập nhật hồ sơ bệnh nhân của chính mình. Nhân viên chỉ được xem theo phạm vi được cấp.</Blocker>}
    <ErrorNotice error={form.error} onReload={onReload} />
    {form.issues.version && <p role="alert" className="pt-field-error">{form.issues.version}</p>}
    {Object.values(form.issues).some(Boolean) && <p role="alert" className="pt-field-error">Vui lòng kiểm tra các trường được đánh dấu.</p>}
    <fieldset disabled={!allowed || form.disabled || fieldsDisabled}><div className="pt-form-grid">
      {field('fullName', 'Họ và tên', 'text', { required: true, hint: 'Tối đa 255 ký tự.' })}
      {field('phoneNumber', 'Số điện thoại', 'tel', { required: true, hint: 'Ví dụ: 0900 000 001 hoặc +84900000001.' })}
      {field('dateOfBirth', 'Ngày sinh', 'date')}
      <Field id={`${prefix}-gender`} label="Giới tính" issue={form.issues.gender}><select value={v.gender} onChange={e => change('gender', e.target.value)}>{Object.entries(genders).map(([key, value]) => <option key={key} value={key}>{value}</option>)}</select></Field>
      {field('email', 'Email', 'email')}
      {patient && !selfService && <Field id={`${prefix}-status`} label="Trạng thái" required issue={form.issues.status}><select value={v.status} onChange={e => change('status', e.target.value)}>{Object.entries(statuses).map(([key, value]) => <option key={key} value={key}>{value}</option>)}</select></Field>}
      <Field id={`${prefix}-address`} label="Địa chỉ" wide issue={form.issues.address} hint="Tối đa 2.000 ký tự. Có thể để trống."><textarea rows={3} value={v.address} onChange={e => change('address', e.target.value)} /></Field>
    </div></fieldset>
    <div className="pt-form-footer"><span>{preview ? 'Thao tác chỉ lưu trong bộ nhớ Preview.' : 'Chỉ xác nhận sau khi máy chủ trả kết quả.'}</span><div>{onCancel && <button type="button" className="pt-button pt-secondary" disabled={form.busy} onClick={onCancel}>Hủy</button>}<button type="button" className="pt-icon-button" aria-label="Hoàn tác biểu mẫu" disabled={form.busy || fieldsDisabled} onClick={form.reset}><RotateCcw size={17} /></button><button className="pt-button pt-primary" disabled={!allowed || form.disabled}><Save size={17} />{form.busy ? 'Đang lưu…' : patient ? 'Lưu cập nhật' : 'Tạo hồ sơ'}</button></div></div>
  </form>;
}
