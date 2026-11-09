import { beforeEach, expect, it, vi } from 'vitest';
import axios from 'axios';
import { refreshSession, clearSessionStorage } from '../../services/session';
beforeEach(() => { localStorage.clear(); sessionStorage.clear(); vi.restoreAllMocks(); });
function login(token = 'old') { localStorage.setItem('fclinic_token', token); localStorage.setItem('fclinic_refresh', 'refresh'); localStorage.setItem('fclinic_user', JSON.stringify({ id: 'account-a' })); }
it('coalesces concurrent refresh and preserves verified account', async () => {
 login(); let resolve; const post = vi.spyOn(axios, 'post').mockImplementation(() => new Promise(done => { resolve = done; }));
 const a = refreshSession('Bearer old'); const b = refreshSession('Bearer old');
 resolve({ data: { data: { accessToken: 'new', refreshToken: 'rotated', user: { id: 'account-a' } } } });
 expect(await a).toBe(true); expect(await b).toBe(true); expect(post).toHaveBeenCalledTimes(1); expect(localStorage.getItem('fclinic_token')).toBe('new');
});
it('late refresh cannot restore a logged-out account or overwrite a new one', async () => {
 login(); let resolve; vi.spyOn(axios, 'post').mockImplementation(() => new Promise(done => { resolve = done; }));
 const request = refreshSession('Bearer old'); clearSessionStorage(); login('account-b-token');
 localStorage.setItem('fclinic_user', JSON.stringify({ id: 'account-b' }));
 resolve({ data: { data: { accessToken: 'late', user: { id: 'account-a' } } } });
 expect(await request).toBe(false); expect(localStorage.getItem('fclinic_token')).toBe('account-b-token');
});
it('failed refresh clears protected pending commands and never loops', async () => {
 login(); sessionStorage.setItem('fclinic.pending.account-a.book', 'protected');
 const post = vi.spyOn(axios, 'post').mockRejectedValue(new Error('expired'));
 expect(await refreshSession('Bearer old')).toBe(false); expect(localStorage.getItem('fclinic_token')).toBeNull(); expect(sessionStorage.length).toBe(0); expect(post).toHaveBeenCalledTimes(1);
});
