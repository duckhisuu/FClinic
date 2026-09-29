import React, { useState, useEffect } from 'react';
import {
  Search,
  Calendar,
  Clock,
  User,
  Stethoscope,
  XCircle,
  CheckCircle2,
  AlertTriangle,
  RefreshCw,
  FileCheck,
  AlertCircle,
  HelpCircle,
} from 'lucide-react';
import { appointmentApi } from '../services/api';

export const PatientHistoryPage = ({ onBookNew }) => {
  const [searchPhone, setSearchPhone] = useState('0912345678');
  const [searchCode, setSearchCode] = useState('');
  const [appointments, setAppointments] = useState([]);
  const [loading, setLoading] = useState(false);
  const [cancelModalAppt, setCancelModalAppt] = useState(null);
  const [cancelReason, setCancelReason] = useState('Có việc bận đột xuất');
  const [cancelling, setCancelling] = useState(false);
  const [message, setMessage] = useState('');

  const fetchAppointments = async () => {
    setLoading(true);
    setMessage('');
    try {
      if (searchCode.trim()) {
        const single = await appointmentApi.getAppointmentByCode(searchCode.trim());
        setAppointments(single ? [single] : []);
        if (!single) setMessage('Không tìm thấy cuộc hẹn nào với mã: ' + searchCode);
      } else if (searchPhone.trim()) {
        const list = await appointmentApi.getAppointmentsByPhone(searchPhone.trim());
        setAppointments(list || []);
        if (!list || list.length === 0) {
          setMessage('Không tìm thấy cuộc hẹn nào cho số điện thoại: ' + searchPhone);
        }
      } else {
        const all = await appointmentApi.getAllAppointments();
        setAppointments(all || []);
      }
    } catch (err) {
      setMessage(err.message || 'Lỗi khi tra cứu lịch hẹn.');
      setAppointments([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAppointments();
  }, []);

  const handleConfirmCancel = async () => {
    if (!cancelModalAppt) return;
    setCancelling(true);
    try {
      await appointmentApi.cancelAppointment(cancelModalAppt.id, cancelReason);
      setCancelModalAppt(null);
      await fetchAppointments();
    } catch (err) {
      alert(err.message || 'Không thể hủy lịch hẹn');
    } finally {
      setCancelling(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Search Header */}
      <div className="bg-white rounded-2xl border border-slate-200/80 p-6 shadow-xs space-y-4">
        <div>
          <h1 className="text-xl font-bold text-slate-900">Tra cứu & Quản lý Lịch hẹn</h1>
          <p className="text-xs text-slate-500 mt-1">
            Nhập số điện thoại bệnh nhân hoặc mã đặt hẹn (FC-XXXXXX) để kiểm tra trạng thái và lịch khám.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <div className="relative">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Tra theo SĐT (VD: 0912345678)"
              value={searchPhone}
              onChange={(e) => {
                setSearchPhone(e.target.value);
                setSearchCode('');
              }}
              className="w-full pl-9 pr-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:bg-white focus:ring-2 focus:ring-teal-500 focus:outline-none"
            />
          </div>

          <div className="relative">
            <FileCheck className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Hoặc mã hẹn (VD: FC-DEMO001)"
              value={searchCode}
              onChange={(e) => {
                setSearchCode(e.target.value);
                setSearchPhone('');
              }}
              className="w-full pl-9 pr-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:bg-white focus:ring-2 focus:ring-teal-500 focus:outline-none font-mono"
            />
          </div>

          <div className="flex gap-2">
            <button
              onClick={fetchAppointments}
              disabled={loading}
              className="flex-1 py-2.5 px-4 rounded-xl bg-teal-700 text-white font-semibold text-sm hover:bg-teal-800 disabled:opacity-50 transition-colors shadow-sm flex items-center justify-center gap-2"
            >
              <Search className="w-4 h-4" /> Tra Cứu
            </button>
            <button
              onClick={() => {
                setSearchPhone('');
                setSearchCode('');
                appointmentApi.getAllAppointments().then(setAppointments);
              }}
              className="p-2.5 rounded-xl border border-slate-200 text-slate-600 hover:bg-slate-50 transition-colors"
              title="Xem tất cả"
            >
              <RefreshCw className="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>

      {message && (
        <div className="p-4 rounded-xl bg-amber-50 border border-amber-200 text-amber-800 text-xs flex items-center gap-2">
          <AlertCircle className="w-4 h-4 text-amber-600 shrink-0" />
          <span>{message}</span>
        </div>
      )}

      {/* Appointment Cards */}
      {loading ? (
        <div className="bg-white rounded-2xl border border-slate-200/80 p-12 text-center text-slate-400">
          <RefreshCw className="w-8 h-8 mx-auto animate-spin text-teal-600 mb-3" />
          <p className="text-sm font-medium">Đang tra cứu cơ sở dữ liệu lịch hẹn...</p>
        </div>
      ) : appointments.length === 0 ? (
        <div className="bg-white rounded-2xl border border-slate-200/80 p-12 text-center text-slate-400 space-y-3">
          <Clock className="w-12 h-12 mx-auto stroke-1 text-slate-300" />
          <p className="text-base font-semibold text-slate-700">Chưa tìm thấy cuộc hẹn nào</p>
          <p className="text-xs text-slate-400 max-w-sm mx-auto">
            Quý khách có thể kiểm tra lại số điện thoại hoặc tiến hành đặt lịch khám mới cùng các bác sĩ chuyên khoa FClinic.
          </p>
          {onBookNew && (
            <button
              onClick={onBookNew}
              className="mt-3 inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-teal-700 text-white text-xs font-semibold hover:bg-teal-800 transition-colors shadow-sm"
            >
              Đặt Lịch Khám Ngay
            </button>
          )}
        </div>
      ) : (
        <div className="space-y-4">
          <div className="text-xs font-bold text-slate-500 uppercase tracking-wider px-1">
            Tìm thấy {appointments.length} cuộc hẹn
          </div>

          {appointments.map((appt) => {
            const isConfirmed = appt.status === 'CONFIRMED';
            const isPending = appt.status === 'PENDING';
            const isCancelled = appt.status === 'CANCELLED';

            return (
              <div
                key={appt.id || appt.bookingCode}
                className={`bg-white rounded-2xl border p-5 sm:p-6 shadow-xs transition-all ${
                  isCancelled
                    ? 'border-slate-200 opacity-70 bg-slate-50/50'
                    : 'border-slate-200/90 hover:border-teal-300'
                }`}
              >
                <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-4 border-b border-slate-100 gap-3">
                  <div className="flex items-center gap-3">
                    <span className="px-3 py-1 rounded-xl bg-teal-50 text-teal-800 font-mono font-bold text-sm border border-teal-200">
                      {appt.bookingCode}
                    </span>
                    <span
                      className={`inline-flex items-center gap-1.5 px-3 py-0.5 rounded-full text-xs font-bold ${
                        isConfirmed
                          ? 'bg-emerald-100 text-emerald-800'
                          : isPending
                          ? 'bg-amber-100 text-amber-800'
                          : 'bg-rose-100 text-rose-800'
                      }`}
                    >
                      {isConfirmed && <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />}
                      {isPending && <Clock className="w-3.5 h-3.5 text-amber-600" />}
                      {isCancelled && <XCircle className="w-3.5 h-3.5 text-rose-600" />}
                      {isConfirmed ? 'Đã xác nhận' : isPending ? 'Chờ xác nhận' : 'Đã hủy'}
                    </span>
                  </div>

                  <div className="text-xs text-slate-400">
                    Đặt lúc: {appt.createdAt ? new Date(appt.createdAt).toLocaleString('vi-VN') : 'Mới đây'}
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-4 text-xs">
                  <div>
                    <span className="text-slate-400 block mb-0.5 font-medium">Bác sĩ phụ trách</span>
                    <strong className="text-slate-900 text-sm block">{appt.doctorName}</strong>
                    <span className="text-teal-700 font-semibold">{appt.doctorSpecialty}</span>
                  </div>

                  <div>
                    <span className="text-slate-400 block mb-0.5 font-medium">Thời gian khám</span>
                    <div className="flex items-center gap-1.5 text-slate-900 font-bold text-sm">
                      <Calendar className="w-4 h-4 text-teal-600" />
                      <span>{new Date(appt.appointmentDate).toLocaleDateString('vi-VN')}</span>
                    </div>
                    <div className="text-slate-500 font-mono mt-0.5">
                      {appt.startTime?.substring(0, 5)} - {appt.endTime?.substring(0, 5)}
                    </div>
                  </div>

                  <div>
                    <span className="text-slate-400 block mb-0.5 font-medium">Bệnh nhân</span>
                    <strong className="text-slate-900 text-sm block">{appt.patientName}</strong>
                    <span className="text-slate-500">{appt.patientPhone}</span>
                  </div>
                </div>

                {appt.reasonForVisit && (
                  <div className="mt-4 pt-3 border-t border-slate-100 text-xs text-slate-600 flex items-start gap-1.5">
                    <span className="text-slate-400 font-medium shrink-0">Lý do khám:</span>
                    <span>{appt.reasonForVisit}</span>
                  </div>
                )}

                <div className="mt-4 pt-3 border-t border-slate-100 flex items-center justify-between text-xs">
                  <div>
                    <span className="text-slate-400">Chi phí: </span>
                    <strong className="text-teal-800 font-bold">
                      {appt.consultationFee ? appt.consultationFee.toLocaleString('vi-VN') + ' đ' : 'Liên hệ'}
                    </strong>
                  </div>

                  {!isCancelled && (
                    <button
                      onClick={() => setCancelModalAppt(appt)}
                      className="text-rose-600 hover:text-rose-800 font-semibold transition-colors hover:underline"
                    >
                      Hủy lịch hẹn
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Cancel Confirmation Modal */}
      {cancelModalAppt && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs">
          <div className="bg-white rounded-3xl max-w-md w-full p-6 shadow-2xl border border-slate-200 space-y-4">
            <div className="w-12 h-12 rounded-2xl bg-rose-100 text-rose-600 flex items-center justify-center">
              <AlertTriangle className="w-6 h-6" />
            </div>

            <div>
              <h3 className="text-lg font-bold text-slate-900">Xác nhận hủy lịch khám?</h3>
              <p className="text-xs text-slate-500 mt-1">
                Lịch hẹn <strong className="text-slate-800">{cancelModalAppt.bookingCode}</strong> với{' '}
                <strong>{cancelModalAppt.doctorName}</strong> sẽ được hủy trên hệ thống và gửi thông báo tới phòng khám.
              </p>
            </div>

            <div>
              <label className="text-xs font-bold text-slate-700 block mb-1">Lý do hủy lịch:</label>
              <select
                value={cancelReason}
                onChange={(e) => setCancelReason(e.target.value)}
                className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs focus:outline-none focus:ring-2 focus:ring-teal-500"
              >
                <option value="Có việc bận đột xuất">Có việc bận đột xuất</option>
                <option value="Đổi giờ sang ngày khác">Đổi giờ sang ngày khác</option>
                <option value="Sức khỏe đã ổn định">Sức khỏe đã ổn định</option>
                <option value="Đặt nhầm khung giờ">Đặt nhầm khung giờ</option>
              </select>
            </div>

            <div className="flex gap-3 pt-2">
              <button
                type="button"
                disabled={cancelling}
                onClick={() => setCancelModalAppt(null)}
                className="flex-1 py-2.5 px-4 rounded-xl border border-slate-200 text-slate-600 font-semibold text-xs hover:bg-slate-50"
              >
                Giữ Lại Lịch
              </button>
              <button
                type="button"
                disabled={cancelling}
                onClick={handleConfirmCancel}
                className="flex-1 py-2.5 px-4 rounded-xl bg-rose-600 text-white font-semibold text-xs hover:bg-rose-700 disabled:opacity-50 shadow-md shadow-rose-600/20"
              >
                {cancelling ? 'Đang hủy...' : 'Đồng Ý Hủy'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default PatientHistoryPage;
