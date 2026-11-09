import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { NotificationDrawer } from '../../components/NotificationDrawer';
import { apiClient, notificationApi } from '../../services/api';

vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({ user: { id: 'patient-a', role: 'ROLE_PATIENT' } }) }));

const item = { id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', recipientName: 'Người nhận A', recipientEmail: 'a@example.test',
  recipientType: 'PATIENT', type: 'APPOINTMENT_CONFIRMATION', status: 'PERMANENT_FAILURE', subject: 'Lịch hẹn riêng của A' };

describe('notification transport', () => {
  it('uses the current-user endpoint without a caller-supplied user ID', async () => {
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({ content: [item], totalElements: 1 });
    await notificationApi.getUserNotifications({ page: 0, size: 20, userId: 'someone-else' });
    expect(get).toHaveBeenCalledWith('/api/v1/notifications/me?page=0&size=20', { signal: undefined });
  });

  it('propagates access and network errors instead of returning shared mock notifications', async () => {
    vi.spyOn(apiClient, 'get').mockRejectedValue(new Error('Không được phép'));
    vi.spyOn(apiClient, 'post').mockRejectedValue(new Error('Không được phép'));
    await expect(notificationApi.getUserNotifications()).rejects.toThrow('Không được phép');
    await expect(notificationApi.getAdminNotifications()).rejects.toThrow('Không được phép');
    await expect(notificationApi.getNotificationById(item.id)).rejects.toThrow('Không được phép');
    await expect(notificationApi.retryNotification(item.id)).rejects.toThrow('Không được phép');
  });
});

describe('personal notification drawer', () => {
  it('fetches only the personal inbox and hides admin retry controls', async () => {
    const mine = vi.spyOn(notificationApi, 'getUserNotifications').mockResolvedValue({ content: [item], totalElements: 1 });
    const admin = vi.spyOn(notificationApi, 'getAdminNotifications');
    render(<NotificationDrawer isOpen onClose={() => {}} />);
    expect(await screen.findByText('Lịch hẹn riêng của A')).toBeInTheDocument();
    expect(mine).toHaveBeenCalled();
    expect(admin).not.toHaveBeenCalled();
    expect(screen.queryByText('Thử lại ngay')).not.toBeInTheDocument();
  });

  it('clears previously displayed rows and surfaces an access failure', async () => {
    vi.spyOn(notificationApi, 'getUserNotifications')
      .mockResolvedValueOnce({ content: [item], totalElements: 1 })
      .mockRejectedValueOnce(new Error('Phiên đăng nhập đã hết hạn'));
    render(<NotificationDrawer isOpen onClose={() => {}} />);
    await screen.findByText('Lịch hẹn riêng của A');
    fireEvent.click(screen.getByTitle('Làm mới danh sách'));
    expect(await screen.findByRole('alert')).toHaveTextContent('Phiên đăng nhập đã hết hạn');
    await waitFor(() => expect(screen.queryByText('Lịch hẹn riêng của A')).not.toBeInTheDocument());
  });
});
