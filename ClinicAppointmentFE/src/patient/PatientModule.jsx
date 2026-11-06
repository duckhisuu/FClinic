import React, { useEffect, useMemo, useRef, useState } from 'react';
import { CheckCircle2, FlaskConical, PlugZap, UsersRound } from 'lucide-react';
import { createPatientApi } from './api/patientApi';
import { PatientListPage } from './pages/PatientListPage';
import { PatientDetailPage } from './pages/PatientDetailPage';
import { MedicalRecordsPage, PatientContext } from './pages/MedicalRecordsPage';
import { MedicalRecordDetailPage } from './pages/MedicalRecordDetailPage';
import { PatientForm } from './components/PatientForm';
import { MedicalRecordForm } from './components/MedicalRecordForm';
import { PageHeading, PageState } from './components/Shared';
import { usePatient } from './hooks/usePatients';
import './patient.css';
export const previewRequested = () => import.meta.env.DEV && new URLSearchParams(window.location.search).get('patientPreview') === '1';
function CreateRecord({ api, route, revision, navigate, finish, preview, setDirty, setBusy }) {
  const patient = usePatient(api, route.patientId, revision);
  return <><PageHeading title="Tạo bệnh án" description="Ghi nhận nội dung khám cho đúng hồ sơ bệnh nhân." onBack={() => navigate({ name: 'records', patientId: route.patientId })} /><PageState resource={patient}>{patient.data && <><PatientContext patient={patient.data} /><MedicalRecordForm patient={patient.data} preview={preview} previewDoctorId={api.syntheticDoctorId} onDirty={setDirty} onBusy={setBusy} onCancel={() => navigate({ name: 'records', patientId: route.patientId })} onReload={() => navigate({ name: 'records', patientId: route.patientId }, true)} onSave={async body => { const saved = await api.createRecord(route.patientId, body); finish({ name: 'record', patientId: route.patientId, recordId: saved.id }, 'Đã tạo bệnh án.'); }} /></>}</PageState></>;
}
function PatientWorkspace({ api, session, scenario, revision: scenarioRevision, registerGuard, setParentBusy }) {
  const [route, setRoute] = useState({ name: 'list' }); const [revision, setRevision] = useState(0);
  const [dirty, setDirty] = useState(false); const [busy, setBusyState] = useState(false); const [notice, setNotice] = useState(''); const root = useRef();
  const preview = api.mode === 'preview';
  const setBusy = React.useCallback(value => { setBusyState(value); setParentBusy(value); }, [setParentBusy]);
  const guard = () => !busy && (!dirty || window.confirm('Bạn có thay đổi chưa lưu. Rời màn hình và bỏ các thay đổi này?'));
  useEffect(() => { registerGuard(() => guard); return () => registerGuard(null); }, [dirty, busy, registerGuard]);
  useEffect(() => { root.current?.scrollIntoView?.({ block: 'start' }); root.current?.querySelector('h1')?.focus({ preventScroll: true }); }, [route.name, route.patientId, route.recordId]);
  const navigate = (next, reload = false) => { if (!guard()) return; setDirty(false); setNotice(''); setRoute(next); if (reload) setRevision(n => n + 1); };
  const finish = (next, message) => { setDirty(false); setNotice(`${preview ? 'Mô phỏng: ' : ''}${message}`); setRevision(n => n + 1); setRoute(next); };
  const canCreate = preview || session?.permissions?.createPatient === true;
  const canEdit = id => preview || session?.permissions?.canUpdatePatient?.(id) === true;
  const props = { api, revision: revision + scenarioRevision, navigate, finish, preview, onDirty: setDirty, onBusy: setBusy };
  let page;
  if (route.name === 'list') page = <PatientListPage {...props} canCreate={canCreate} />;
  else if (route.name === 'createPatient') page = <><PageHeading title="Tạo hồ sơ bệnh nhân" description="Thông tin hành chính được lưu tách biệt với bệnh án." onBack={() => navigate({ name: 'list' })} /><PatientForm allowed={canCreate} preview={preview} onDirty={setDirty} onBusy={setBusy} onCancel={() => navigate({ name: 'list' })} onReload={() => navigate({ name: 'list' }, true)} onSave={async body => { const saved = await api.createPatient(body); finish({ name: 'profile', patientId: saved.id }, 'Đã tạo hồ sơ bệnh nhân.'); }} /></>;
  else if (route.name === 'profile' || route.name === 'editPatient') page = <PatientDetailPage {...props} patientId={route.patientId} editing={route.name === 'editPatient'} canEdit={canEdit} />;
  else if (route.name === 'records') page = <MedicalRecordsPage {...props} patientId={route.patientId} />;
  else if (route.name === 'createRecord') page = <CreateRecord {...props} route={route} setDirty={setDirty} setBusy={setBusy} />;
  else page = <MedicalRecordDetailPage {...props} patientId={route.patientId} recordId={route.recordId} editing={route.name === 'editRecord'} />;
  return <div ref={root} className="pt-workspace">{notice && <div className="pt-notice pt-success" role="status"><CheckCircle2 size={20} /><span>{notice}</span><button aria-label="Đóng thông báo" onClick={() => setNotice('')}>×</button></div>}<nav className="pt-breadcrumb" aria-label="Điều hướng Patient"><button onClick={() => navigate({ name: 'list' })}><UsersRound size={15} />Bệnh nhân</button>{route.patientId && <><span>/</span><button onClick={() => navigate({ name: 'profile', patientId: route.patientId })}>Hồ sơ</button></>}{['records', 'record', 'editRecord', 'createRecord'].includes(route.name) && <><span>/</span><button onClick={() => navigate({ name: 'records', patientId: route.patientId })}>Bệnh án</button></>}</nav>{page}</div>;
}
// Session is supplied by the verified host integration, never synthesized by this module.
export function PatientModule({ session = null, onBeforeLeave }) {
  const [mode, setMode] = useState(previewRequested() ? 'preview' : 'live'); const [previewApi, setPreviewApi] = useState(null);
  const [scenario, setScenario] = useState('normal'); const [revision, setRevision] = useState(0); const [busy, setBusy] = useState(false); const [guard, registerGuard] = useState(null);
  const liveApi = useMemo(() => createPatientApi({ getAccessToken: () => session?.getAccessToken?.() }), [session]);
  useEffect(() => {
    if (mode !== 'preview' || !import.meta.env.DEV || previewApi) return;
    let current = true;
    import('./preview/previewAdapter').then(({ createPreviewAdapter }) => { if (current) setPreviewApi(createPreviewAdapter()); });
    return () => { current = false; };
  }, [mode, previewApi]);
  useEffect(() => { onBeforeLeave?.(guard); return () => onBeforeLeave?.(null); }, [guard, onBeforeLeave]);
  const changeMode = next => { if (busy || (guard && !guard())) return; setMode(next); setScenario('normal'); previewApi?.setScenario('normal'); setRevision(n => n + 1); };
  return <section className="pt-module" aria-label="Module quản lý bệnh nhân">
    <div className={`pt-mode-bar ${mode === 'preview' ? 'pt-preview-bar' : ''}`}><div>{mode === 'preview' ? <FlaskConical size={19} /> : <PlugZap size={19} />}<span>{mode === 'preview' ? 'UI Preview — Dữ liệu minh họa, không lưu vào hệ thống thật.' : 'Live API — Dữ liệu từ hệ thống thật'}{mode === 'live' && !session && <small>Chưa kết nối phiên đăng nhập được xác minh.</small>}</span></div><div className="pt-mode-actions">{mode === 'preview' && <select aria-label="Tình huống Preview" disabled={busy} value={scenario} onChange={e => { if (guard && !guard()) return; setScenario(e.target.value); previewApi?.setScenario(e.target.value); setRevision(n => n + 1); }}><option value="normal">Bình thường</option><option value="empty">Danh sách trống</option><option value="error">Lỗi dịch vụ</option><option value="slow">Tải chậm</option><option value="conflict">Xung đột khi lưu</option></select>}{import.meta.env.DEV && <div className="pt-mode-switch" aria-label="Chế độ dữ liệu"><button aria-pressed={mode === 'preview'} disabled={busy} onClick={() => changeMode('preview')}>UI Preview</button><button aria-pressed={mode === 'live'} disabled={busy} onClick={() => changeMode('live')}>Live API</button></div>}</div></div>
    {mode === 'preview' && !previewApi ? <div className="pt-empty" role="status">Đang chuẩn bị dữ liệu minh họa…</div> : <PatientWorkspace key={mode} api={mode === 'preview' ? previewApi : liveApi} session={session} scenario={scenario} revision={revision} registerGuard={registerGuard} setParentBusy={setBusy} />}
  </section>;
}
