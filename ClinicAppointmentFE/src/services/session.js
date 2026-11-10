import axios from 'axios';

let refreshing;
export const SESSION_EVENT = 'fclinic-session-updated';
const notify = () => window.dispatchEvent(new Event(SESSION_EVENT));
export function sameSessionIdentity(a, b) {
  return !!a?.id && a.id === b?.id && a.role === b.role
    && (a.patientId || null) === (b.patientId || null) && (a.doctorId || null) === (b.doctorId || null);
}
export function accessTokenForIdentity(expected) {
  let current;
  try { current = JSON.parse(localStorage.getItem('fclinic_user') || 'null'); } catch { /* fail closed */ }
  if (!sameSessionIdentity(expected, current)) throw new Error('SESSION_CHANGED');
  return localStorage.getItem('fclinic_token');
}
export function clearSessionStorage() {
  for (const key of ['fclinic_token', 'fclinic_refresh', 'fclinic_user']) localStorage.removeItem(key);
  for (const key of Object.keys(sessionStorage)) if (key.startsWith('fclinic.pending.')) sessionStorage.removeItem(key);
  notify();
}
// One refresh per session, without the bearer interceptor. Never refresh an old account's request
// into the newly logged-in account, and never change an idempotency key or request body.
export async function refreshSession(originalAuthorization) {
  const before = localStorage.getItem('fclinic_token');
  if (!before) return false;
  if (originalAuthorization && originalAuthorization !== `Bearer ${before}`) return false;
  const refresh = localStorage.getItem('fclinic_refresh');
  if (!refresh) { clearSessionStorage(); return false; }
  if (!refreshing) {
    refreshing = (async () => {
      try {
        const response = await axios.post(`${import.meta.env.VITE_API_GATEWAY_URL || ''}/api/v1/auth/refresh`,
          { refreshToken: refresh }, { timeout: 10000 });
        const data = response.data?.data;
        if (localStorage.getItem('fclinic_token') !== before) return false;
        const previousUser = JSON.parse(localStorage.getItem('fclinic_user') || 'null');
        if (typeof data?.accessToken !== 'string' || !data.user?.id || data.user.id !== previousUser?.id) throw new Error('Invalid session');
        localStorage.setItem('fclinic_token', data.accessToken);
        if (data.refreshToken) localStorage.setItem('fclinic_refresh', data.refreshToken);
        localStorage.setItem('fclinic_user', JSON.stringify(data.user)); notify(); return true;
      } catch {
        if (localStorage.getItem('fclinic_token') === before) clearSessionStorage();
        return false;
      } finally { refreshing = null; }
    })();
  }
  return refreshing;
}
