import React, { useState, useEffect, useCallback } from 'react';
import {
  Bell,
  X,
  RefreshCw,
  Mail,
  Smartphone,
  CheckCircle2,
  AlertTriangle,
  Clock,
  RotateCcw,
  User,
  Stethoscope,
  Filter,
  CheckCircle,
  XCircle,
  Calendar,
} from 'lucide-react';
import { notificationApi } from '../services/api';

export const NotificationDrawer = ({ isOpen, onClose, onNotify }) => {
  const [notifications, setNotifications] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [recipientFilter, setRecipientFilter] = useState('ALL');
  const [retryingId, setRetryingId] = useState(null);

  const fetchNotifications = useCallback(async () => {
    setLoading(true);
    try {
      const res = await notificationApi.getAdminNotifications({
        status: statusFilter === 'ALL' ? undefined : statusFilter,
        size: 50,
      });
      setNotifications(res?.content || []);
      setTotalElements(res?.totalElements || 0);
    } catch {
      // fallback handled in api.js
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    if (isOpen) {
      fetchNotifications();
      const interval = setInterval(fetchNotifications, 8000);
      return () => clearInterval(interval);
    }
  }, [isOpen, fetchNotifications]);

  const handleRetry = async (item) => {
    setRetryingId(item.id);
    try {
      await notificationApi.retryNotification(item.id);
      if (onNotify) {
        onNotify(`Đã lên lịch gửi lại thông báo #${item.id} cho ${item.recipientName}`, {
          type: 'success',
          title: 'Thử lại thành công',
        });
      }
      await fetchNotifications();
    } catch (err) {
      if (onNotify) {
        onNotify(err.message || 'Không thể thử lại gửi thông báo', {
          type: 'error',
          title: 'Thử lại thất bại',
        });
      }
    } finally {
      setRetryingId(null);
    }
  };

  if (!isOpen) return null;

  // Filter in-memory for recipient type if selected
  const displayedNotifications = notifications.filter((item) => {
    if (recipientFilter === 'ALL') return true;
    return item.recipientType === recipientFilter;
  });

  const sentCount = notifications.filter((n) => n.status === 'SENT').length;
  const failedCount = notifications.filter(
    (n) => n.status === 'FAILED' || n.status === 'PERMANENT_FAILURE'
  ).length;

  const getTypeLabel = (type) => {
    switch (type) {
      case 'APPOINTMENT_CONFIRMATION':
        return { label: 'Xác nhận lịch hẹn', color: 'bg-teal-50 text-teal-700 border-teal-200' };
      case 'APPOINTMENT_CANCELLATION':
        return { label: 'Hủy lịch hẹn', color: 'bg-rose-50 text-rose-700 border-rose-200' };
      case 'APPOINTMENT_RESCHEDULED':
        return { label: 'Đổi lịch hẹn', color: 'bg-sky-50 text-sky-700 border-sky-200' };
      case 'APPOINTMENT_REMINDER':
        return { label: 'Nhắc lịch khám', color: 'bg-amber-50 text-amber-700 border-amber-200' };
      default:
        return { label: type || 'Thông báo', color: 'bg-slate-50 text-slate-700 border-slate-200' };
    }
  };

  const getStatusBadge = (status) => {
    switch (status) {
      case 'SENT':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-bold bg-emerald-100 text-emerald-800">
            <CheckCircle2 className="w-3 h-3 text-emerald-600" /> Đã gửi
          </span>
        );
      case 'FAILED':
      case 'PERMANENT_FAILURE':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-bold bg-rose-100 text-rose-800">
            <XCircle className="w-3 h-3 text-rose-600" /> Thất bại
          </span>
        );
      case 'PROCESSING':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-bold bg-sky-100 text-sky-800 animate-pulse">
            <RefreshCw className="w-3 h-3 text-sky-600 animate-spin" /> Đang gửi
          </span>
        );
      case 'PENDING':
      default:
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-bold bg-amber-100 text-amber-800">
            <Clock className="w-3 h-3 text-amber-600" /> Đang chờ
          </span>
        );
    }
  };

  const formatTime = (isoString) => {
    if (!isoString) return 'Vừa xong';
    try {
      const d = new Date(isoString);
      return d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }) +
        ', ' +
        d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' });
    } catch {
      return isoString;
    }
  };

  return (
    <div className="fixed inset-0 z-50 overflow-hidden" role="dialog" aria-modal="true" aria-labelledby="notification-drawer-title">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-slate-900/60 backdrop-blur-xs transition-opacity"
        onClick={onClose}
      />

      <div className="fixed inset-y-0 right-0 max-w-full flex pl-4 sm:pl-10">
        <div className="w-screen max-w-lg bg-white shadow-2xl flex flex-col border-l border-slate-200">
          {/* Header */}
          <div className="p-5 border-b border-teal-800 bg-gradient-to-r from-teal-800 via-teal-900 to-slate-900 text-white">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="p-2.5 bg-white/10 rounded-2xl backdrop-blur-md ring-1 ring-white/20">
                  <Bell className="w-5 h-5 text-teal-300" />
                </div>
                <div>
                  <h2 id="notification-drawer-title" className="font-extrabold text-base tracking-tight">Trung tâm Thông báo</h2>
                  <p className="text-xs text-teal-200">Email & Tin nhắn hệ thống (MailHog / SMTP)</p>
                </div>
              </div>

              <div className="flex items-center gap-2">
                <button
                  onClick={fetchNotifications}
                  disabled={loading}
                  className="p-2 text-white/80 hover:text-white hover:bg-white/10 rounded-xl transition-colors"
                  title="Làm mới danh sách"
                >
                  <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
                </button>
                <button
                  onClick={onClose}
                  className="p-2 text-white/80 hover:text-white hover:bg-white/10 rounded-xl transition-colors"
                  title="Đóng"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>
            </div>

            {/* Quick Stat Badges */}
            <div className="mt-4 grid grid-cols-3 gap-2 pt-3 border-t border-white/10 text-center">
              <div className="bg-white/5 rounded-xl p-2 border border-white/10">
                <div className="text-xs text-teal-200 font-medium">Tổng số</div>
                <div className="text-lg font-black text-white">{totalElements || notifications.length}</div>
              </div>
              <div className="bg-white/5 rounded-xl p-2 border border-white/10">
                <div className="text-xs text-emerald-300 font-medium">Đã gửi</div>
                <div className="text-lg font-black text-emerald-400">{sentCount}</div>
              </div>
              <div className="bg-white/5 rounded-xl p-2 border border-white/10">
                <div className="text-xs text-rose-300 font-medium">Lỗi gửi</div>
                <div className="text-lg font-black text-rose-400">{failedCount}</div>
              </div>
            </div>
          </div>

          {/* Filter Bar */}
          <div className="p-3 bg-slate-50 border-b border-slate-200/80 space-y-2">
            {/* Status Tabs */}
            <div className="flex items-center gap-1 overflow-x-auto text-xs pb-1">
              {[
                { id: 'ALL', label: 'Tất cả' },
                { id: 'SENT', label: 'Đã gửi' },
                { id: 'FAILED', label: 'Lỗi gửi' },
                { id: 'PENDING', label: 'Đang chờ' },
              ].map((tab) => (
                <button
                  key={tab.id}
                  onClick={() => setStatusFilter(tab.id)}
                  className={`px-3 py-1.5 rounded-lg font-bold shrink-0 transition-all ${
                    statusFilter === tab.id
                      ? 'bg-teal-700 text-white shadow-xs'
                      : 'bg-white text-slate-600 hover:bg-slate-200/60 border border-slate-200'
                  }`}
                >
                  {tab.label}
                </button>
              ))}
            </div>

            {/* Recipient Filter */}
            <div className="flex items-center justify-between text-xs pt-1">
              <span className="text-slate-400 font-semibold flex items-center gap-1">
                <Filter className="w-3.5 h-3.5" /> Lọc đối tượng:
              </span>
              <div className="flex items-center gap-1.5">
                {[
                  { id: 'ALL', label: 'Tất cả' },
                  { id: 'PATIENT', label: 'Bệnh nhân' },
                  { id: 'DOCTOR', label: 'Bác sĩ' },
                ].map((rf) => (
                  <button
                    key={rf.id}
                    onClick={() => setRecipientFilter(rf.id)}
                    className={`px-2 py-0.5 rounded-md font-semibold text-[11px] transition-colors ${
                      recipientFilter === rf.id
                        ? 'bg-slate-900 text-white'
                        : 'text-slate-600 hover:bg-slate-200/70'
                    }`}
                  >
                    {rf.label}
                  </button>
                ))}
              </div>
            </div>
          </div>

          {/* List */}
          <div className="flex-1 overflow-y-auto p-4 space-y-3 bg-slate-50/50">
            {displayedNotifications.length === 0 ? (
              <div className="text-center py-16 text-slate-400">
                <div className="w-14 h-14 rounded-2xl bg-slate-100 flex items-center justify-center mx-auto mb-3 text-slate-400">
                  <Mail className="w-7 h-7" />
                </div>
                <p className="text-sm font-bold text-slate-700">Chưa có thông báo nào phù hợp</p>
                <p className="text-xs text-slate-400 mt-1 max-w-xs mx-auto">
                  Khi người dùng đặt hoặc hủy lịch hẹn, các thông báo email và SMS sẽ hiển thị trực tiếp tại đây.
                </p>
              </div>
            ) : (
              displayedNotifications.map((item) => {
                const typeInfo = getTypeLabel(item.type);
                const isFailed = item.status === 'FAILED' || item.status === 'PERMANENT_FAILURE';
                const isRetrying = retryingId === item.id;

                return (
                  <div
                    key={item.id}
                    className={`p-4 rounded-2xl border transition-all shadow-xs ${
                      isFailed
                        ? 'bg-rose-50/40 border-rose-200 hover:border-rose-300'
                        : 'bg-white border-slate-200/80 hover:border-teal-300'
                    }`}
                  >
                    {/* Top Row: Type & Status & Time */}
                    <div className="flex items-center justify-between gap-2 mb-2">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className={`px-2 py-0.5 rounded-md text-[10px] font-bold border ${typeInfo.color}`}>
                          {typeInfo.label}
                        </span>
                        <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-slate-500">
                          {item.channel === 'EMAIL' ? (
                            <Mail className="w-3.5 h-3.5 text-teal-600" />
                          ) : (
                            <Smartphone className="w-3.5 h-3.5 text-sky-600" />
                          )}
                          {item.channel || 'EMAIL'}
                        </span>
                      </div>
                      <div className="flex items-center gap-2">
                        {getStatusBadge(item.status)}
                      </div>
                    </div>

                    {/* Subject */}
                    <h3 className="text-xs font-bold text-slate-900 mb-1 leading-snug">
                      {item.subject || 'Thông báo từ phòng khám FClinic'}
                    </h3>

                    {/* Recipient Details */}
                    <div className="mt-2.5 pt-2 border-t border-slate-100 flex items-center justify-between text-[11px] text-slate-600">
                      <div className="flex items-center gap-1.5 truncate max-w-[240px]">
                        {item.recipientType === 'DOCTOR' ? (
                          <Stethoscope className="w-3.5 h-3.5 text-teal-600 shrink-0" />
                        ) : (
                          <User className="w-3.5 h-3.5 text-sky-600 shrink-0" />
                        )}
                        <span className="font-semibold text-slate-800 truncate">{item.recipientName}</span>
                        <span className="text-slate-400 truncate">({item.recipientEmail})</span>
                      </div>

                      <span className="text-[10px] text-slate-400 shrink-0 flex items-center gap-1">
                        <Clock className="w-3 h-3" />
                        {formatTime(item.sentAt || item.createdAt)}
                      </span>
                    </div>

                    {/* Bottom Metadata: Appointment ID & Retry Action */}
                    <div className="mt-2 pt-2 border-t border-slate-100 flex items-center justify-between text-[11px]">
                      <span className="text-slate-400 font-mono">
                        Cuộc hẹn: <strong className="text-teal-700 font-bold">#{item.appointmentId}</strong>
                      </span>

                      {/* Retry Button if Failed */}
                      {isFailed && (
                        <button
                          onClick={() => handleRetry(item)}
                          disabled={isRetrying}
                          className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-rose-600 hover:bg-rose-700 text-white text-[11px] font-bold transition-colors shadow-xs"
                          title="Gửi lại thông báo"
                        >
                          <RotateCcw className={`w-3 h-3 ${isRetrying ? 'animate-spin' : ''}`} />
                          {isRetrying ? 'Đang gửi...' : 'Thử lại ngay'}
                        </button>
                      )}
                    </div>

                    {/* Error Banner if Failed */}
                    {isFailed && item.lastErrorMessage && (
                      <div className="mt-2 p-2 rounded-xl bg-rose-100/70 border border-rose-200 text-rose-800 text-[11px] flex items-start gap-1.5">
                        <AlertTriangle className="w-3.5 h-3.5 text-rose-600 shrink-0 mt-0.5" />
                        <div className="leading-tight">
                          <strong>Lỗi:</strong> {item.lastErrorMessage}
                          {item.attemptCount > 0 && <span className="ml-1 text-[10px] text-rose-600">(Đã thử {item.attemptCount} lần)</span>}
                        </div>
                      </div>
                    )}
                  </div>
                );
              })
            )}
          </div>

          {/* Footer Info */}
          <div className="p-3.5 border-t border-slate-200/80 bg-white text-center">
            <p className="text-[11px] text-slate-500 flex items-center justify-center gap-1.5">
              <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
              Đồng bộ 2 chiều qua RabbitMQ Topic Exchange <code>clinic.events</code> & MailHog SMTP
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};
