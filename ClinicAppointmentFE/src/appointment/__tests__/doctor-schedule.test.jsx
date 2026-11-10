import React from 'react';
import { beforeEach, it, vi, expect } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
const api = vi.hoisted(() => ({ schedule: vi.fn(), list: vi.fn(), create: vi.fn() }));
vi.mock('../api', () => ({ ApiError: class extends Error {}, doctorApi: { getDoctorSchedule: api.schedule, createDoctorSlot: api.create }, appointmentApi: { list: api.list } }));
vi.mock('../AppointmentHistory', () => ({ AppointmentHistory: () => <div>appointments</div> }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({ user: { id: 'doctor-account', doctorId: 'doctor-a' } }) }));
import { DoctorDashboardPage } from '../../pages/DoctorDashboardPage';
beforeEach(() => {
  vi.resetAllMocks();
  api.schedule.mockResolvedValue({ doctorId: 'doctor-a', fromDate: '2030-01-01', toDate: '2030-01-07', days: [
    { date: '2030-01-01', slots: [{ id: 'slot-a', startTime: '09:00:00', endTime: '10:00:00', isBooked: false, published: true }] },
  ] });
  api.list.mockResolvedValue({ totalElements: 1, content: [{ doctorId: 'doctor-a', appointmentDate: '2030-01-01', startTime: '09:00:00', status: 'CONFIRMED' }] });
});
async function open() { render(<DoctorDashboardPage />); fireEvent.click(screen.getByRole('button', { name: 'Lịch làm việc' })); }
it('shows Appointment occupancy even when the Doctor offer says isBooked=false, and refreshes after cancellation', async () => {
  await open(); await screen.findByText('Đã có lịch hẹn');
  expect(api.schedule).toHaveBeenCalledWith('doctor-a', 'WEEK', expect.any(String), expect.any(AbortSignal));
  expect(api.list).toHaveBeenCalledWith(expect.objectContaining({ doctorId: 'doctor-a', status: 'CONFIRMED' }), expect.any(AbortSignal));
  expect(screen.queryByRole('button', { name: /Chọn khám/ })).not.toBeInTheDocument();
  api.list.mockResolvedValue({ totalElements: 0, content: [] });
  fireEvent.click(screen.getByRole('button', { name: 'Tải lại lịch' }));
  await screen.findByText('Còn trống'); expect(screen.queryByText('Đã có lịch hẹn')).not.toBeInTheDocument();
});
it('does not advertise free appointments when the occupancy service fails', async () => {
  api.list.mockRejectedValue(new Error('Appointment unavailable')); await open();
  expect(await screen.findByRole('alert')).toHaveTextContent('Appointment unavailable');
  expect(screen.queryByText('Còn trống')).not.toBeInTheDocument();
});
it('publishes a full-hour offer only for the linked Doctor and surfaces duplicate conflicts', async () => {
  api.create.mockRejectedValue(new Error('Khung giờ đã tồn tại')); await open(); await screen.findByText('Đã có lịch hẹn');
  fireEvent.change(screen.getByLabelText('Ngày tạo ca'), { target: { value: '2030-01-02' } });
  fireEvent.click(screen.getByRole('button', { name: 'Thêm ca 60 phút' }));
  await waitFor(() => expect(api.create).toHaveBeenCalledWith('doctor-a', { slotDate: '2030-01-02', startTime: '08:00:00', endTime: '09:00:00' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Khung giờ đã tồn tại');
});
