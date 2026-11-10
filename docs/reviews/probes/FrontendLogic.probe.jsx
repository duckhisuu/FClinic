// The runner temporarily places this file under src/appointment/__tests__.
// `it.fails` records the expected invariant that CURRENT production code violates.
import React from 'react';
import { beforeEach, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
const state = vi.hoisted(() => ({
  user: { id: 'account-b', fullName: 'Synthetic B', phoneNumber: '+84900000002', email: 'b@example.test', role: 'ROLE_DOCTOR', doctorId: 'doctor-b' },
  create: vi.fn(), link: vi.fn(), getDoctors: vi.fn(), getDoctorSlots: vi.fn(),
}));
vi.mock('../api', () => ({
  patientApi: { create: state.create },
  doctorApi: { getDoctors: state.getDoctors, getDoctorSlots: state.getDoctorSlots },
}));
vi.mock('../../services/authApi', () => ({ authApi: { linkPatientProfile: state.link } }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({ user: state.user, updateUserSession: () => true }) }));
// Stub the other dashboard tab so this audit remains about the reachable schedule branch.
vi.mock('../AppointmentHistory', () => ({ AppointmentHistory: () => <div>history</div> }));
import { PatientOnboardingPage } from '../../pages/PatientOnboardingPage';
import { DoctorDashboardPage } from '../../pages/DoctorDashboardPage';
beforeEach(() => { sessionStorage.clear(); localStorage.clear(); vi.resetAllMocks(); });

it.fails('onboarding must not reuse a different account pending profile', async () => {
  sessionStorage.setItem('fclinic.pending.patient', JSON.stringify({
    id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', createdBy: 'account-a', fullName: 'Synthetic A',
  }));
  state.link.mockRejectedValue(Object.assign(new Error('Trusted provisioning required'), { status: 403 }));
  const { container } = render(<PatientOnboardingPage />);
  fireEvent.change(container.querySelector('input[type="date"]'), { target: { value: '1995-01-01' } });
  fireEvent.click(screen.getByRole('button', { name: /Lưu & Tiếp tục/ }));
  await waitFor(() => expect(state.link).toHaveBeenCalled());
  expect(state.create).toHaveBeenCalledTimes(1);
  expect(state.link).not.toHaveBeenCalledWith('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
});

it.fails('reachable Doctor work schedule must handle a slot click without throwing', async () => {
  state.getDoctors.mockResolvedValue([{ id: 'doctor-b', name: 'Synthetic Doctor', specialty: 'General', consultationFee: 100, active: true }]);
  state.getDoctorSlots.mockResolvedValue([{ id: 'slot-b', startTime: '09:00:00', endTime: '10:00:00', isBooked: false }]);
  const errors = [];
  const listener = event => { errors.push(event.error?.message || event.message); event.preventDefault(); };
  window.addEventListener('error', listener);
  try {
    render(<DoctorDashboardPage />);
    fireEvent.click(screen.getByRole('button', { name: 'Lịch làm việc' }));
    const button = await screen.findByRole('button', { name: /09:00 - 10:00 Chọn khám/ });
    fireEvent.click(button);
    expect(errors).toEqual([]);
  } finally { window.removeEventListener('error', listener); }
});
