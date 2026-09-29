import React, { useState, useEffect } from 'react';
import { Bell, X, RefreshCw, Send, CheckCircle, Clock, Smartphone } from 'lucide-react';
import { notificationApi } from '../services/api';

export const NotificationDrawer = ({ isOpen, onClose }) => {
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(false);

  const fetchNotifications = async () => {
    setLoading(true);
    try {
      const data = await notificationApi.getRecentNotifications(30);
      setNotifications(data || []);
    } catch {
      // fallback
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (isOpen) {
      fetchNotifications();
    }
  }, [isOpen]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 overflow-hidden">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-slate-900/60 backdrop-blur-sm transition-opacity"
        onClick={onClose}
      />

      <div className="fixed inset-y-0 right-0 max-w-full flex pl-10">
        <div className="w-screen max-w-md bg-white shadow-2xl flex flex-col border-l border-slate-200">
          {/* Header */}
          <div className="p-5 border-b border-slate-100 flex items-center justify-between bg-gradient-to-r from-teal-700 to-teal-900 text-white">
            <div className="flex items-center gap-2.5">
              <div className="p-2 bg-white/10 rounded-xl backdrop-blur-md">
                <Bell className="w-5 h-5 text-teal-200" />
              </div>
              <div>
                <h3 className="font-bold text-base">Trung tâm Thông báo</h3>
                <p className="text-xs text-teal-200">Thông báo SMS & Email xác nhận</p>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <button
                onClick={fetchNotifications}
                disabled={loading}
                className="p-2 text-white/80 hover:text-white hover:bg-white/10 rounded-lg transition-colors"
                title="Làm mới"
              >
                <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
              </button>
              <button
                onClick={onClose}
                className="p-2 text-white/80 hover:text-white hover:bg-white/10 rounded-lg transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
          </div>

          {/* List */}
          <div className="flex-1 overflow-y-auto p-4 space-y-3">
            {notifications.length === 0 ? (
              <div className="text-center py-12 text-slate-400">
                <Smartphone className="w-12 h-12 mx-auto stroke-1 text-slate-300 mb-3" />
                <p className="text-sm font-medium">Chưa có thông báo nào</p>
                <p className="text-xs text-slate-400 mt-1">Khi bệnh nhân đặt hoặc hủy lịch khám, tin nhắn thông báo sẽ được lưu tại đây.</p>
              </div>
            ) : (
              notifications.map((item, idx) => (
                <div
                  key={item.id || idx}
                  className="p-4 rounded-xl border border-slate-100 bg-slate-50/70 hover:bg-slate-50 transition-colors shadow-sm"
                >
                  <div className="flex items-center justify-between mb-2">
                    <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800">
                      <CheckCircle className="w-3 h-3 text-emerald-600" />
                      {item.channel || 'SMS'}
                    </span>
                    <span className="text-xs text-slate-400 flex items-center gap-1">
                      <Clock className="w-3 h-3" />
                      {item.createdAt ? new Date(item.createdAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }) : 'Vừa xong'}
                    </span>
                  </div>

                  <p className="text-xs font-semibold text-slate-800 mb-1">{item.message}</p>

                  <div className="mt-2.5 pt-2 border-t border-slate-200/60 flex items-center justify-between text-[11px] text-slate-500">
                    <span>Mã hẹn: <strong className="text-teal-700">{item.bookingCode}</strong></span>
                    <span>Người nhận: <strong>{item.recipientName}</strong></span>
                  </div>
                </div>
              ))
            )}
          </div>

          {/* Footer */}
          <div className="p-4 border-t border-slate-100 bg-slate-50 text-center">
            <p className="text-xs text-slate-500">
              Hệ thống gửi tin nhắn xác nhận tự động qua SMS và Email
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};
