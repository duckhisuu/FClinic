import { useResource } from './useResource';
export const useMedicalRecords = (api, patientId, params, revision = 0) => useResource(signal => api.listRecords(patientId, params, signal), [api, patientId, JSON.stringify(params), revision]);
export const useMedicalRecord = (api, patientId, recordId, revision = 0) => useResource(signal => api.getRecord(patientId, recordId, signal), [api, patientId, recordId, revision]);
