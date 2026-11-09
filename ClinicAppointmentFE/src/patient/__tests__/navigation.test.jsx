import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
const auth = vi.hoisted(() => ({ user: null }));
vi.mock('../../context/AuthContext', () => ({ AuthProvider: ({ children }) => children, useAuth: () => ({ user: auth.user, isAuthenticated: !!auth.user, isLoading: false, logout: vi.fn() }) }));
vi.mock('../../appointment/DoctorDirectory', () => ({ DoctorDirectory: ({ onPick }) => <><h2>Existing Doctor Directory</h2><button onClick={() => onPick({ id: 'doctor' }, { id: 'slot' })}>Select slot</button></> }));
vi.mock('../../appointment/Booking', () => ({ Booking: ({ patientId, actorSubject, role, selection }) => <><h2>Booking {patientId} {actorSubject}</h2><p>Booking role {role}</p>{selection && <p>Selected slot</p>}</> }));
vi.mock('../../appointment/AppointmentHistory', () => ({ AppointmentHistory: ({ role, patientId }) => <><h2>Existing Appointment History</h2><p>History role {role}</p>{patientId && <p>History patient {patientId}</p>}</> }));
vi.mock('../../pages/StaffWalkinPage', () => ({ StaffWalkinPage: ({ onSelectPatientForBooking, onManageProfiles }) => <><button onClick={() => onSelectPatientForBooking({ id: 'walk-in', fullName: 'Walk In' })}>Select walk-in</button><button onClick={onManageProfiles}>Mở quản lý bệnh nhân</button></> }));
vi.mock('../../patient/PatientModule', () => ({ PatientModule: ({ session }) => <h2>Patient session {typeof session?.getAccessToken}</h2> }));
vi.mock('../../components/NotificationDrawer', () => ({ NotificationDrawer: () => null }));
import App from '../../App';
beforeEach(() => { auth.user = { id: 'staff-a', fullName: 'Same Name', role: 'ROLE_STAFF' }; });
it('uses role navigation and supplies the Patient host session', async () => {
  const user = userEvent.setup(); render(<App />);
  await user.click(screen.getByRole('button', { name: /Bác sĩ & giờ khám/ })); expect(screen.getByText('Existing Doctor Directory')).toBeVisible();
  await user.click(screen.getByRole('button', { name: /Đặt lịch tại quầy/ })); expect(screen.getByRole('heading', { name: /Booking/ })).toBeVisible();
  await user.click(screen.getByRole('button', { name: /Quản lý lịch hẹn/ })); expect(screen.getByText('Existing Appointment History')).toBeVisible();
  await user.click(screen.getByRole('button', { name: /Hồ sơ bệnh án/ })); expect(screen.getByText('Patient session function')).toBeVisible();
});
it('clears walk-in selection when switching to an account with the same name', async () => {
  const user = userEvent.setup(); const view = render(<App />);
  await user.click(screen.getByRole('button', { name: 'Select walk-in' }));
  await user.click(screen.getByRole('button', { name: /Đặt lịch tại quầy/ }));
  expect(screen.getByText('Booking walk-in staff-a')).toBeVisible();
  expect(screen.getByText('Booking role ROLE_STAFF')).toBeVisible();
  auth.user = { id: 'patient-b', fullName: 'Same Name', role: 'ROLE_PATIENT', patientId: 'self-b' }; view.rerender(<App />);
  await user.click(screen.getByRole('button', { name: /Đặt lịch khám/ }));
  expect(screen.getByText('Booking self-b patient-b')).toBeVisible(); expect(screen.queryByText(/Hồ sơ đang chọn/)).not.toBeInTheDocument();
});
it('opens patient management from the walk-in screen', async () => {
  const user = userEvent.setup(); render(<App />);
  await user.click(screen.getByRole('button', { name: 'Mở quản lý bệnh nhân' }));
  expect(screen.getByText('Patient session function')).toBeVisible();
});
it('does not restrict staff appointment history to the selected walk-in patient', async () => {
  const user = userEvent.setup(); render(<App />);
  await user.click(screen.getByRole('button', { name: 'Select walk-in' }));
  await user.click(screen.getByRole('button', { name: /Quản lý lịch hẹn/ }));
  expect(screen.getByText('History role ROLE_STAFF')).toBeVisible();
  expect(screen.queryByText(/History patient/)).not.toBeInTheDocument();
});
it('clears walk-in selection when another staff account signs in', async () => {
  const user = userEvent.setup(); const view = render(<App />);
  await user.click(screen.getByRole('button', { name: 'Select walk-in' }));
  auth.user = { id: 'staff-b', fullName: 'Same Name', role: 'ROLE_STAFF' }; view.rerender(<App />);
  expect(screen.getByRole('button', { name: 'Select walk-in' })).toBeVisible();
  await user.click(screen.getByRole('button', { name: /Đặt lịch tại quầy/ }));
  expect(screen.getByText('Booking staff-b')).toBeVisible();
});
it('clears a selected slot across patient account changes and passes the patient history scope', async () => {
  auth.user = { id: 'patient-a', role: 'ROLE_PATIENT', patientId: 'self-a' };
  const user = userEvent.setup(); const view = render(<App />);
  await user.click(screen.getByRole('button', { name: 'Select slot' }));
  expect(screen.getByText('Selected slot')).toBeVisible();
  auth.user = { id: 'patient-b', role: 'ROLE_PATIENT', patientId: 'self-b' }; view.rerender(<App />);
  await user.click(screen.getByRole('button', { name: /Đặt lịch khám/ }));
  expect(screen.queryByText('Selected slot')).not.toBeInTheDocument();
  expect(screen.getByText('Booking role ROLE_PATIENT')).toBeVisible();
  await user.click(screen.getByRole('button', { name: /Lịch hẹn của tôi/ }));
  expect(screen.getByText('History role ROLE_PATIENT')).toBeVisible();
  expect(screen.getByText('History patient self-b')).toBeVisible();
});
