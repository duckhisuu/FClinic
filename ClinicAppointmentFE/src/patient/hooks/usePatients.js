import { useResource } from './useResource';
export const usePatients = (api, params, revision = 0) => useResource(signal => api.listPatients(params, signal), [api, JSON.stringify(params), revision]);
export const usePatient = (api, id, revision = 0) => useResource(signal => api.getPatient(id, signal), [api, id, revision]);
