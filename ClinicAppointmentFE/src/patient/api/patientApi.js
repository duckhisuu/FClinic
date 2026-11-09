import { refreshSession } from '../../services/session';
import { UUID } from '../model';
const ROOT = '/api/v3/patients';
const messages = { 400: 'Thông tin chưa hợp lệ. Vui lòng kiểm tra các trường được đánh dấu.', 401: 'Phiên đăng nhập chưa có hoặc đã hết hạn.', 403: 'Bạn chưa được cấp quyền cho thao tác này.', 404: 'Không tìm thấy hồ sơ yêu cầu.', 409: 'Dữ liệu đã thay đổi hoặc không thể cập nhật ở trạng thái hiện tại.', 422: 'Thông tin tham chiếu hoặc lần khám không hợp lệ.', 503: 'Dịch vụ phụ thuộc chưa sẵn sàng. Vui lòng thử lại sau.' };
export class PatientApiError extends Error {
  constructor(status, code, fields = [], uncertain = false) {
    super(messages[status] || (code === 'NO_SESSION' ? 'Chưa kết nối phiên đăng nhập được xác minh.' : 'Chưa nhận được kết quả hợp lệ từ máy chủ.'));
    Object.assign(this, { status, code, fields, uncertain });
  }
}
function failShape() { throw new PatientApiError(503, 'INVALID_ENVELOPE'); }
export function parseEnvelope(status, body) {
  if (!body || typeof body !== 'object' || body.status !== status || typeof body.message !== 'string' || !Object.hasOwn(body, 'data')) failShape();
  if (status < 200 || status >= 300) throw new PatientApiError(status, typeof body.code === 'string' ? body.code : `HTTP_${status}`, Array.isArray(body.fieldErrors) ? body.fieldErrors.filter(f => typeof f?.field === 'string' && typeof f?.message === 'string') : []);
  return body.data;
}
function resource(value, medical = false) {
  if (!value || !UUID.test(value.id) || !Number.isSafeInteger(value.version) || value.version < 0 || typeof value.createdAt !== 'string' || typeof value.updatedAt !== 'string') failShape();
  if (medical ? !UUID.test(value.patientId) || !UUID.test(value.doctorId) || (value.appointmentId !== null && !UUID.test(value.appointmentId)) || typeof value.diagnosis !== 'string' : typeof value.fullName !== 'string' || typeof value.phoneNumber !== 'string' || !['ACTIVE', 'INACTIVE'].includes(value.status) || !['MALE', 'FEMALE', 'OTHER', 'UNKNOWN'].includes(value.gender)) failShape();
  return value;
}
function matching(value, expected, medical = false, patientId) {
  resource(value, medical);
  if ((expected && value.id !== expected) || (medical && value.patientId !== patientId)) failShape();
  return value;
}
function fields(body, allowed) {
  if (!body || Object.keys(body).some(key => !allowed.includes(key)) || (Object.hasOwn(body, 'version') && (!Number.isSafeInteger(body.version) || body.version < 0))) throw new PatientApiError(400, 'INVALID_REQUEST_FIELDS');
  return Object.fromEntries(allowed.filter(key => Object.hasOwn(body, key)).map(key => [key, body[key]]));
}
const PROFILE = ['fullName', 'phoneNumber', 'email', 'dateOfBirth', 'gender', 'address'];
const CLINICAL = ['visitDate', 'symptoms', 'diagnosis', 'treatment', 'notes'];
function page(value, medical) {
  if (!value || !Array.isArray(value.content) || !['page', 'size', 'totalElements', 'totalPages'].every(k => Number.isSafeInteger(value[k]) && value[k] >= 0) || value.size < 1 || value.size > 100 || value.content.length > value.size || value.totalElements < value.content.length) failShape();
  value.content.forEach(v => resource(v, medical)); return value;
}
const id = value => { if (!UUID.test(value)) throw new PatientApiError(400, 'INVALID_UUID'); return value; };
// The host owns authentication. No token input/storage, role simulation, cookies-as-Bearer or mock fallback.
export function createPatientApi({ getAccessToken, fetchImpl = (...args) => fetch(...args), baseUrl = import.meta.env.VITE_API_GATEWAY_URL || '' } = {}) {
  async function request(method, path, body, signal, retried = false) {
    let token;
    try { token = await getAccessToken?.(); } catch { throw new PatientApiError(503, 'SESSION_UNAVAILABLE'); }
    signal?.throwIfAborted();
    if (typeof token !== 'string' || !token || /\s/.test(token)) throw new PatientApiError(0, 'NO_SESSION');
    const controller = new AbortController();
    const abort = () => controller.abort(); signal?.addEventListener('abort', abort, { once: true });
    const timer = setTimeout(abort, 10000);
    let acknowledged = false;
    try {
      const response = await fetchImpl(`${baseUrl}${path}`, { method, signal: controller.signal, credentials: 'same-origin', headers: { Accept: 'application/json', Authorization: `Bearer ${token}`, ...(body ? { 'Content-Type': 'application/json' } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}) });
      if (response.status === 401 && !retried && await refreshSession('Bearer ' + token)) {
        return request(method, path, body, signal, true);
      }
      acknowledged = response.status >= 200 && response.status < 300;
      if (!(response.headers.get('content-type') || '').toLowerCase().includes('application/json')) throw new PatientApiError(response.status >= 400 ? response.status : 503, 'INVALID_CONTENT_TYPE');
      let payload; try { payload = await response.json(); } catch { throw new PatientApiError(response.status >= 400 ? response.status : 503, 'INVALID_JSON'); }
      return parseEnvelope(response.status, payload);
    } catch (error) {
      if (signal?.aborted) throw new DOMException('Aborted', 'AbortError');
      if (error instanceof PatientApiError) { if (method !== 'GET' && acknowledged) error.uncertain = true; throw error; }
      throw new PatientApiError(0, 'NETWORK_ERROR', [], method !== 'GET');
    } finally { clearTimeout(timer); signal?.removeEventListener('abort', abort); }
  }
  const query = params => new URLSearchParams(Object.entries(params).filter(([, v]) => v !== '' && v !== null && v !== undefined)).toString();
  const write = (method, path, body, check) => request(method, path, body).then(check).catch(error => { if (error.code === 'INVALID_ENVELOPE') error.uncertain = true; throw error; });
  return {
    mode: 'live',
    listPatients: (params, signal) => request('GET', `${ROOT}?${query(params)}`, null, signal).then(v => page(v, false)),
    getPatient: (patient, signal) => request('GET', `${ROOT}/${id(patient)}`, null, signal).then(v => matching(v, patient)),
    createPatient: body => write('POST', ROOT, fields(body, PROFILE), v => resource(v)),
    updatePatient: (patient, body) => write('PUT', `${ROOT}/${id(patient)}`, fields(body, [...PROFILE, 'status', 'version']), v => matching(v, patient)),
    listRecords: (patient, params, signal) => request('GET', `${ROOT}/${id(patient)}/medical-records?${query(params)}`, null, signal).then(v => { page(v, true); v.content.forEach(r => matching(r, null, true, patient)); return v; }),
    getRecord: (patient, record, signal) => request('GET', `${ROOT}/${id(patient)}/medical-records/${id(record)}`, null, signal).then(v => matching(v, record, true, patient)),
    createRecord: (patient, body) => write('POST', `${ROOT}/${id(patient)}/medical-records`, fields(body, [...CLINICAL, 'doctorId', 'appointmentId']), v => matching(v, null, true, patient)),
    updateRecord: (patient, record, body) => write('PUT', `${ROOT}/${id(patient)}/medical-records/${id(record)}`, fields(body, [...CLINICAL, 'version', 'changeReason']), v => matching(v, record, true, patient)),
  };
}
