import React from 'react';
import { render, screen, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
const api = vi.hoisted(() => ({ getMe: vi.fn(), getPatientCapabilities: vi.fn(), logout: vi.fn() }));
vi.mock('../../services/authApi', () => ({ authApi: api }));
import { AuthProvider, useAuth } from '../../context/AuthContext';
import { accessTokenForIdentity } from '../../services/session';
function Probe() {
  const auth = useAuth();
  return <><span>{auth.capabilities?.createPatient ? 'create-granted' : 'no-grant'}</span><span>{auth.isAuthenticated ? 'signed-in' : 'signed-out'}</span><span>{auth.user?.role}</span><button onClick={auth.logout}>Logout</button><button onClick={auth.refreshCapabilities}>Refresh capabilities</button></>;
}
beforeEach(() => {
  localStorage.clear(); sessionStorage.clear(); vi.clearAllMocks();
  const user = { id: 'verified-staff', role: 'ROLE_STAFF' };
  localStorage.setItem('fclinic_user', JSON.stringify(user)); localStorage.setItem('fclinic_token', 'token'); localStorage.setItem('fclinic_refresh', 'refresh');
  api.getMe.mockResolvedValue(user); api.getPatientCapabilities.mockResolvedValue({ createPatient: true }); api.logout.mockReturnValue(new Promise(() => {}));
});
it('does not replace newly assigned capabilities with an older in-flight response',async()=>{
 let resolveOld;
 api.getPatientCapabilities.mockReturnValueOnce(new Promise(resolve=>{resolveOld=resolve})).mockResolvedValueOnce({createPatient:true});
 render(<AuthProvider><Probe/></AuthProvider>);
 await screen.findByText('signed-in');
 await userEvent.click(screen.getByText('Refresh capabilities'));
 await screen.findByText('create-granted');
 await act(async()=>resolveOld({createPatient:false}));
 expect(screen.getByText('create-granted')).toBeVisible();
});
it('publishes backend capabilities and clears protected state before remote logout completes', async () => {
  sessionStorage.setItem('fclinic.pending.verified-staff.book', 'private');
  render(<AuthProvider><Probe /></AuthProvider>);
  await screen.findByText('create-granted'); await screen.findByText('signed-in');
  await userEvent.click(screen.getByText('Logout'));
  await screen.findByText('signed-out'); expect(localStorage.getItem('fclinic_token')).toBeNull(); expect(sessionStorage.length).toBe(0);
});
it('clears the old Staff workspace on another-tab login and verifies the replacement identity', async () => {
  render(<AuthProvider><Probe /></AuthProvider>);
  await screen.findByText('create-granted');
  sessionStorage.setItem('fclinic.pending.verified-staff.book','private');
  let resolveMe;
  api.getMe.mockReturnValueOnce(new Promise(resolve=>{resolveMe=resolve}));
  api.getPatientCapabilities.mockResolvedValue({createPatient:false});
  const next={id:'other-patient',role:'ROLE_PATIENT',patientId:'linked-profile'};
  await act(async()=>{
    localStorage.setItem('fclinic_token','patient-token');localStorage.setItem('fclinic_user',JSON.stringify(next));
    window.dispatchEvent(new StorageEvent('storage',{key:'fclinic_user'}));
    window.dispatchEvent(new StorageEvent('storage',{key:'fclinic_token'}));
  });
  expect(screen.getByText('signed-out')).toBeVisible();expect(screen.queryByText('ROLE_STAFF')).toBeNull();
  expect(screen.getByText('no-grant')).toBeVisible();expect(sessionStorage.length).toBe(0);
  expect(api.getMe).toHaveBeenCalledTimes(2); // initial verification + one cross-tab verification
  await act(async()=>resolveMe(next));
  expect(screen.getByText('ROLE_PATIENT')).toBeVisible();expect(screen.getByText('signed-in')).toBeVisible();
});
it('honors another-tab logout and rejects requests from an old account identity before fetch', async () => {
  const staff=JSON.parse(localStorage.getItem('fclinic_user'));
  expect(accessTokenForIdentity(staff)).toBe('token');
  render(<AuthProvider><Probe /></AuthProvider>);await screen.findByText('create-granted');
  await act(async()=>{localStorage.removeItem('fclinic_token');localStorage.removeItem('fclinic_user');window.dispatchEvent(new StorageEvent('storage',{key:'fclinic_token'}));});
  expect(screen.getByText('signed-out')).toBeVisible();expect(screen.getByText('no-grant')).toBeVisible();
  localStorage.setItem('fclinic_user',JSON.stringify({id:'different-user',role:'ROLE_PATIENT'}));localStorage.setItem('fclinic_token','different-token');
  expect(()=>accessTokenForIdentity(staff)).toThrow('SESSION_CHANGED');
});
