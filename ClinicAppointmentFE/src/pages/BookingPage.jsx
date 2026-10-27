import React, { useState, useEffect } from 'react';
import {
  Calendar,
  Clock,
  User,
  Phone,
  Mail,
  FileText,
  CheckCircle2,
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  ShieldAlert,
  Sparkles,
  Printer,
  ChevronRight,
  ShieldCheck,
} from 'lucide-react';
import { appointmentApi, doctorApi, patientApi } from '../services/api';

export const BookingPage = ({
  selectedDoctor,
  selectedSlot,
  onBookingSuccess,
  onBackToSchedule,
  onViewHistory,
}) => {
  const [step, setStep] = useState(selectedDoctor && selectedSlot ? 2 : 1);
  const [doctors, setDoctors] = useState([]);
  const [doctor, setDoctor] = useState(selectedDoctor || null);
  const [slot, setSlot] = useState(selectedSlot || null);
  const [availableSlots, setAvailableSlots] = useState([]);
  const [appointmentDate, setAppointmentDate] = useState(
    selectedSlot?.slotDate || new Date().toISOString().split('T')[0]
  );

  // Patient form
  const [patientForm, setPatientForm] = useState({
    fullName: '',
    phoneNumber: '',
    email: '',
    dateOfBirth: '1995-05-15',
    gender: 'Nam',
    address: '',
    reasonForVisit: '',
  });

  const [loading, setLoading] = useState(false);
  const [bookingResult, setBookingResult] = useState(null);
  const [errorMsg, setErrorMsg] = useState('');

  // Fetch doctors list for step 1 if not selected
  useEffect(() => {
    if (!selectedDoctor) {
      doctorApi.getDoctors().then((docs) => {
        setDoctors(docs || []);
        if (docs && docs.length > 0 && !doctor) {
          setDoctor(docs[0]);
        }
      });
    }
  }, [selectedDoctor]);

  // Fetch slots when doctor or date changes
  useEffect(() => {
    if (doctor?.id) {
      doctorApi.getDoctorSlots(doctor.id, appointmentDate).then((slots) => {
        setAvailableSlots(slots || []);
        if (!slot || slot.doctorId !== doctor.id) {
          const firstOpen = (slots || []).find((s) => !s.isBooked);
          if (firstOpen) setSlot(firstOpen);
        }
      });
    }
  }, [doctor, appointmentDate]);

  // Auto-fill patient info if phone number exists in patient DB
  const handlePhoneBlur = async () => {
    if (patientForm.phoneNumber && patientForm.phoneNumber.length >= 9) {
      try {
        const found = await patientApi.getPatientByPhone(patientForm.phoneNumber);
        if (found) {
          setPatientForm((prev) => ({
            ...prev,
            fullName: found.fullName || prev.fullName,
            email: found.email || prev.email,
            dateOfBirth: found.dateOfBirth || prev.dateOfBirth,
            gender: found.gender || prev.gender,
            address: found.address || prev.address,
          }));
        }
      } catch {
        // ignore
      }
    }
  };

  const handleSubmitBooking = async (e) => {
    e.preventDefault();
    if (!doctor || !slot) {
      setErrorMsg('Vui lòng chọn bác sĩ và khung giờ khám.');
      return;
    }
    if (!patientForm.fullName || !patientForm.phoneNumber) {
      setErrorMsg('Vui lòng điền họ tên và số điện thoại của bệnh nhân.');
      return;
    }

    setLoading(true);
    setErrorMsg('');

    try {
      // 1. Register or update patient
      const patientData = await patientApi.createOrUpdatePatient({
        fullName: patientForm.fullName,
        phoneNumber: patientForm.phoneNumber,
        email: patientForm.email,
        dateOfBirth: patientForm.dateOfBirth,
        gender: patientForm.gender,
        address: patientForm.address,
      });

      const patientId = patientData?.id || 1;

      // 2. Book appointment via Clean Architecture Use Case with Distributed Lock & Outbox
      const requestPayload = {
        patientId,
        patientName: patientForm.fullName,
        patientPhone: patientForm.phoneNumber,
        doctorId: doctor.id,
        doctorName: doctor.name,
        doctorSpecialty: doctor.specialty,
        slotId: slot.id,
        appointmentDate: appointmentDate,
        startTime: slot.startTime,
        endTime: slot.endTime,
        reasonForVisit: patientForm.reasonForVisit || 'Khám và tư vấn sức khỏe tổng quát',
        consultationFee: doctor.consultationFee,
      };

      const result = await appointmentApi.bookAppointment(requestPayload);
      setBookingResult(result);
      if (onBookingSuccess) {
        onBookingSuccess(result);
      }
    } catch (err) {
      setErrorMsg(err.message || 'Xảy ra lỗi trong quá trình đặt lịch.');
    } finally {
      setLoading(false);
    }
  };

  // If booking succeeded, show confirmation screen
  if (bookingResult) {
    return (
      <div className="bg-white rounded-3xl border border-slate-200/80 p-6 sm:p-10 shadow-sm max-w-3xl mx-auto space-y-6">
        <div className="text-center space-y-3">
          <div className="w-16 h-16 rounded-full bg-emerald-100 text-emerald-600 flex items-center justify-center mx-auto shadow-md shadow-emerald-500/10">
            <CheckCircle2 className="w-10 h-10" />
          </div>
          <span className="inline-block px-3 py-1 rounded-full bg-emerald-50 text-emerald-700 text-xs font-bold uppercase tracking-wider">
            Đặt Lịch Khám Thành Công
          </span>
          <h2 className="text-2xl sm:text-3xl font-extrabold text-slate-900">
            Mã Đặt Hẹn:{' '}
            <span className="text-teal-700 font-mono tracking-wider">
              {bookingResult.bookingCode}
            </span>
          </h2>
          <p className="text-sm text-slate-500 max-w-lg mx-auto">
            Hệ thống đã xác nhận lịch hẹn của bạn. Thông báo xác nhận chi tiết đã được gửi qua SMS/Email.
          </p>
        </div>

        {/* Appointment Card */}
        <div className="p-6 rounded-2xl bg-gradient-to-br from-teal-50/60 to-slate-50 border border-teal-100/80 space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
            <div>
              <span className="text-xs text-slate-400 block font-medium">Bệnh nhân</span>
              <strong className="text-slate-900 text-base">{bookingResult.patientName}</strong>
              <div className="text-xs text-slate-500">{bookingResult.patientPhone}</div>
            </div>

            <div>
              <span className="text-xs text-slate-400 block font-medium">Bác sĩ phụ trách</span>
              <strong className="text-slate-900 text-base">{bookingResult.doctorName}</strong>
              <div className="text-xs text-teal-700 font-semibold">{bookingResult.doctorSpecialty}</div>
            </div>

            <div>
              <span className="text-xs text-slate-400 block font-medium">Thời gian khám</span>
              <strong className="text-slate-900">
                {new Date(bookingResult.appointmentDate).toLocaleDateString('vi-VN')}
              </strong>
              <div className="text-xs text-slate-600 font-mono">
                {bookingResult.startTime?.substring(0, 5)} - {bookingResult.endTime?.substring(0, 5)}
              </div>
            </div>

            <div>
              <span className="text-xs text-slate-400 block font-medium">Chi phí khám dự kiến</span>
              <strong className="text-teal-800 text-base">
                {bookingResult.consultationFee ? bookingResult.consultationFee.toLocaleString('vi-VN') + ' đ' : 'Chưa cập nhật'}
              </strong>
              <div className="text-[11px] text-emerald-600 font-medium">Thanh toán tại quầy lễ tân</div>
            </div>
          </div>

          {bookingResult.reasonForVisit && (
            <div className="pt-3 border-t border-teal-100/60 text-xs text-slate-600">
              <span className="text-slate-400 font-medium">Lý do khám: </span>
              <span>{bookingResult.reasonForVisit}</span>
            </div>
          )}
        </div>

        {/* Patient Instructions */}
        <div className="p-5 rounded-2xl bg-teal-50/70 border border-teal-100 text-xs space-y-2.5 text-teal-950">
          <div className="flex items-center gap-2 font-bold text-teal-800 text-sm">
            <ShieldCheck className="w-4 h-4 text-teal-600" />
            <span>Hướng dẫn dành cho bệnh nhân khi đến khám</span>
          </div>
          <ul className="space-y-1.5 text-slate-600 list-disc list-inside">
            <li>Quý khách vui lòng có mặt tại quầy tiếp đón trước giờ hẹn 10 - 15 phút.</li>
            <li>Mang theo CMND/CCCD hoặc giấy tờ tùy thân và sổ khám bệnh (nếu có).</li>
            <li>Thông tin lịch hẹn và mã đặt chỗ đã được gửi đến số điện thoại và email đăng ký.</li>
          </ul>
        </div>

        {/* Actions */}
        <div className="flex flex-col sm:flex-row gap-3 pt-2">
          <button
            onClick={() => {
              setBookingResult(null);
              setStep(1);
            }}
            className="flex-1 py-3 px-5 rounded-xl border border-slate-200 text-slate-700 font-semibold text-sm hover:bg-slate-50 transition-colors text-center"
          >
            Đặt Lịch Hẹn Khác
          </button>
          <button
            onClick={onViewHistory || onBackToSchedule}
            className="flex-1 py-3 px-5 rounded-xl bg-teal-700 text-white font-semibold text-sm hover:bg-teal-800 transition-colors shadow-md shadow-teal-700/20 text-center"
          >
            Xem Lịch Sử Cuộc Hẹn
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Back button */}
      {onBackToSchedule && (
        <button
          onClick={onBackToSchedule}
          className="inline-flex items-center gap-2 text-xs font-semibold text-slate-600 hover:text-teal-700 transition-colors"
        >
          <ArrowLeft className="w-4 h-4" /> Quay lại danh sách lịch trực bác sĩ
        </button>
      )}

      {/* Stepper Header */}
      <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs">
        <div className="flex items-center justify-between max-w-xl mx-auto">
          <div className="flex items-center gap-3">
            <div
              className={`w-9 h-9 rounded-xl flex items-center justify-center font-bold text-sm ${
                step >= 1 ? 'bg-teal-700 text-white shadow-sm' : 'bg-slate-100 text-slate-400'
              }`}
            >
              1
            </div>
            <div>
              <div className="text-xs font-bold text-slate-800">Bước 1</div>
              <div className="text-xs text-slate-400">Chọn Bác sĩ & Giờ khám</div>
            </div>
          </div>

          <ChevronRight className="w-5 h-5 text-slate-300" />

          <div className="flex items-center gap-3">
            <div
              className={`w-9 h-9 rounded-xl flex items-center justify-center font-bold text-sm ${
                step >= 2 ? 'bg-teal-700 text-white shadow-sm' : 'bg-slate-100 text-slate-400'
              }`}
            >
              2
            </div>
            <div>
              <div className="text-xs font-bold text-slate-800">Bước 2</div>
              <div className="text-xs text-slate-400">Thông tin bệnh nhân</div>
            </div>
          </div>
        </div>
      </div>

      {errorMsg && (
        <div className="p-4 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-sm flex items-start gap-3">
          <ShieldAlert className="w-5 h-5 text-rose-600 shrink-0 mt-0.5" />
          <div>
            <strong className="block font-bold">Lỗi đặt khám</strong>
            <span>{errorMsg}</span>
          </div>
        </div>
      )}

      {/* Step 1: Doctor & Slot Selection */}
      {step === 1 && (
        <div className="bg-white rounded-2xl border border-slate-200/80 p-6 shadow-xs space-y-6">
          <h2 className="text-lg font-bold text-slate-900">1. Chọn Bác sĩ & Khung giờ khám</h2>

          {/* Select Doctor */}
          <div className="space-y-3">
            <label className="text-xs font-bold text-slate-700 uppercase tracking-wider block">
              Bác sĩ chuyên khoa
            </label>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {doctors.map((d) => (
                <button
                  key={d.id}
                  type="button"
                  onClick={() => setDoctor(d)}
                  className={`p-3.5 rounded-xl border text-left flex items-center gap-3 transition-all ${
                    doctor?.id === d.id
                      ? 'border-teal-600 bg-teal-50/60 ring-2 ring-teal-500/20'
                      : 'border-slate-200 hover:bg-slate-50'
                  }`}
                >
                  <img src={d.avatarUrl} alt={d.name} className="w-12 h-12 rounded-xl object-cover" />
                  <div className="min-w-0">
                    <div className="font-bold text-sm text-slate-900 truncate">{d.name}</div>
                    <div className="text-xs text-teal-700 font-medium">{d.specialty}</div>
                    <div className="text-[11px] text-slate-400 mt-0.5">
                      {d.consultationFee ? d.consultationFee.toLocaleString('vi-VN') + ' đ' : ''}
                    </div>
                  </div>
                </button>
              ))}
            </div>
          </div>

          {/* Date Picker */}
          <div className="space-y-2">
            <label className="text-xs font-bold text-slate-700 uppercase tracking-wider block">
              Ngày khám mong muốn
            </label>
            <input
              type="date"
              value={appointmentDate}
              min={new Date().toISOString().split('T')[0]}
              onChange={(e) => setAppointmentDate(e.target.value)}
              className="px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm font-medium text-slate-800 focus:outline-none focus:ring-2 focus:ring-teal-500"
            />
          </div>

          {/* Slots */}
          <div className="space-y-3">
            <label className="text-xs font-bold text-slate-700 uppercase tracking-wider block">
              Khung giờ có sẵn
            </label>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
              {availableSlots.length === 0 ? (
                <div className="col-span-full py-4 text-center text-xs text-slate-400 bg-slate-50 rounded-xl">
                  Chưa có khung giờ trực cho ngày này
                </div>
              ) : (
                availableSlots.map((s) => {
                  const isSelected = slot?.id === s.id;
                  const isBooked = s.isBooked;

                  return (
                    <button
                      key={s.id}
                      type="button"
                      disabled={isBooked}
                      onClick={() => setSlot(s)}
                      className={`p-3 rounded-xl border text-xs font-semibold flex flex-col items-center gap-1 transition-all ${
                        isBooked
                          ? 'bg-slate-100 text-slate-400 border-slate-200 line-through cursor-not-allowed'
                          : isSelected
                          ? 'bg-teal-700 text-white border-teal-700 shadow-md shadow-teal-700/20'
                          : 'bg-slate-50 hover:bg-teal-50 hover:text-teal-800 border-slate-200 text-slate-700'
                      }`}
                    >
                      <span>
                        {s.startTime?.substring(0, 5)} - {s.endTime?.substring(0, 5)}
                      </span>
                      <span className="text-[10px] font-normal opacity-80">
                        {isBooked ? 'Đã kín' : isSelected ? 'Đang chọn' : 'Còn trống'}
                      </span>
                    </button>
                  );
                })
              )}
            </div>
          </div>

          <div className="pt-4 flex justify-end">
            <button
              type="button"
              disabled={!doctor || !slot}
              onClick={() => setStep(2)}
              className="inline-flex items-center gap-2 px-6 py-2.5 rounded-xl bg-teal-700 text-white font-semibold text-sm hover:bg-teal-800 disabled:opacity-50 transition-colors shadow-md shadow-teal-700/20"
            >
              Tiếp tục: Thông tin bệnh nhân <ArrowRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}

      {/* Step 2: Patient Form & Lock Confirmation */}
      {step === 2 && (
        <form onSubmit={handleSubmitBooking} className="space-y-6">
          <div className="bg-white rounded-2xl border border-slate-200/80 p-6 shadow-xs space-y-5">
            {/* Selected summary strip */}
            <div className="p-4 rounded-xl bg-teal-50/70 border border-teal-100 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs">
              <div className="flex items-center gap-3">
                <img src={doctor?.avatarUrl} alt={doctor?.name} className="w-10 h-10 rounded-xl object-cover" />
                <div>
                  <div className="font-bold text-slate-900 text-sm">{doctor?.name}</div>
                  <div className="text-teal-700 font-semibold">{doctor?.specialty} • {doctor?.department}</div>
                </div>
              </div>

              <div className="text-slate-600 sm:text-right">
                <div className="font-semibold text-slate-900">
                  {new Date(appointmentDate).toLocaleDateString('vi-VN')} ({slot?.startTime?.substring(0, 5)} - {slot?.endTime?.substring(0, 5)})
                </div>
                <button
                  type="button"
                  onClick={() => setStep(1)}
                  className="text-teal-700 underline font-medium hover:text-teal-900 mt-0.5 inline-block"
                >
                  Thay đổi bác sĩ / giờ
                </button>
              </div>
            </div>

            <h2 className="text-lg font-bold text-slate-900">2. Thông tin bệnh nhân</h2>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="text-xs font-bold text-slate-700 block mb-1">
                  Họ và tên bệnh nhân <span className="text-rose-500">*</span>
                </label>
                <div className="relative">
                  <User className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
                  <input
                    type="text"
                    required
                    placeholder="Nguyễn Văn A"
                    value={patientForm.fullName}
                    onChange={(e) => setPatientForm({ ...patientForm, fullName: e.target.value })}
                    className="w-full pl-9 pr-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:bg-white focus:ring-2 focus:ring-teal-500 focus:outline-none"
                  />
                </div>
              </div>

              <div>
                <label className="text-xs font-bold text-slate-700 block mb-1">
                  Số điện thoại liên hệ <span className="text-rose-500">*</span>
                </label>
                <div className="relative">
                  <Phone className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
                  <input
                    type="tel"
                    required
                    placeholder="0912345678"
                    value={patientForm.phoneNumber}
                    onBlur={handlePhoneBlur}
                    onChange={(e) => setPatientForm({ ...patientForm, phoneNumber: e.target.value })}
                    className="w-full pl-9 pr-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:bg-white focus:ring-2 focus:ring-teal-500 focus:outline-none"
                  />
                </div>
                <span className="text-[11px] text-slate-400 mt-1 block">
                  Nhập số điện thoại để tự động điền hồ sơ nếu đã từng khám tại FClinic.
                </span>
              </div>

              <div>
                <label className="text-xs font-bold text-slate-700 block mb-1">Email</label>
                <div className="relative">
                  <Mail className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
                  <input
                    type="email"
                    placeholder="email@example.com"
                    value={patientForm.email}
                    onChange={(e) => setPatientForm({ ...patientForm, email: e.target.value })}
                    className="w-full pl-9 pr-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:bg-white focus:ring-2 focus:ring-teal-500 focus:outline-none"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">Giới tính</label>
                  <select
                    value={patientForm.gender}
                    onChange={(e) => setPatientForm({ ...patientForm, gender: e.target.value })}
                    className="w-full px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:bg-white focus:ring-2 focus:ring-teal-500 focus:outline-none"
                  >
                    <option value="Nam">Nam</option>
                    <option value="Nữ">Nữ</option>
                    <option value="Khác">Khác</option>
                  </select>
                </div>

                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">Ngày sinh</label>
                  <input
                    type="date"
                    value={patientForm.dateOfBirth}
                    onChange={(e) => setPatientForm({ ...patientForm, dateOfBirth: e.target.value })}
                    className="w-full px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:bg-white focus:ring-2 focus:ring-teal-500 focus:outline-none"
                  />
                </div>
              </div>

              <div className="sm:col-span-2">
                <label className="text-xs font-bold text-slate-700 block mb-1">Địa chỉ thường trú</label>
                <input
                  type="text"
                  placeholder="Số nhà, đường, phường/xã, quận/huyện..."
                  value={patientForm.address}
                  onChange={(e) => setPatientForm({ ...patientForm, address: e.target.value })}
                  className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:bg-white focus:ring-2 focus:ring-teal-500 focus:outline-none"
                />
              </div>

              <div className="sm:col-span-2">
                <label className="text-xs font-bold text-slate-700 block mb-1">
                  Triệu chứng / Lý do khám
                </label>
                <textarea
                  rows={3}
                  placeholder="Mô tả cụ thể triệu chứng khó chịu, thời gian khởi phát..."
                  value={patientForm.reasonForVisit}
                  onChange={(e) => setPatientForm({ ...patientForm, reasonForVisit: e.target.value })}
                  className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:bg-white focus:ring-2 focus:ring-teal-500 focus:outline-none"
                />
              </div>
            </div>

            {/* Privacy & Guarantee notice */}
            <div className="p-3.5 rounded-xl bg-teal-50/70 border border-teal-100 flex items-center gap-3 text-xs text-teal-800">
              <ShieldCheck className="w-5 h-5 text-teal-600 shrink-0" />
              <span>
                Thông tin đặt khám của bạn được bảo mật tuyệt đối. Khung giờ khám được giữ chỗ ngay khi bạn xác nhận.
              </span>
            </div>

            {/* Action buttons */}
            <div className="pt-2 flex items-center justify-between">
              <button
                type="button"
                onClick={() => setStep(1)}
                className="px-5 py-2.5 rounded-xl border border-slate-200 text-slate-600 font-semibold text-sm hover:bg-slate-50 transition-colors"
              >
                Quay lại bước 1
              </button>

              <button
                type="submit"
                disabled={loading}
                className="inline-flex items-center gap-2 px-8 py-3 rounded-xl bg-gradient-to-r from-teal-700 to-teal-800 text-white font-bold text-sm hover:from-teal-800 hover:to-teal-900 disabled:opacity-50 transition-all shadow-lg shadow-teal-700/25"
              >
                {loading ? (
                  <>
                    <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
                    Đang xử lý đặt lịch khám...
                  </>
                ) : (
                  <>
                    Xác Nhận Đặt Lịch Khám <ArrowRight className="w-4 h-4" />
                  </>
                )}
              </button>
            </div>
          </div>
        </form>
      )}
    </div>
  );
};

export default BookingPage;
