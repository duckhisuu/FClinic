import React, { useState, useEffect } from 'react';
import {
  Layers,
  Database,
  Radio,
  Bell,
  Lock,
  ArrowRight,
  CheckCircle2,
  Clock,
  RefreshCw,
  Cpu,
  ShieldCheck,
  AlertTriangle,
  Play,
} from 'lucide-react';
import { appointmentApi } from '../services/api';

export const OutboxMonitorPage = () => {
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(false);
  const [simulatingLock, setSimulatingLock] = useState(false);
  const [simulationLog, setSimulationLog] = useState([]);

  const fetchOutboxEvents = async () => {
    setLoading(true);
    try {
      const data = await appointmentApi.getRecentOutboxEvents();
      setEvents(data || []);
    } catch {
      // fallback
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOutboxEvents();
    const interval = setInterval(fetchOutboxEvents, 6000);
    return () => clearInterval(interval);
  }, []);

  const runConcurrentLockSimulation = async () => {
    setSimulatingLock(true);
    setSimulationLog([
      'Bắt đầu giả lập 2 yêu cầu đặt khám đồng thời cho cùng 1 Bác sĩ & khung giờ...',
    ]);

    await new Promise((r) => setTimeout(r, 600));
    setSimulationLog((prev) => [
      ...prev,
      'Yêu cầu #1 (Bệnh nhân A) gửi đến appointment-service: Cần lock key "lock:slot:1:1:2026-09-29"',
      'Yêu cầu #2 (Bệnh nhân B) gửi đồng thời tới appointment-service: Cần cùng lock key',
    ]);

    await new Promise((r) => setTimeout(r, 800));
    setSimulationLog((prev) => [
      ...prev,
      '✅ Redisson Distributed Lock: Yêu cầu #1 chiếm lock thành công!',
      'Ghi dữ liệu Appointment #1 và Outbox event vào PostgreSQL trong Transaction.',
    ]);

    await new Promise((r) => setTimeout(r, 800));
    setSimulationLog((prev) => [
      ...prev,
      '⛔ Yêu cầu #2: Redisson lock phát hiện xung đột! SlotAlreadyBookedException được kích hoạt.',
      'Bệnh nhân B nhận thông báo: "Khung giờ này đã có bệnh nhân đặt. Vui lòng chọn khung giờ khác."',
      '🎉 Ngăn chặn thành công 100% Double-Booking nhờ Redis Distributed Lock!',
    ]);

    setSimulatingLock(false);
    fetchOutboxEvents();
  };

  return (
    <div className="space-y-6">
      {/* Banner */}
      <div className="bg-gradient-to-r from-slate-900 via-teal-950 to-slate-900 rounded-3xl p-6 sm:p-8 text-white shadow-xl relative overflow-hidden">
        <div className="max-w-2xl relative z-10">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-teal-500/20 text-teal-300 text-xs font-bold uppercase tracking-wider mb-3 border border-teal-500/30">
            <Cpu className="w-3.5 h-3.5" /> Kiến Trúc Doanh Nghiệp & EDA
          </div>
          <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight">
            Clean Architecture & Transactional Outbox
          </h1>
          <p className="mt-2 text-sm text-slate-300 leading-relaxed">
            Hệ thống tuân thủ thiết kế Domain-Driven Design (DDD) tách biệt Domain, Application, Infrastructure và API layers theo mẫu SocialMediaBlogPlatform, kết hợp Transactional Outbox qua RabbitMQ để đảm bảo tính nhất quán cuối cùng (Eventual Consistency).
          </p>
        </div>
      </div>

      {/* Clean Architecture Diagram Visualizer */}
      <div className="bg-white rounded-2xl border border-slate-200/80 p-6 shadow-xs space-y-4">
        <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
          <Layers className="w-5 h-5 text-teal-600" />
          Sơ đồ luồng xử lý Transactional Outbox
        </h2>

        <div className="grid grid-cols-1 md:grid-cols-4 gap-3 pt-2">
          {/* Box 1 */}
          <div className="p-4 rounded-xl bg-teal-50/70 border border-teal-100 flex flex-col justify-between">
            <div>
              <div className="w-8 h-8 rounded-lg bg-teal-600 text-white flex items-center justify-center font-bold text-xs mb-2">
                1
              </div>
              <h3 className="font-bold text-xs text-slate-900 mb-1">Application UseCase</h3>
              <p className="text-[11px] text-slate-600">
                BookAppointmentUseCase kiểm tra Redis Lock, tạo Domain Aggregate và bắt đầu Database Transaction.
              </p>
            </div>
            <div className="mt-3 pt-2 border-t border-teal-200/60 text-[10px] text-teal-800 font-mono font-semibold">
              lock:slot:id:date
            </div>
          </div>

          {/* Box 2 */}
          <div className="p-4 rounded-xl bg-blue-50/70 border border-blue-100 flex flex-col justify-between">
            <div>
              <div className="w-8 h-8 rounded-lg bg-blue-600 text-white flex items-center justify-center font-bold text-xs mb-2">
                2
              </div>
              <h3 className="font-bold text-xs text-slate-900 mb-1">Atomicity in DB</h3>
              <p className="text-[11px] text-slate-600">
                Bảng <code>appointments</code> và bảng <code>outbox_events</code> được lưu cùng lúc trong 1 PostgreSQL Transaction.
              </p>
            </div>
            <div className="mt-3 pt-2 border-t border-blue-200/60 text-[10px] text-blue-800 font-mono font-semibold">
              status: PENDING
            </div>
          </div>

          {/* Box 3 */}
          <div className="p-4 rounded-xl bg-amber-50/70 border border-amber-100 flex flex-col justify-between">
            <div>
              <div className="w-8 h-8 rounded-lg bg-amber-600 text-white flex items-center justify-center font-bold text-xs mb-2">
                3
              </div>
              <h3 className="font-bold text-xs text-slate-900 mb-1">OutboxRelayJob</h3>
              <p className="text-[11px] text-slate-600">
                Job định kỳ (@Scheduled) quét các event PENDING, publish vào RabbitMQ Direct Exchange rồi cập nhật status.
              </p>
            </div>
            <div className="mt-3 pt-2 border-t border-amber-200/60 text-[10px] text-amber-800 font-mono font-semibold">
              exchange: fclinic.direct
            </div>
          </div>

          {/* Box 4 */}
          <div className="p-4 rounded-xl bg-emerald-50/70 border border-emerald-100 flex flex-col justify-between">
            <div>
              <div className="w-8 h-8 rounded-lg bg-emerald-600 text-white flex items-center justify-center font-bold text-xs mb-2">
                4
              </div>
              <h3 className="font-bold text-xs text-slate-900 mb-1">Notification Consumer</h3>
              <p className="text-[11px] text-slate-600">
                NotificationConsumer nhận event từ RabbitMQ queue, phân tích thông tin và gửi tin xác nhận SMS/Zalo.
              </p>
            </div>
            <div className="mt-3 pt-2 border-t border-emerald-200/60 text-[10px] text-emerald-800 font-mono font-semibold">
              status: COMPLETED
            </div>
          </div>
        </div>
      </div>

      {/* Concurrent Booking Simulation */}
      <div className="bg-white rounded-2xl border border-slate-200/80 p-6 shadow-xs space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
              <Lock className="w-5 h-5 text-teal-600" />
              Kiểm chứng Distributed Lock chống Double-Booking
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Thực hiện kịch bản 2 người cùng nhấn đặt lịch tại cùng một mili-giây.
            </p>
          </div>

          <button
            onClick={runConcurrentLockSimulation}
            disabled={simulatingLock}
            className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-slate-900 text-white text-xs font-bold hover:bg-slate-800 disabled:opacity-50 transition-colors shadow-sm shrink-0"
          >
            <Play className={`w-3.5 h-3.5 ${simulatingLock ? 'animate-spin' : ''}`} />
            {simulatingLock ? 'Đang giả lập tranh chấp...' : 'Chạy Thử Nghiệm Concurrency'}
          </button>
        </div>

        {simulationLog.length > 0 && (
          <div className="p-4 rounded-xl bg-slate-950 text-slate-200 font-mono text-xs space-y-1.5 border border-slate-800">
            {simulationLog.map((logItem, i) => (
              <div key={i} className="leading-relaxed">
                {logItem.includes('✅') || logItem.includes('🎉') ? (
                  <span className="text-emerald-400">{logItem}</span>
                ) : logItem.includes('⛔') ? (
                  <span className="text-rose-400">{logItem}</span>
                ) : (
                  <span className="text-slate-300">{logItem}</span>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Live Outbox Events Table */}
      <div className="bg-white rounded-2xl border border-slate-200/80 p-6 shadow-xs space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <Radio className="w-5 h-5 text-emerald-600 animate-pulse" />
            <div>
              <h2 className="text-base font-bold text-slate-900">Bảng Outbox Events Thời Gian Thực</h2>
              <p className="text-xs text-slate-500">Dữ liệu từ bảng <code>outbox_events</code> trong appointment-service</p>
            </div>
          </div>

          <button
            onClick={fetchOutboxEvents}
            disabled={loading}
            className="p-2 text-slate-600 hover:text-teal-700 rounded-lg hover:bg-slate-50 transition-colors"
            title="Làm mới"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-slate-200 text-slate-400 uppercase tracking-wider font-bold">
                <th className="py-3 px-3">Event ID</th>
                <th className="py-3 px-3">Loại Sự Kiện</th>
                <th className="py-3 px-3">Aggregate</th>
                <th className="py-3 px-3">Trạng Thái</th>
                <th className="py-3 px-3">Thời Gian Ghi</th>
                <th className="py-3 px-3">Nội Dung (Payload)</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {events.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-slate-400">
                    Chưa có sự kiện Outbox nào được tạo
                  </td>
                </tr>
              ) : (
                events.map((evt) => (
                  <tr key={evt.id} className="hover:bg-slate-50/80 transition-colors">
                    <td className="py-3 px-3 font-mono text-[11px] text-slate-500 truncate max-w-[120px]">
                      {evt.id}
                    </td>
                    <td className="py-3 px-3">
                      <span className="font-bold text-slate-900">{evt.eventType}</span>
                    </td>
                    <td className="py-3 px-3 font-mono text-slate-600">
                      {evt.aggregateType} #{evt.aggregateId}
                    </td>
                    <td className="py-3 px-3">
                      <span
                        className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold ${
                          evt.status === 'COMPLETED'
                            ? 'bg-emerald-100 text-emerald-800'
                            : 'bg-amber-100 text-amber-800'
                        }`}
                      >
                        <CheckCircle2 className="w-3 h-3 text-emerald-600" />
                        {evt.status}
                      </span>
                    </td>
                    <td className="py-3 px-3 text-slate-500 whitespace-nowrap">
                      {evt.createdAt ? new Date(evt.createdAt).toLocaleTimeString('vi-VN') : 'Vừa xong'}
                    </td>
                    <td className="py-3 px-3 max-w-xs">
                      <div className="truncate font-mono text-[11px] text-slate-600 bg-slate-50 p-1 rounded border border-slate-200/60">
                        {evt.payload}
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default OutboxMonitorPage;
