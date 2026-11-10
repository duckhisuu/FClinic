import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError } from 'axios';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { Booking } from '../Booking';
import { PatientSelection } from '../PatientSelection';
import { apiClient, ApiError, patientApi } from '../api';

vi.mock('../api', async (original) => ({
  ...(await original()),
  patientApi: { get: vi.fn(), search: vi.fn() },
}));

const patient = {
  id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  fullName: 'Synthetic Patient', patientCode: 'PAT-SYNTHETIC', status: 'ACTIVE',
};
const selection = {
  doctor: { id: 'doctor', name: 'Synthetic Doctor', consultationFee: 100000 },
  slot: { id: 'slot', slotDate: '2099-01-02', startTime: '09:00:00', endTime: '10:00:00' },
};
const defaultAdapter = apiClient.defaults.adapter;
afterEach(() => { apiClient.defaults.adapter = defaultAdapter; });

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  vi.resetAllMocks();
});

it('retries the profile after a 503 without losing the selected slot or reason', async () => {
  const user = userEvent.setup();
  let finishLoading;
  patientApi.get
    .mockRejectedValueOnce(new ApiError('Temporarily unavailable', 503, 'DEPENDENCY_UNAVAILABLE'))
    .mockImplementationOnce(() => new Promise((resolve) => { finishLoading = resolve; }));

  render(<Booking role="ROLE_PATIENT" actorSubject="synthetic-account" patientId={patient.id} selection={selection} />);
  await screen.findByRole('alert');
  expect(screen.getByRole('button', { name: 'Xác nhận đặt lịch' })).toBeDisabled();
  await user.type(screen.getByLabelText(/Lý do khám/), 'Synthetic reason');
  await user.click(screen.getByRole('button', { name: 'Thử lại' }));
  await screen.findByRole('status');
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Thử lại' })).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Xác nhận đặt lịch' })).toBeDisabled();
  finishLoading(patient);

  await screen.findByText(patient.fullName);
  expect(screen.getByLabelText(/Lý do khám/)).toHaveValue('Synthetic reason');
  expect(screen.getByText('Synthetic Doctor')).toBeInTheDocument();
  expect(screen.getByText(/09:00–10:00/)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Xác nhận đặt lịch' })).toBeEnabled();
  expect(patientApi.get).toHaveBeenCalledTimes(2);
  expect(patientApi.get.mock.calls.every(([id]) => id === patient.id)).toBe(true);
  expect(patientApi.search).not.toHaveBeenCalled();
});

it('ignores a late profile response after leaving the booking page', async () => {
  let finishLoading;
  patientApi.get.mockImplementation(() => new Promise((resolve) => { finishLoading = resolve; }));
  const onSelect = vi.fn();
  const { unmount } = render(<PatientSelection patientId={patient.id} onSelect={onSelect} />);
  await waitFor(() => expect(patientApi.get).toHaveBeenCalledTimes(1));
  const signal = patientApi.get.mock.calls[0][1];
  unmount();
  expect(signal.aborted).toBe(true);
  finishLoading(patient);
  await Promise.resolve();
  expect(onSelect).not.toHaveBeenCalled();
});

it.each([502, 503, 504])('explains a gateway %s without an application error envelope', async (status) => {
  apiClient.defaults.adapter = async (config) => {
    throw new AxiosError('Unavailable', 'ERR_BAD_RESPONSE', config, null, {
      status, data: { error: 'Service Unavailable' }, headers: {},
    });
  };
  await expect(apiClient.get('/api/v3/patients/' + patient.id)).rejects.toMatchObject({
    status, code: 'DEPENDENCY_UNAVAILABLE',
    message: 'Dịch vụ tạm thời chưa sẵn sàng. Vui lòng thử lại sau ít phút.',
  });
});
