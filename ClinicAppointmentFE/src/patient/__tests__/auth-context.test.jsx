import React from 'react';
import { render, screen, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
const api = vi.hoisted(() => ({ getMe: vi.fn(), getPatientCapabilities: vi.fn(), logout: vi.fn() }));
vi.mock('../../services/authApi', () => ({ authApi: api }));
import { AuthProvider, useAuth } from '../../context/AuthContext';
function Probe() {
  const auth = useAuth();
  return <><span>{auth.capabilities?.createPatient ? 'create-granted' : 'no-grant'}</span><span>{auth.isAuthenticated ? 'signed-in' : 'signed-out'}</span><button onClick={auth.logout}>Logout</button><button onClick={auth.refreshCapabilities}>Refresh capabilities</button></>;
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
