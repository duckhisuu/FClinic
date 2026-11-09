import React, { useEffect, useMemo, useRef, useState } from 'react';
import { CheckCircle2, UsersRound } from 'lucide-react';
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

function CreateRecord({ api, session, route, revision, navigate, finish, preview, setDirty, setBusy }) {
  const patient = usePatient(api, route.patientId, revision);
  return <><PageHeading title="Tạo bệnh án" description="Ghi nhận nội dung khám cho đúng hồ sơ bệnh nhân." onBack={() => navigate({ name: 'records', patientId: route.patientId })} /><PageState resource={patient}>{patient.data && <><PatientContext patient={patient.data} /><MedicalRecordForm patient={patient.data} preview={preview} previewDoctorId={api.syntheticDoctorId} doctorId={session?.doctorId} allowed={session?.permissions?.canCreateRecord?.(route.patientId) === true} onDirty={setDirty} onBusy={setBusy} onCancel={() => navigate({ name: 'records', patientId: route.patientId })} onReload={() => navigate({ name: 'records', patientId: route.patientId }, true)} onSave={async body => { const saved = await api.createRecord(route.patientId, body); finish({ name: 'record', patientId: route.patientId, recordId: saved.id }, 'Đã tạo bệnh án.'); }} /></>}</PageState></>;
}
export function PatientWorkspace({ api, session, scenario, revision: scenarioRevision, registerGuard, setParentBusy }) {
  const [route, setRoute] = useState(() => {
    const params = new URLSearchParams(window.location.hash.split('?')[1]);
    const name = params.get('view'); const patientId = params.get('patientId'); const recordId = params.get('recordId');
    if (['profile', 'records', 'record'].includes(name) && patientId) return { name, patientId, recordId };
    return session?.patientId && !session?.permissions?.searchPatients ? { name: 'profile', patientId: session.patientId } : { name: 'list' };
  }); const [revision, setRevision] = useState(0);
  const [dirty, setDirty] = useState(false); const [busy, setBusyState] = useState(false); const [notice, setNotice] = useState(''); const root = useRef();
  const preview = api.mode === 'preview';
  const setBusy = React.useCallback(value => { setBusyState(value); setParentBusy(value); }, [setParentBusy]);
  const guard = () => !busy && (!dirty || window.confirm('Bạn có thay đổi chưa lưu. Rời màn hình và bỏ các thay đổi này?'));
  useEffect(() => { registerGuard(() => guard); return () => registerGuard(null); }, [dirty, busy, registerGuard]);
  useEffect(() => { root.current?.scrollIntoView?.({ block: 'start' }); root.current?.querySelector('h1')?.focus({ preventScroll: true }); }, [route.name, route.patientId, route.recordId]);
  const navigate = (next, reload = false) => { if (!guard()) return; setDirty(false); setNotice(''); setRoute(next); if (!preview) {
    const params = new URLSearchParams(Object.entries({ view: next.name, patientId: next.patientId, recordId: next.recordId }).filter(([, value]) => value));
    window.history.pushState({}, '', '#patient-records?' + params);
  } if (reload) setRevision(n => n + 1); };
  useEffect(() => {
    const leave = event => { if (dirty || busy) { event.preventDefault(); event.returnValue = ''; } };
    const restore = () => {
      const params = new URLSearchParams(window.location.hash.split('?')[1]);
      if (window.location.hash.startsWith('#patient-records') && guard()) {
        setDirty(false); setRoute({ name: params.get('view') || 'list', patientId: params.get('patientId'), recordId: params.get('recordId') });
      }
    };
    window.addEventListener('beforeunload', leave); window.addEventListener('popstate', restore);
    return () => { window.removeEventListener('beforeunload', leave); window.removeEventListener('popstate', restore); };
  }, [dirty, busy]);
  const finish = (next, message) => { setDirty(false); setNotice(`${preview ? 'Mô phỏng: ' : ''}${message}`); setRevision(n => n + 1); setRoute(next); if (!preview) window.history.replaceState({}, '', '#patient-records?' + new URLSearchParams(Object.entries({ view: next.name, patientId: next.patientId, recordId: next.recordId }).filter(([, value]) => value))); };
  const canCreate = preview || session?.permissions?.createPatient === true;
  const canEdit = id => preview || session?.permissions?.canUpdatePatient?.(id) === true;
  const props = { api, session, revision: revision + (scenarioRevision || 0), navigate, finish, preview, onDirty: setDirty, onBusy: setBusy };
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
 const [guard, registerGuard] = useState(null);
 const api = useMemo(() => createPatientApi({ getAccessToken: () => session?.getAccessToken?.() }), [session]);
 useEffect(() => { onBeforeLeave?.(guard); return () => onBeforeLeave?.(null); }, [guard, onBeforeLeave]);
 return <section className="pt-module" aria-label="Module quản lý bệnh nhân"><PatientWorkspace api={api} session={session} revision={0} registerGuard={registerGuard} setParentBusy={ignoreBusy} /></section>;
}
const ignoreBusy = () => {};
