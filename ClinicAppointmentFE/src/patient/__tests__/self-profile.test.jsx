import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
const mocks = vi.hoisted(() => ({ user: null, updateUserSession: vi.fn(), createPatient: vi.fn(), getOwnPatient: vi.fn(), updatePatient: vi.fn(), linkPatientProfile: vi.fn() }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({ user: mocks.user, updateUserSession: mocks.updateUserSession }) }));
vi.mock('../api/patientApi', () => ({ createPatientApi: () => mocks }));
vi.mock('../../services/authApi', () => ({ authApi: mocks }));
import { PatientOnboardingPage } from '../../pages/PatientOnboardingPage';
import { ProfilePage } from '../../pages/ProfilePage';

const patient = { id: 'profile-a', patientCode: 'PAT-A', fullName: 'Patient A', phoneNumber: '+84912345678',
  email: 'a@example.test', dateOfBirth: '2000-01-01', gender: 'FEMALE', address: 'Hà Nội', status: 'ACTIVE', version: 0 };
beforeEach(() => {
  vi.clearAllMocks();
  mocks.user = { id: 'user-a', username: 'patient_a', fullName: 'Patient A', phoneNumber: '0912345678', email: 'a@example.test', role: 'ROLE_PATIENT' };
  mocks.createPatient.mockResolvedValue(patient);
  mocks.getOwnPatient.mockResolvedValue(patient);
  mocks.updatePatient.mockResolvedValue({ ...patient, fullName: 'Updated', version: 1 });
  mocks.linkPatientProfile.mockResolvedValue({ ...mocks.user, patientId: patient.id });
  mocks.updateUserSession.mockReturnValue(true);
});
it('creates an initial profile, verifies the link, and updates the session before allowing booking', async () => {
  const complete = vi.fn(); const user = userEvent.setup();
  render(<PatientOnboardingPage onComplete={complete} />);
  await user.click(screen.getByRole('button', { name: 'Tạo hồ sơ' }));
  await waitFor(() => expect(complete).toHaveBeenCalledWith(patient));
  expect(mocks.createPatient).toHaveBeenCalledWith(expect.objectContaining({ fullName: 'Patient A', phoneNumber: '+84912345678' }));
  expect(mocks.linkPatientProfile).toHaveBeenCalledWith(patient.id);
  expect(mocks.updateUserSession).toHaveBeenCalledWith(expect.objectContaining({ patientId: patient.id }));
});
it('does not complete onboarding when ownership linking fails and allows retry', async () => {
  mocks.linkPatientProfile.mockRejectedValueOnce(new Error('Không thể xác minh hồ sơ'));
  const complete = vi.fn(); const user = userEvent.setup();
  render(<PatientOnboardingPage onComplete={complete} />);
  await user.click(screen.getByRole('button', { name: 'Tạo hồ sơ' }));
  await screen.findByText(/Không thể xác minh hồ sơ/);
  expect(complete).not.toHaveBeenCalled(); expect(mocks.updateUserSession).not.toHaveBeenCalled();
  await user.click(screen.getByRole('button', { name: 'Tạo hồ sơ' }));
  await waitFor(() => expect(complete).toHaveBeenCalledWith(patient));
  expect(mocks.createPatient).toHaveBeenCalledTimes(1);
});
it('lets a linked patient see and edit their profile without changing its status', async () => {
  mocks.user = { ...mocks.user, patientId: patient.id }; const user = userEvent.setup();
  render(<ProfilePage />);
  await user.click(await screen.findByRole('button', { name: 'Chỉnh sửa hồ sơ của tôi' }));
  expect(screen.queryByLabelText(/Trạng thái/)).not.toBeInTheDocument();
  const name = screen.getByLabelText(/Họ và tên/); await user.clear(name); await user.type(name, 'Updated');
  await user.click(screen.getByRole('button', { name: 'Lưu cập nhật' }));
  expect(await screen.findByText('Đã cập nhật hồ sơ cá nhân.')).toBeVisible();
  expect(mocks.updatePatient).toHaveBeenCalledWith(patient.id, expect.objectContaining({ fullName: 'Updated', status: 'ACTIVE', version: 0 }));
});
it('shows staff account information without profile-management controls', () => {
  mocks.user = { ...mocks.user, role: 'ROLE_STAFF' }; render(<ProfilePage />);
  expect(screen.getByText('patient_a')).toBeVisible();
  expect(mocks.getOwnPatient).not.toHaveBeenCalled();
  expect(screen.queryByRole('button', { name: /Chỉnh sửa|Tạo hồ sơ/ })).not.toBeInTheDocument();
});
