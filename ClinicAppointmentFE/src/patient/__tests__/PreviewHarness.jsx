import React, { useEffect, useMemo, useState } from 'react';
import { FlaskConical, PlugZap } from 'lucide-react';
import { createPatientApi } from '../api/patientApi';
import { PatientWorkspace } from '../PatientModule';
const previewRequested = () => new URLSearchParams(window.location.search).get('patientPreview') === '1';
export function PatientModule({ session = null, onBeforeLeave }) {
  const [mode, setMode] = useState(previewRequested() ? 'preview' : 'live'); const [previewApi, setPreviewApi] = useState(null);
  const [scenario, setScenario] = useState('normal'); const [revision, setRevision] = useState(0); const [busy, setBusy] = useState(false); const [guard, registerGuard] = useState(null);
  const liveApi = useMemo(() => createPatientApi({ getAccessToken: () => session?.getAccessToken?.() }), [session]);
  useEffect(() => {
    if (mode !== 'preview' || !import.meta.env.DEV || previewApi) return;
    let current = true;
    import('../preview/previewAdapter').then(({ createPreviewAdapter }) => { if (current) setPreviewApi(createPreviewAdapter()); });
    return () => { current = false; };
  }, [mode, previewApi]);
  useEffect(() => { onBeforeLeave?.(guard); return () => onBeforeLeave?.(null); }, [guard, onBeforeLeave]);
  const changeMode = next => { if (busy || (guard && !guard())) return; setMode(next); setScenario('normal'); previewApi?.setScenario('normal'); setRevision(n => n + 1); };
  return <section className="pt-module" aria-label="Module quản lý bệnh nhân">
    <div className={`pt-mode-bar ${mode === 'preview' ? 'pt-preview-bar' : ''}`}><div>{mode === 'preview' ? <FlaskConical size={19} /> : <PlugZap size={19} />}<span>{mode === 'preview' ? 'UI Preview — Dữ liệu minh họa, không lưu vào hệ thống thật.' : 'Live API — Dữ liệu từ hệ thống thật'}{mode === 'live' && !session && <small>Chưa kết nối phiên đăng nhập được xác minh.</small>}</span></div><div className="pt-mode-actions">{mode === 'preview' && <select aria-label="Tình huống Preview" disabled={busy} value={scenario} onChange={e => { if (guard && !guard()) return; setScenario(e.target.value); previewApi?.setScenario(e.target.value); setRevision(n => n + 1); }}><option value="normal">Bình thường</option><option value="empty">Danh sách trống</option><option value="error">Lỗi dịch vụ</option><option value="slow">Tải chậm</option><option value="conflict">Xung đột khi lưu</option></select>}{import.meta.env.DEV && <div className="pt-mode-switch" aria-label="Chế độ dữ liệu"><button aria-pressed={mode === 'preview'} disabled={busy} onClick={() => changeMode('preview')}>UI Preview</button><button aria-pressed={mode === 'live'} disabled={busy} onClick={() => changeMode('live')}>Live API</button></div>}</div></div>
    {mode === 'preview' && !previewApi ? <div className="pt-empty" role="status">Đang chuẩn bị dữ liệu minh họa…</div> : <PatientWorkspace key={mode} api={mode === 'preview' ? previewApi : liveApi} session={session} scenario={scenario} revision={revision} registerGuard={registerGuard} setParentBusy={setBusy} />}
  </section>;
}
