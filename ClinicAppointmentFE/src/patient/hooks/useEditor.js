import { useEffect, useRef, useState } from 'react';
export function useEditor(initial, { onDirty, onBusy, onSave }) {
  const [values, setValues] = useState(initial); const [issues, setIssues] = useState({});
  const [error, setError] = useState(null); const [busy, setBusy] = useState(false); const locked = useRef(false);
  const dirty = JSON.stringify(values) !== JSON.stringify(initial);
  useEffect(() => { onDirty?.(dirty); return () => onDirty?.(false); }, [dirty, onDirty]);
  useEffect(() => {
    if (!dirty) return;
    const leave = e => { e.preventDefault(); e.returnValue = ''; };
    window.addEventListener('beforeunload', leave); return () => window.removeEventListener('beforeunload', leave);
  }, [dirty]);
  const change = (field, value) => { setValues(v => ({ ...v, [field]: value })); setIssues(v => ({ ...v, [field]: undefined })); };
  async function submit(result) {
    if (locked.current || error?.uncertain) return;
    setIssues(result.errors); if (Object.keys(result.errors).length) return;
    locked.current = true; setBusy(true); onBusy?.(true); setError(null);
    try { await onSave(result.body); onDirty?.(false); }
    catch (err) { setError(err); setIssues(Object.fromEntries((err.fields || []).map(f => [f.field, 'Giá trị chưa hợp lệ theo máy chủ.']))); }
    finally { locked.current = false; setBusy(false); onBusy?.(false); }
  }
  const reset = () => { if (busy || error?.uncertain) return; if (!dirty || window.confirm('Bỏ các thay đổi chưa lưu và khôi phục biểu mẫu?')) { setValues(initial); setIssues({}); setError(null); } };
  return { values, issues, error, busy, dirty, change, submit, reset, disabled: busy || !!error?.uncertain };
}
