import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, it, vi } from 'vitest';
vi.mock('../../appointment/DoctorDirectory', () => ({ DoctorDirectory: () => <h2>Existing Doctor Directory</h2> }));
vi.mock('../../appointment/Booking', () => ({ Booking: () => <h2>Existing Booking</h2> }));
vi.mock('../../appointment/AppointmentHistory', () => ({ AppointmentHistory: () => <h2>Existing Appointment History</h2> }));
vi.mock('../../pages/DoctorSchedulePage', () => ({ DoctorSchedulePage: () => <h2>Existing Doctor Schedule</h2> }));
vi.mock('../../components/NotificationDrawer', () => ({ NotificationDrawer: ({ isOpen }) => isOpen ? <h2>Existing Notification Drawer</h2> : null }));
import App from '../../App';
it('preserves all existing navigation plus Patient without rewriting the shared application', async () => {
  const user = userEvent.setup(); render(<App />);
  expect(screen.getByText('Existing Doctor Directory')).toBeVisible();
  await user.click(screen.getByRole('button', { name: /Quản lý bác sĩ/ })); expect(screen.getByText('Existing Doctor Schedule')).toBeVisible();
  await user.click(screen.getByRole('button', { name: /Đặt lịch khám Xác nhận/ })); expect(screen.getByText('Existing Booking')).toBeVisible();
  await user.click(screen.getByRole('button', { name: /Quản lý lịch hẹn/ })); expect(screen.getByText('Existing Appointment History')).toBeVisible();
  await user.click(screen.getByRole('button', { name: 'Thông báo' })); expect(screen.getByText('Existing Notification Drawer')).toBeVisible();
  await user.click(screen.getByRole('button', { name: /Quản lý bệnh nhân/ })); expect(screen.getByRole('heading', { name: 'Quản lý bệnh nhân' })).toBeVisible();
});
