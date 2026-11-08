import React, { useState, useEffect } from 'react';
import {
  Stethoscope,
  CalendarDays,
  Clock,
  User,
  CheckCircle2,
  AlertCircle,
  FileText,
  CalendarCheck2
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { appointmentApi } from '../services/api';
import { DoctorSchedulePage } from './DoctorSchedulePage';

export function DoctorDashboardPage() {
  const { user } = useAuth();
  const [activeSubTab, setActiveSubTab] = useState('appointments'); // 'appointments' | 'schedule'
  const [appointments, setAppointments] = useState([]);
  const [isLoadingAppts, setIsLoadingAppts] = useState(true);
  const [toastMsg, setToastMsg] = useState('');

  const fetchDoctorAppointments = async () => {
    setIsLoadingAppts(true);
    try {
      const allAppts = await appointmentApi.getAllAppointments();
      // Filter for this doctor (by doctorName matching or doctorId)
      const filtered = Array.isArray(allAppts)
        ? allAppts.filter((a) => {
            if (user?.doctorId && String(a.doctorId) === String(user.doctorId)) return true;
            if (user?.fullName && a.doctorName && a.doctorName.toLowerCase().includes(user.fullName.toLowerCase())) return true;
            return false;
          })
        : [];
      setAppointments(filtered);
    } catch {
      setAppointments([]);
    } finally {
      setIsLoadingAppts(false);
    }
  };

  useEffect(() => {
    if (activeSubTab === 'appointments') {
      fetchDoctorAppointments();
    }
  }, [activeSubTab, user]);

  return (
    <div className="module-page-container">
      {/* Doctor Header Banner */}
      <div className="doctor-banner-card">
        <div className="doctor-avatar-circle">
          <Stethoscope size={32} color="#0d9488" />
        </div>
        <div className="doctor-info-text">
          <h2>{user?.fullName || 'Bác sĩ chuyên khoa'}</h2>
          <p className="doctor-role-title">Tài khoản Bác sĩ phòng khám FClinic</p>
          <div className="doctor-meta-chips">
            <span className="chip"><User size={13} /> Username: <strong>{user?.username}</strong></span>
            {user?.doctorId && (
              <span className="chip"><FileText size={13} /> Doctor UUID: <code>{user.doctorId.substring(0, 8)}...</code></span>
            )}
            <span className="chip"><Clock size={13} /> Ca khám: Thứ 2 - Thứ 7</span>
          </div>
        </div>
      </div>

      {toastMsg && (
        <div className="alert-box success mb-4">
          <CheckCircle2 size={16} />
          <span>{toastMsg}</span>
        </div>
      )}

      {/* Sub Tabs */}
      <div className="doctor-subtabs">
        <button
          type="button"
          className={`subtab-btn ${activeSubTab === 'appointments' ? 'active' : ''}`}
          onClick={() => setActiveSubTab('appointments')}
        >
          <CalendarCheck2 size={16} />
          Danh sách Bệnh nhân đặt lịch khám ({appointments.length})
        </button>
        <button
          type="button"
          className={`subtab-btn ${activeSubTab === 'schedule' ? 'active' : ''}`}
          onClick={() => setActiveSubTab('schedule')}
        >
          <CalendarDays size={16} />
          Quản lý Lịch trực & Khung giờ khám
        </button>
      </div>

      {/* Content */}
      {activeSubTab === 'appointments' ? (
        <div className="card-box mt-4">
          <div className="card-box-header">
            <CalendarCheck2 size={20} />
            <h3>Danh sách lịch khám của Bác sĩ</h3>
          </div>
          <p className="card-desc">Bệnh nhân đã đăng ký hẹn khám với bác sĩ qua hệ thống:</p>

          <div className="table-responsive-wrapper mt-4">
            <table className="fclinic-table">
              <thead>
                <tr>
                  <th>Mã đặt lịch</th>
                  <th>Bệnh nhân</th>
                  <th>Số điện thoại</th>
                  <th>Ngày khám</th>
                  <th>Giờ khám</th>
                  <th>Lý do khám</th>
                  <th>Trạng thái</th>
                </tr>
              </thead>
              <tbody>
                {isLoadingAppts ? (
                  <tr>
                    <td colSpan={7} className="text-center py-6 text-slate-500">
                      Đang tải danh sách lịch khám...
                    </td>
                  </tr>
                ) : appointments.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="text-center py-6 text-slate-500">
                      Chưa có bệnh nhân nào đặt lịch hẹn với bác sĩ trong giai đoạn này.
                    </td>
                  </tr>
                ) : (
                  appointments.map((a) => (
                    <tr key={a.id}>
                      <td><strong>{a.bookingCode}</strong></td>
                      <td>{a.patientName}</td>
                      <td>{a.patientPhone || '-'}</td>
                      <td>{a.appointmentDate}</td>
                      <td>{a.startTime} - {a.endTime}</td>
                      <td>{a.reasonForVisit || 'Khám tổng quát'}</td>
                      <td>
                        <span className={`status-badge ${a.status === 'CONFIRMED' ? 'active' : a.status === 'CANCELLED' ? 'locked' : 'pending'}`}>
                          {a.status === 'CONFIRMED' ? 'Đã xác nhận' : a.status === 'CANCELLED' ? 'Đã hủy' : 'Chờ xác nhận'}
                        </span>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      ) : (
        <div className="mt-4">
          <DoctorSchedulePage
            onNotify={(msg) => setToastMsg(typeof msg === 'string' ? msg : 'Cập nhật thành công')}
          />
        </div>
      )}
    </div>
  );
}

