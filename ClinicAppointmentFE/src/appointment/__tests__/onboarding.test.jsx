import React from 'react';
import { it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
const state = vi.hoisted(() => ({ auth: null, get: vi.fn(), onboard: vi.fn(), update: vi.fn() }));
vi.mock('../../services/authApi', () => ({ authApi: { getPatientOnboarding: state.get, onboardPatient: state.onboard } }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => state.auth }));
import { PatientOnboardingPage } from '../../pages/PatientOnboardingPage';
const profile = { fullName: 'Synthetic Patient', phoneNumber: '+84900000001', dateOfBirth: '1995-01-01', gender: 'OTHER', address: null };
function session(id = 'account-a', key = 'session-a') {
  state.auth = { user: { id, fullName: profile.fullName, phoneNumber: profile.phoneNumber, email: id + '@example.test' }, sessionKey: key, updateUserSession: state.update };
  localStorage.setItem('fclinic_user', JSON.stringify(state.auth.user)); localStorage.setItem('fclinic_session_id', key);
}
beforeEach(() => {
  localStorage.clear(); sessionStorage.clear(); vi.resetAllMocks(); session(); state.update.mockReturnValue(true);
  state.get.mockImplementation(() => Promise.resolve({ user: state.auth.user, profile: null }));
});
async function fill(container) {
  const submit = screen.getByRole('button', { name: /Lưu & Tiếp tục/ });
  await waitFor(() => expect(submit).toBeEnabled());
  fireEvent.change(container.querySelector('input[type="date"]'), { target: { value: profile.dateOfBirth } });
  fireEvent.change(container.querySelector('select'), { target: { value: profile.gender } });
  return submit;
}
it('submits demographics to trusted orchestration without any browser-selected UUID', async () => {
  sessionStorage.setItem('fclinic.pending.patient', JSON.stringify({ id: 'another-patient' }));
  state.onboard.mockResolvedValue({ ...state.auth.user, patientId: 'server-selected-profile' });
  const done = vi.fn(), { container } = render(<PatientOnboardingPage onComplete={done} />);
  fireEvent.click(await fill(container));
  await waitFor(() => expect(done).toHaveBeenCalledTimes(1));
  expect(state.onboard).toHaveBeenCalledWith(profile);
  expect(state.update).toHaveBeenCalledWith(expect.objectContaining({ patientId: 'server-selected-profile' }));
  expect(sessionStorage.getItem('fclinic.pending.patient')).toBeNull();
});
it('keeps the exact request after response loss and prevents duplicate concurrent submits', async () => {
  state.onboard.mockRejectedValueOnce(Object.assign(new Error('Response lost'), { status: 503 }))
    .mockResolvedValueOnce({ ...state.auth.user, patientId: 'server-profile' });
  const { container } = render(<PatientOnboardingPage />); fireEvent.click(await fill(container));
  await screen.findByText('Response lost');
  expect(screen.getByPlaceholderText('Ví dụ: Nguyễn Văn An')).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: 'Tiếp tục khởi tạo hồ sơ' }));
  await waitFor(() => expect(state.update).toHaveBeenCalledTimes(1));
  expect(state.onboard.mock.calls[0][0]).toEqual(state.onboard.mock.calls[1][0]);
});
it('restores server-held progress after a remount or a different device', async () => {
  state.get.mockResolvedValue({ user: state.auth.user, profile });
  state.onboard.mockResolvedValue({ ...state.auth.user, patientId: 'same-profile' });
  const { unmount } = render(<PatientOnboardingPage />);
  await screen.findByRole('button', { name: 'Tiếp tục khởi tạo hồ sơ' }); unmount();
  render(<PatientOnboardingPage />);
  const submit = await screen.findByRole('button', { name: 'Tiếp tục khởi tạo hồ sơ' });
  await waitFor(() => expect(submit).toBeEnabled()); fireEvent.click(submit);
  await waitFor(() => expect(state.update).toHaveBeenCalledTimes(1));
  expect(state.onboard).toHaveBeenCalledWith(profile); expect(state.onboard).toHaveBeenCalledTimes(1);
});
it('ignores a late completion after switching account and does not recreate old cached state', async () => {
  let resolve; state.onboard.mockReturnValue(new Promise(done => { resolve = done; }));
  const done = vi.fn(), { container, rerender } = render(<PatientOnboardingPage onComplete={done} />);
  fireEvent.click(await fill(container)); await waitFor(() => expect(state.onboard).toHaveBeenCalledTimes(1));
  session('account-b', 'session-b'); rerender(<PatientOnboardingPage onComplete={done} />);
  await act(async () => resolve({ id: 'account-a', patientId: 'profile-a' }));
  expect(state.update).not.toHaveBeenCalled(); expect(done).not.toHaveBeenCalled(); expect(sessionStorage.length).toBe(0);
  expect(screen.queryByText(/Đã hoàn tất hồ sơ/)).not.toBeInTheDocument();
});
it('blocks creation until current server progress can be read', async () => {
  state.get.mockRejectedValueOnce(new Error('Server unavailable'));
  render(<PatientOnboardingPage />); await screen.findByText('Server unavailable');
  expect(screen.getByRole('button', { name: /Lưu & Tiếp tục/ })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: 'Tải lại tiến trình' }));
  await waitFor(() => expect(screen.getByRole('button', { name: /Lưu & Tiếp tục/ })).toBeEnabled());
  expect(state.onboard).not.toHaveBeenCalled();
});