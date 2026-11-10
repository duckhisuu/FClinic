import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
import { PatientWorkspace } from '../PatientModule';

const patient = { id: '74fba630-5953-44b8-86d6-bc236ac1213b', fullName: 'Nguyễn Văn Hùng', gender: 'MALE', status: 'ACTIVE', phoneNumber: '+84900000001' };
const api = { mode: 'live', listPatients: vi.fn(), getPatient: vi.fn() };
const props = { api, registerGuard: () => {}, setParentBusy: () => {} };
beforeEach(() => {
  window.history.replaceState({}, '', '#patient-records');
  vi.clearAllMocks();
  api.listPatients.mockResolvedValue({ content: [patient], page: 0, size: 10, totalElements: 1, totalPages: 1 });
  api.getPatient.mockResolvedValue(patient);
});
it('keeps a patient on their own profile when they use the directory breadcrumb', async () => {
  render(<PatientWorkspace {...props} session={{ patientId: patient.id, permissions: { searchPatients: false } }} />);
  await screen.findByRole('heading', { name: 'Thông tin hành chính' });
  await userEvent.click(screen.getByRole('button', { name: 'Bệnh nhân', exact: true }));
  expect(screen.getByRole('heading', { name: 'Hồ sơ bệnh nhân' })).toBeVisible();
  expect(api.listPatients).not.toHaveBeenCalled();
  expect(window.location.hash).toContain(`patientId=${patient.id}`);
});
it('does not request a forbidden directory before search permission is confirmed', async () => {
  const { rerender } = render(<PatientWorkspace {...props} session={{ permissions: {} }} />);
  expect(screen.getByText('Chưa có quyền tra cứu bệnh nhân')).toBeVisible();
  expect(api.listPatients).not.toHaveBeenCalled();
  rerender(<PatientWorkspace {...props} session={{ permissions: { searchPatients: true } }} />);
  expect(await screen.findByText('Nguyễn Văn Hùng')).toBeVisible();
  expect(api.listPatients).toHaveBeenCalledTimes(1);
  expect(screen.queryByRole('button', { name: 'Tạo bệnh nhân' })).not.toBeInTheDocument();
});
