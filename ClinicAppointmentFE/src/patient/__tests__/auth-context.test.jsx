import React from 'react';
import { render, screen } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from '../../context/AuthContext';

const { adapter } = vi.hoisted(() => ({ adapter: vi.fn() }));
vi.mock('axios', async importOriginal => {
  const actual = await importOriginal();
  return {
    ...actual,
    default: {
      ...actual.default,
      create: config => actual.default.create({ ...config, adapter }),
    },
  };
});

const user = { id: 'patient-account', role: 'ROLE_PATIENT', patientId: 'patient-profile' };
function SessionProbe() {
  const { isLoading, isAuthenticated, capabilities } = useAuth();
  return <>
    <p>{isLoading ? 'Loading session' : isAuthenticated ? 'Authenticated' : 'Signed out'}</p>
    <output aria-label="Capabilities">{JSON.stringify(capabilities)}</output>
  </>;
}

beforeEach(() => {
  localStorage.clear();
  adapter.mockReset();
  adapter.mockImplementation(async config => ({
    status: 200,
    data: { data: config.url.endsWith('patient-capabilities') ? { createPatient: false, searchPatients: true } : user },
    headers: {},
    config,
  }));
});

function restoreSession() {
  localStorage.setItem('fclinic_token', 'access-token');
  localStorage.setItem('fclinic_user', JSON.stringify(user));
  render(<AuthProvider><SessionProbe /></AuthProvider>);
}

it('restores an authenticated session and loads capabilities through the real auth adapter', async () => {
  restoreSession();
  expect(await screen.findByText('Authenticated')).toBeVisible();
  await vi.waitFor(() => expect(screen.getByLabelText('Capabilities')).toHaveTextContent('"searchPatients":true'));
  expect(adapter).toHaveBeenCalledWith(expect.objectContaining({
    method: 'get',
    url: '/api/v1/auth/me/patient-capabilities',
    headers: expect.objectContaining({ Authorization: 'Bearer access-token' }),
  }));
});

it('keeps the app mounted with no capabilities when the capabilities request fails', async () => {
  adapter.mockImplementation(async config => {
    if (config.url.endsWith('patient-capabilities')) throw new Error('Service unavailable');
    return { status: 200, data: { data: user }, headers: {}, config };
  });
  restoreSession();
  expect(await screen.findByText('Authenticated')).toBeVisible();
  await vi.waitFor(() => expect(adapter).toHaveBeenCalledWith(expect.objectContaining({ url: '/api/v1/auth/me/patient-capabilities' })));
  expect(screen.getByLabelText('Capabilities')).toHaveTextContent('{}');
});

it('does not load capabilities without an authenticated session', async () => {
  render(<AuthProvider><SessionProbe /></AuthProvider>);
  expect(await screen.findByText('Signed out')).toBeVisible();
  expect(adapter).not.toHaveBeenCalled();
});
