import { makeFixtures, PREVIEW_DOCTOR } from './fixtures';
import { PatientApiError } from '../api/patientApi';
export function createPreviewAdapter({ latency = 250, development = import.meta.env.DEV } = {}) {
  if (!development) throw new Error('Preview requires development mode');
  let { patients, records } = makeFixtures(); let scenario = 'normal';
  const copy = value => structuredClone(value);
  async function wait(signal, mutation = false) {
    await new Promise((resolve, reject) => {
      const abort = () => { clearTimeout(timer); reject(new DOMException('Aborted', 'AbortError')); };
      const timer = setTimeout(() => { signal?.removeEventListener('abort', abort); resolve(); }, scenario === 'slow' ? 1800 : latency);
      if (signal?.aborted) abort(); else signal?.addEventListener('abort', abort, { once: true });
    });
    if (scenario === 'error') throw new PatientApiError(503, 'PREVIEW_DEPENDENCY_UNAVAILABLE');
    if (mutation && scenario === 'conflict') throw new PatientApiError(409, 'VERSION_CONFLICT');
  }
  const find = (items, id) => { const value = items.find(x => x.id === id); if (!value) throw new PatientApiError(404, 'NOT_FOUND'); return value; };
  const paginate = (items, { page = 0, size = 10, sort }) => {
    const [field, direction] = (sort || 'fullName,asc').split(',');
    items.sort((a, b) => String(a[field]).localeCompare(String(b[field]), 'vi') * (direction === 'desc' ? -1 : 1) || a.id.localeCompare(b.id));
    return copy({ content: items.slice(page * size, (page + 1) * size), page: Number(page), size: Number(size), totalElements: items.length, totalPages: Math.ceil(items.length / size) });
  };
  return {
    mode: 'preview', syntheticDoctorId: PREVIEW_DOCTOR, setScenario: value => { scenario = value; },
    async listPatients(params, signal) { await wait(signal); let result = scenario === 'empty' ? [] : [...patients]; if (params.keyword) result = result.filter(p => p.fullName.toLocaleLowerCase('vi').includes(params.keyword.toLocaleLowerCase('vi'))); if (params.phoneNumber) result = result.filter(p => p.phoneNumber === params.phoneNumber); return paginate(result, params); },
    async getPatient(id, signal) { await wait(signal); return copy(find(patients, id)); },
    async createPatient(body) { await wait(null, true); const id = crypto.randomUUID(); const now = new Date().toISOString(); const value = { ...copy(body), id, patientCode: `PAT-${id}`, status: 'ACTIVE', version: 0, createdAt: now, updatedAt: now, createdBy: 'preview-staff', updatedBy: 'preview-staff' }; patients.push(value); return copy(value); },
    async updatePatient(id, body) { await wait(null, true); const value = find(patients, id); if (body.version !== value.version) throw new PatientApiError(409, 'VERSION_CONFLICT'); const changed = Object.entries(body).some(([key, val]) => key !== 'version' && value[key] !== val); Object.assign(value, copy(body), { version: value.version + (changed ? 1 : 0), updatedAt: new Date().toISOString(), updatedBy: 'preview-staff' }); return copy(value); },
    async listRecords(patient, params, signal) { await wait(signal); find(patients, patient); return paginate(scenario === 'empty' ? [] : records.filter(r => r.patientId === patient), params); },
    async getRecord(patient, id, signal) { await wait(signal); const value = find(records, id); if (value.patientId !== patient) throw new PatientApiError(404, 'MEDICAL_RECORD_NOT_FOUND'); return copy(value); },
    async createRecord(patient, body) { await wait(null, true); if (find(patients, patient).status !== 'ACTIVE') throw new PatientApiError(409, 'PATIENT_INACTIVE'); if (body.appointmentId && records.some(r => r.appointmentId === body.appointmentId)) throw new PatientApiError(409, 'APPOINTMENT_RECORD_EXISTS'); const now = new Date().toISOString(); const value = { ...copy(body), id: crypto.randomUUID(), patientId: patient, version: 0, createdAt: now, updatedAt: now, createdBy: 'preview-doctor', updatedBy: 'preview-doctor' }; records.unshift(value); return copy(value); },
    async updateRecord(patient, id, body) { await wait(null, true); const value = find(records, id); if (value.patientId !== patient) throw new PatientApiError(404, 'MEDICAL_RECORD_NOT_FOUND'); if (body.version !== value.version) throw new PatientApiError(409, 'VERSION_CONFLICT'); const { changeReason, ...fields } = body; const changed = Object.entries(fields).some(([key, val]) => key !== 'version' && value[key] !== val); Object.assign(value, copy(fields), { version: value.version + (changed ? 1 : 0), updatedAt: new Date().toISOString(), updatedBy: 'preview-doctor' }); return copy(value); },
  };
}
