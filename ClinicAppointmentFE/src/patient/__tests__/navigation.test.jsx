import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
const auth = vi.hoisted(() => ({ user: null }));
vi.mock('../../context/AuthContext', () => ({ AuthProvider: ({ children }) => children, useAuth: () => ({ user: auth.user, isAuthenticated: !!auth.user, isLoading: false, logout: vi.fn() }) }));
vi.mock('../../appointment/DoctorDirectory', () => ({ DoctorDirectory: () => <h2>Existing Doctor Directory</h2> }));
vi.mock('../../appointment/Booking', () => ({ Booking: ({ patientId, actorSubject }) => <h2>Booking {patientId} {actorSubject}</h2> }));
vi.mock('../../appointment/AppointmentHistory', () => ({ AppointmentHistory: () => <h2>Existing Appointment History</h2> }));
vi.mock('../../pages/StaffWalkinPage', () => ({ StaffWalkinPage: ({ onSelectPatientForBooking }) => <button onClick={() => onSelectPatientForBooking({ id: 'walk-in', fullName: 'Walk In' })}>Select walk-in</button> }));
vi.mock('../../patient/PatientModule', () => ({ PatientModule: ({ session }) => <h2>Patient session {typeof session?.getAccessToken}</h2> }));
vi.mock('../../components/NotificationDrawer', () => ({ NotificationDrawer: () => null }));
import App from '../../App';
beforeEach(() => { auth.user = { id: 'staff-a', fullName: 'Same Name', role: 'ROLE_STAFF' }; });
it('uses role navigation and supplies the Patient host session', async () => {
  const user = userEvent.setup(); render(<App />);
  await user.click(screen.getByRole('button', { name: /Bác sĩ & giờ khám/ })); expect(screen.getByText('Existing Doctor Directory')).toBeVisible();
  await user.click(screen.getByRole('button', { name: /Đặt lịch tại quầy/ })); expect(screen.getByText(/Booking/)).toBeVisible();
  await user.click(screen.getByRole('button', { name: /Quản lý lịch hẹn/ })); expect(screen.getByText('Existing Appointment History')).toBeVisible();
  await user.click(screen.getByRole('button', { name: /Quản lý bệnh nhân/ })); expect(screen.getByText('Patient session function')).toBeVisible();
});
it('clears walk-in selection when switching to an account with the same name', async () => {
  const user = userEvent.setup(); const view = render(<App />);
  await user.click(screen.getByRole('button', { name: 'Select walk-in' }));
  auth.user = { id: 'patient-b', fullName: 'Same Name', role: 'ROLE_PATIENT', patientId: 'self-b' }; view.rerender(<App />);
  await user.click(screen.getByRole('button', { name: /Đặt lịch khám/ }));
  expect(screen.getByText('Booking self-b patient-b')).toBeVisible(); expect(screen.queryByText(/Hồ sơ đang chọn/)).not.toBeInTheDocument();
});