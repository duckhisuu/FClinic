import { describe, expect, it, vi } from 'vitest';
import { createPatientApi, parseEnvelope } from '../api/patientApi';
import { makeFixtures } from '../preview/fixtures';
import { createPreviewAdapter } from '../preview/previewAdapter';
const reply = (status, data) => ({ status, headers: new Headers({ 'Content-Type': 'application/json' }), json: async () => ({ status, message: 'backend', data, code: `HTTP_${status}`, fieldErrors: [] }) });
describe('live transport isolation', () => {
  it('requires an injected session and never manufactures authorization', async () => {
    const fetchImpl = vi.fn(); const api = createPatientApi({ fetchImpl });
    await expect(api.listPatients({ page: 0, size: 10 })).rejects.toMatchObject({ code: 'NO_SESSION' }); expect(fetchImpl).not.toHaveBeenCalled();
  });
  it('does not expose a failing host session error or send a request', async () => {
    const fetchImpl = vi.fn(); const api = createPatientApi({ fetchImpl, getAccessToken: () => { throw new Error('private provider detail'); } });
    await expect(api.listPatients({})).rejects.toMatchObject({ code: 'SESSION_UNAVAILABLE' }); expect(fetchImpl).not.toHaveBeenCalled();
  });
  it('bounds a network timeout with no automatic retry', async () => {
    vi.useFakeTimers();
    const fetchImpl = vi.fn((url, { signal }) => new Promise((resolve, reject) => signal.addEventListener('abort', () => reject(new DOMException('timeout', 'AbortError')))));
    const api = createPatientApi({ fetchImpl, getAccessToken: () => 'token' });
    const rejected = expect(api.listPatients({ page: 0, size: 10 })).rejects.toMatchObject({ code: 'NETWORK_ERROR' });
    await vi.advanceTimersByTimeAsync(10001); await rejected; expect(fetchImpl).toHaveBeenCalledTimes(1);
  });
  it('binds all eight v3 endpoints and exact headers without retries', async () => {
    const { patients: [p], records: [r] } = makeFixtures();
    const fetchImpl = vi.fn(async (url, init) => reply(init.method === 'POST' ? 201 : 200, url.includes('?') ? { content: [url.includes('medical-records') ? r : p], page: 0, size: 10, totalElements: 1, totalPages: 1 } : url.includes('medical-records') ? r : p));
    const api = createPatientApi({ fetchImpl, getAccessToken: async () => 'host-session-token' });
    await api.listPatients({ phoneNumber: p.phoneNumber, page: 0, size: 10, sort: 'fullName,asc' }); await api.getPatient(p.id); await api.createPatient({ fullName: 'A' }); await api.updatePatient(p.id, { version: 0 });
    await api.listRecords(p.id, { page: 0, size: 10 }); await api.getRecord(p.id, r.id); await api.createRecord(p.id, { doctorId: r.doctorId }); await api.updateRecord(p.id, r.id, { version: 0 });
    expect(fetchImpl).toHaveBeenCalledTimes(8); const calls = fetchImpl.mock.calls;
    expect(calls[0][0]).toContain('/api/v3/patients?phoneNumber=%2B'); expect(calls[3][1].method).toBe('PUT');
    for (const [url, init] of calls) { expect(url).toMatch(/^\/api\/v3\/patients/); expect(init.headers.Authorization).toBe('Bearer host-session-token'); expect(url).not.toContain('doctor-service'); }
  });
  it.each([400, 401, 403, 404, 409, 422, 500, 503])('HTTP %s rejects with no preview fallback', async status => {
    const fetchImpl = vi.fn(async () => reply(status, null)); const api = createPatientApi({ fetchImpl, getAccessToken: () => 'host-token' });
    await expect(api.listPatients({ page: 0, size: 10 })).rejects.toMatchObject({ status }); expect(fetchImpl).toHaveBeenCalledTimes(1);
  });
  it('rejects malformed/mismatched envelope and imprecise response version', async () => {
    expect(() => parseEnvelope(200, { status: 201, message: 'OK', data: {} })).toThrow();
    const p = { ...makeFixtures().patients[0], version: Number.MAX_SAFE_INTEGER + 1 };
    const api = createPatientApi({ fetchImpl: async () => reply(200, p), getAccessToken: () => 'token' });
    await expect(api.getPatient(p.id)).rejects.toMatchObject({ code: 'INVALID_ENVELOPE' });
  });
  it('network mutation uncertainty never reports success or retries', async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new TypeError('offline')); const api = createPatientApi({ fetchImpl, getAccessToken: () => 'token' });
    await expect(api.createPatient({ fullName: 'A' })).rejects.toMatchObject({ code: 'NETWORK_ERROR', uncertain: true }); expect(fetchImpl).toHaveBeenCalledTimes(1);
  });
  it('wrong resource ownership, unknown request fields and unsafe versions fail closed', async () => {
    const [first, second] = makeFixtures().patients;
    const api = createPatientApi({ fetchImpl: async () => reply(200, second), getAccessToken: () => 'token' });
    await expect(api.getPatient(first.id)).rejects.toMatchObject({ code: 'INVALID_ENVELOPE' });
    expect(() => api.createPatient({ fullName: 'A', status: 'ACTIVE' })).toThrow();
    expect(() => api.updatePatient(first.id, { version: Number.MAX_SAFE_INTEGER + 1 })).toThrow();
    expect(() => api.updateRecord(first.id, makeFixtures().records[0].id, { doctorId: first.id })).toThrow();
  });
  it('a malformed successful write remains uncertain instead of reporting success', async () => {
    const api = createPatientApi({ fetchImpl: async () => reply(201, {}), getAccessToken: () => 'token' });
    await expect(api.createPatient({ fullName: 'A' })).rejects.toMatchObject({ code: 'INVALID_ENVELOPE', uncertain: true });
  });
  it('preview is explicit, in memory only and never calls fetch', async () => {
    const fetch = vi.fn(); vi.stubGlobal('fetch', fetch);
    expect(() => createPreviewAdapter({ development: false })).toThrow();
    const api = createPreviewAdapter({ latency: 0 }); const page = await api.listPatients({ page: 0, size: 5, sort: 'fullName,asc' });
    expect(page.content).toHaveLength(5); expect(page.totalElements).toBe(24);
    const created = await api.createPatient({ fullName: 'Minh họa mới', phoneNumber: '+84900000100', email: null, dateOfBirth: null, gender: 'UNKNOWN', address: null });
    expect((await api.getPatient(created.id)).fullName).toBe('Minh họa mới');
    expect((await api.updatePatient(created.id, { ...created, fullName: 'Minh họa sửa', version: 0 })).version).toBe(1);
    await expect(api.updatePatient(created.id, { version: 0 })).rejects.toMatchObject({ status: 409 }); expect(fetch).not.toHaveBeenCalled();
    const fresh = createPreviewAdapter({ latency: 0 }); expect((await fresh.listPatients({ page: 0, size: 5 })).totalElements).toBe(24);
  });
});
