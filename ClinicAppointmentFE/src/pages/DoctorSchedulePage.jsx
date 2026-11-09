import React, { useState, useEffect } from 'react';
import {
  Search,
  Calendar,
  Clock,
  MapPin,
  Award,
  CheckCircle2,
  ChevronRight,
  ChevronLeft,
  Stethoscope,
  RefreshCw,
  PlusCircle,
  CalendarRange,
  Info,
  X,
  ShieldCheck,
  Building,
  GraduationCap,
  Sparkles,
  CalendarDays,
  User,
} from 'lucide-react';
import { doctorApi } from '../appointment/api';
import { ErrorNotice } from '../appointment/ui';

const SPECIALTIES = [
  'Tất cả',
  'Tim Mạch',
  'Nhi Khoa',
  'Răng Hàm Mặt',
  'Cơ Xương Khớp',
  'Nội Tổng Quát',
  'Mắt & Nhãn Khoa',
];

const PRESET_AVATARS = [
  'https://images.unsplash.com/photo-1622253692010-333f2da6031d?w=400',
  'https://images.unsplash.com/photo-1594824813586-7a71f00845a7?w=400',
  'https://images.unsplash.com/photo-1537368910025-700350fe46c7?w=400',
  'https://images.unsplash.com/photo-1612349317150-e413f6a5b16d?w=400',
  'https://images.unsplash.com/photo-1559839734-2b71ea197ec2?w=400',
  'https://images.unsplash.com/photo-1622902046580-2b47f47f5471?w=400',
];

export const DoctorSchedulePage = ({ onSelectSlot, onNotify }) => {
  const [doctors, setDoctors] = useState([]);
  const [selectedSpecialty, setSelectedSpecialty] = useState('Tất cả');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedDate, setSelectedDate] = useState(() => new Date().toISOString().split('T')[0]);
  const [doctorSlots, setDoctorSlots] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Modals state
  const [detailDoctor, setDetailDoctor] = useState(null);
  const [scheduleDoctor, setScheduleDoctor] = useState(null);
  const [scheduleData, setScheduleData] = useState(null);
  const [schedulePeriod, setSchedulePeriod] = useState('WEEK');
  const [scheduleAnchorDate, setScheduleAnchorDate] = useState(() => new Date().toISOString().split('T')[0]);
  const [scheduleLoading, setScheduleLoading] = useState(false);



  const [showAddSlotModal, setShowAddSlotModal] = useState(false);
  const [slotTargetDoctor, setSlotTargetDoctor] = useState(null);
  const [addSlotLoading, setAddSlotLoading] = useState(false);
  const [newSlotForm, setNewSlotForm] = useState({
    slotDate: new Date().toISOString().split('T')[0],
    startTime: '08:00',
    endTime: '09:00',
  });

  const notify = (msg, type = 'info') => {
    if (onNotify) {
      onNotify(msg, { type });
    } else {
      console.log(`[${type}] ${msg}`);
    }
  };

  // Load doctors & slots
  const loadDoctors = async () => {
    setError(null);
    setLoading(true);
    try {
      const spec = selectedSpecialty === 'Tất cả' ? undefined : selectedSpecialty;
      const data = await doctorApi.getDoctors(spec);
      setDoctors(data || []);

      const slotsMap = {};
      await Promise.all(
        (data || []).map(async (doc) => {
          try {
            const slots = await doctorApi.getDoctorSlots(doc.id, selectedDate);
            slotsMap[doc.id] = slots || [];
          } catch (error) { setError(error); slotsMap[doc.id] = []; }
        })
      );
      setDoctorSlots(slotsMap);
    } catch (err) {
      setError(err); setDoctors([]); setDoctorSlots({});
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadDoctors();
  }, [selectedSpecialty, selectedDate]);

  // Load schedule for modal
  const loadSchedule = async (doctorId, period, anchorDate) => {
    setScheduleLoading(true);
    try {
      const res = await doctorApi.getDoctorSchedule(doctorId, period, anchorDate);
      setScheduleData(res);
    } catch (err) {
      setError(err);
      notify('Lỗi tải lịch tuần/tháng của bác sĩ.', 'error');
    } finally {
      setScheduleLoading(false);
    }
  };

  const handleOpenScheduleModal = (doc) => {
    setScheduleDoctor(doc);
    setSchedulePeriod('WEEK');
    setScheduleAnchorDate(selectedDate);
    loadSchedule(doc.id, 'WEEK', selectedDate);
  };

  const handlePeriodChange = (period) => {
    setSchedulePeriod(period);
    if (scheduleDoctor) {
      loadSchedule(scheduleDoctor.id, period, scheduleAnchorDate);
    }
  };

  const handleScheduleAnchorShift = (deltaDays) => {
    const current = new Date(scheduleAnchorDate);
    current.setDate(current.getDate() + deltaDays);
    const newDateStr = current.toISOString().split('T')[0];
    setScheduleAnchorDate(newDateStr);
    if (scheduleDoctor) {
      loadSchedule(scheduleDoctor.id, schedulePeriod, newDateStr);
    }
  };


  // Create slot handler
  const handleOpenAddSlotModal = (doc) => {
    setSlotTargetDoctor(doc);
    setNewSlotForm({
      slotDate: selectedDate,
      startTime: '08:00',
      endTime: '09:00',
    });
    setShowAddSlotModal(true);
  };

  const handleCreateSlotSubmit = async (e) => {
    e.preventDefault();
    if (!slotTargetDoctor) return;
    setAddSlotLoading(true);
    try {
      await doctorApi.createDoctorSlot(slotTargetDoctor.id, newSlotForm);
      notify(
        `Thêm ca khám ${newSlotForm.startTime} - ${newSlotForm.endTime} ngày ${newSlotForm.slotDate} cho BS. ${slotTargetDoctor.name} thành công!`,
        'success'
      );
      setShowAddSlotModal(false);
      // Reload slots for this date
      const slots = await doctorApi.getDoctorSlots(slotTargetDoctor.id, selectedDate);
      setDoctorSlots((prev) => ({ ...prev, [slotTargetDoctor.id]: slots || [] }));
    } catch (err) {
      notify(err.message || 'Không thể thêm khung giờ.', 'error');
    } finally {
      setAddSlotLoading(false);
    }
  };

  // Quick date pickers
  const getRelativeDate = (offsetDays) => {
    const d = new Date();
    d.setDate(d.getDate() + offsetDays);
    return d.toISOString().split('T')[0];
  };

  // Filtered doctors
  const filteredDoctors = doctors.filter((doc) => {
    const q = searchQuery.toLowerCase();
    return (
      doc.name?.toLowerCase().includes(q) ||
      doc.department?.toLowerCase().includes(q) ||
      doc.specialty?.toLowerCase().includes(q) ||
      doc.qualification?.toLowerCase().includes(q)
    );
  });

  return (
    <div className="space-y-6">
      {/* Top Banner with Stats & Quick Actions */}
      <div className="bg-gradient-to-r from-teal-800 via-teal-900 to-slate-900 rounded-3xl p-6 sm:p-8 text-white relative overflow-hidden shadow-xl border border-teal-700/40">
        <div className="relative z-10 flex flex-col lg:flex-row lg:items-center justify-between gap-6">
          <div className="max-w-2xl">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-teal-500/20 text-teal-300 text-xs font-bold uppercase tracking-wider mb-3 border border-teal-400/30 backdrop-blur-md">
              <Stethoscope className="w-3.5 h-3.5" /> Phân Hệ Bác Sĩ & Quản Lý Lịch Khám
            </div>
            <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-white">
              Đội Ngũ Bác Sĩ & Lịch Khám Chuyên Khoa
            </h1>
            <p className="mt-2 text-sm sm:text-base text-teal-100/90 leading-relaxed">
              Tra cứu thông tin bác sĩ đầu ngành, theo dõi khung giờ khám bệnh trực tuyến và đăng ký lịch hẹn nhanh chóng, chính xác.
            </p>

            {/* Quick Metrics */}
            <div className="mt-5 flex flex-wrap items-center gap-4 text-xs font-medium text-teal-200">
              <div className="flex items-center gap-1.5 bg-white/10 px-3 py-1.5 rounded-xl border border-white/10 backdrop-blur-xs">
                <Sparkles className="w-4 h-4 text-amber-300" />
                <span><strong>{doctors.length}</strong> Bác sĩ chuyên khoa</span>
              </div>
              <div className="flex items-center gap-1.5 bg-white/10 px-3 py-1.5 rounded-xl border border-white/10 backdrop-blur-xs">
                <ShieldCheck className="w-4 h-4 text-emerald-300" />
                <span>Tiêu chuẩn y khoa quốc tế</span>
              </div>
              <div className="flex items-center gap-1.5 bg-white/10 px-3 py-1.5 rounded-xl border border-white/10 backdrop-blur-xs">
                <Clock className="w-4 h-4 text-teal-300" />
                <span>Đặt lịch tức thì 24/7</span>
              </div>
            </div>
          </div>

          {/* Action buttons on top right */}
          <div className="flex flex-col sm:flex-row lg:flex-col gap-2.5 shrink-0">
            <button
              onClick={() => {
                if (doctors.length > 0) {
                  handleOpenAddSlotModal(doctors[0]);
                } else {
                  notify('Chưa có bác sĩ nào để thêm ca khám.', 'error');
                }
              }}
              className="inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-white/15 hover:bg-white/25 text-white font-semibold text-xs sm:text-sm border border-white/20 transition-all backdrop-blur-md"
            >
              <PlusCircle className="w-4 h-4" />
              <span>Tạo Khung Giờ Mới</span>
            </button>
          </div>
        </div>

        {/* Decorative shape */}
        <div className="absolute right-0 top-0 bottom-0 w-1/3 bg-radial from-teal-400/15 to-transparent pointer-events-none" />
      </div>

      {/* Filter and Date Bar */}
      <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs space-y-4">
        <div className="flex flex-col md:flex-row gap-4 justify-between items-stretch md:items-center">
          {/* Search Box */}
          <div className="relative flex-1">
            <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Tìm theo tên bác sĩ, chuyên khoa, khoa phòng, học vị..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-10 pr-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-teal-500 focus:bg-white transition-all text-slate-800"
            />
          </div>

          {/* Quick Date Switcher & Date Picker */}
          <div className="flex flex-wrap items-center gap-2">
            <button
              onClick={() => setSelectedDate(getRelativeDate(0))}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                selectedDate === getRelativeDate(0)
                  ? 'bg-teal-700 text-white'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              Hôm nay
            </button>
            <button
              onClick={() => setSelectedDate(getRelativeDate(1))}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                selectedDate === getRelativeDate(1)
                  ? 'bg-teal-700 text-white'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              Ngày mai
            </button>
            <button
              onClick={() => setSelectedDate(getRelativeDate(2))}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                selectedDate === getRelativeDate(2)
                  ? 'bg-teal-700 text-white'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              Ngày kia
            </button>

            <div className="flex items-center gap-1.5 pl-2 border-l border-slate-200">
              <Calendar className="w-4 h-4 text-teal-600" />
              <input
                type="date"
                value={selectedDate}
                min={getRelativeDate(0)}
                onChange={(e) => setSelectedDate(e.target.value)}
                className="px-2.5 py-1.5 bg-slate-50 border border-slate-200 rounded-lg text-xs font-semibold text-slate-800 focus:outline-none focus:ring-2 focus:ring-teal-500"
              />
            </div>
          </div>
        </div>

        {/* Specialty Filter Pills */}
        <div className="flex items-center gap-2 overflow-x-auto pb-1 pt-1 no-scrollbar">
          <span className="text-xs font-bold text-slate-400 uppercase tracking-wider shrink-0 mr-1">
            Chuyên khoa:
          </span>
          {SPECIALTIES.map((spec) => (
            <button
              key={spec}
              onClick={() => setSelectedSpecialty(spec)}
              className={`px-3.5 py-1.5 rounded-full text-xs font-semibold transition-all shrink-0 ${
                selectedSpecialty === spec
                  ? 'bg-teal-700 text-white shadow-sm'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              {spec}
            </button>
          ))}
        </div>
      </div>

      {/* Doctor Cards Section */}
      {loading ? (
        <div className="bg-white rounded-2xl border border-slate-200/80 p-12 text-center text-slate-400">
          <RefreshCw className="w-8 h-8 mx-auto animate-spin text-teal-600 mb-3" />
          <p className="text-sm font-semibold text-slate-700">Đang đồng bộ dữ liệu Bác sĩ & Khung giờ khám...</p>
          <p className="text-xs text-slate-400 mt-1">Vui lòng đợi giây lát</p>
        </div>
      ) : filteredDoctors.length === 0 ? (
        <div className="bg-white rounded-2xl border border-slate-200/80 p-12 text-center text-slate-400">
          <Stethoscope className="w-12 h-12 mx-auto stroke-1 text-slate-300 mb-3" />
          <p className="text-base font-semibold text-slate-700">Không tìm thấy bác sĩ phù hợp</p>
          <p className="text-xs text-slate-400 mt-1">
            Vui lòng thử tìm kiếm với từ khóa khác hoặc liên hệ Quản trị viên để tạo thêm tài khoản bác sĩ.
          </p>
        </div>
      ) : (
        <div className="space-y-5">
          {filteredDoctors.map((doc) => {
            const slots = doctorSlots[doc.id] || [];
            const morningSlots = slots.filter((s) => s.startTime < '12:00:00');
            const afternoonSlots = slots.filter((s) => s.startTime >= '12:00:00');
            const freeSlotsCount = slots.filter((s) => !s.isBooked).length;

            return (
              <div
                key={doc.id}
                className="bg-white rounded-2xl border border-slate-200/80 p-5 sm:p-6 shadow-xs hover:shadow-md transition-all duration-200"
              >
                <div className="flex flex-col lg:flex-row gap-6">
                  {/* Doctor Info Left */}
                  <div className="flex items-start gap-4 lg:w-80 shrink-0">
                    <div className="relative shrink-0">
                      <img
                        src={doc.avatarUrl || PRESET_AVATARS[0]}
                        alt={doc.name}
                        className="w-20 h-20 sm:w-24 sm:h-24 rounded-2xl object-cover ring-2 ring-slate-100 shadow-sm"
                        onError={(e) => {
                          e.target.src = PRESET_AVATARS[0];
                        }}
                      />
                      <span
                        className={`absolute -bottom-1 -right-1 w-4 h-4 rounded-full border-2 border-white ${
                          doc.active ? 'bg-emerald-500' : 'bg-slate-400'
                        }`}
                        title={doc.active ? 'Đang hoạt động' : 'Tạm nghỉ'}
                      />
                    </div>

                    <div className="min-w-0 flex-1">
                      <div className="inline-block px-2.5 py-0.5 rounded-md bg-teal-50 text-teal-700 text-[11px] font-bold mb-1">
                        {doc.specialty}
                      </div>
                      <h2 className="text-base sm:text-lg font-bold text-slate-900 leading-snug">
                        {doc.name}
                      </h2>
                      <p className="text-xs text-slate-500 font-medium mt-0.5 flex items-center gap-1">
                        <GraduationCap className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                        <span className="truncate">{doc.qualification || 'Bác sĩ chuyên khoa'}</span>
                      </p>

                      <div className="mt-2.5 space-y-1 text-xs text-slate-600">
                        <div className="flex items-center gap-1.5">
                          <Award className="w-3.5 h-3.5 text-amber-500 shrink-0" />
                          <span>{doc.experienceYears} năm kinh nghiệm</span>
                        </div>
                        <div className="flex items-center gap-1.5">
                          <MapPin className="w-3.5 h-3.5 text-teal-600 shrink-0" />
                          <span>{doc.roomNumber || 'P.101 - Khoa Khám Bệnh'}</span>
                        </div>
                      </div>

                      <div className="mt-3 text-xs">
                        <span className="text-slate-400">Phí khám: </span>
                        <strong className="text-teal-700 font-bold text-sm">
                          {doc.consultationFee
                            ? doc.consultationFee.toLocaleString('vi-VN') + ' đ'
                            : 'Liên hệ'}
                        </strong>
                      </div>
                    </div>
                  </div>

                  {/* Doctor Bio and Slots Right */}
                  <div className="flex-1 lg:border-l lg:border-slate-100 lg:pl-6 flex flex-col justify-between">
                    <div>
                      {/* Short Bio */}
                      <p className="text-xs text-slate-600 leading-relaxed line-clamp-2 mb-4">
                        {doc.bio || 'Bác sĩ chuyên khoa giàu kinh nghiệm và y đức trong thăm khám, chẩn đoán và điều trị.'}
                      </p>

                      {/* Header for Slots */}
                      <div className="flex items-center justify-between mb-3">
                        <div className="flex items-center gap-2 text-xs font-bold text-slate-700">
                          <Clock className="w-4 h-4 text-teal-600" />
                          <span>
                            Lịch trực ngày {new Date(selectedDate).toLocaleDateString('vi-VN')}
                          </span>
                        </div>
                        <span className="text-[11px] font-semibold text-teal-700 bg-teal-50 px-2 py-0.5 rounded-md">
                          {freeSlotsCount} ca còn trống
                        </span>
                      </div>

                      {/* Slot Grid Container */}
                      {slots.length === 0 ? (
                        <div className="py-5 text-center text-xs text-slate-400 bg-slate-50 rounded-xl border border-dashed border-slate-200">
                          <p>Bác sĩ chưa có lịch trực trong ngày này.</p>
                          <button
                            onClick={() => handleOpenAddSlotModal(doc)}
                            className="mt-2 text-teal-700 font-semibold hover:underline"
                          >
                            + Thêm khung giờ cho ngày này
                          </button>
                        </div>
                      ) : (
                        <div className="space-y-3">
                          {/* Ca Sáng */}
                          {morningSlots.length > 0 && (
                            <div>
                              <div className="text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-1.5 flex items-center gap-1">
                                <span>Ca Sáng</span>
                                <span className="text-slate-300">({morningSlots.length} ca)</span>
                              </div>
                              <div className="grid grid-cols-2 sm:grid-cols-4 md:grid-cols-5 gap-2">
                                {morningSlots.map((slot) => {
                                  const timeLabel = `${slot.startTime.substring(0, 5)} - ${slot.endTime.substring(0, 5)}`;
                                  const isBooked = slot.isBooked;

                                  return (
                                    <button
                                      key={slot.id}
                                      disabled={isBooked}
                                      onClick={() => onSelectSlot(doc, slot)}
                                      className={`px-2 py-2 rounded-xl text-xs font-semibold transition-all border flex flex-col items-center justify-center gap-0.5 ${
                                        isBooked
                                          ? 'bg-slate-100 text-slate-400 border-slate-200 cursor-not-allowed line-through'
                                          : 'bg-teal-50/60 hover:bg-teal-600 hover:text-white text-teal-800 border-teal-200/80 shadow-xs hover:shadow-md'
                                      }`}
                                    >
                                      <span>{timeLabel}</span>
                                      <span className="text-[10px] font-normal opacity-80">
                                        {isBooked ? 'Đã kín' : 'Chọn khám'}
                                      </span>
                                    </button>
                                  );
                                })}
                              </div>
                            </div>
                          )}

                          {/* Ca Chiều */}
                          {afternoonSlots.length > 0 && (
                            <div>
                              <div className="text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-1.5 flex items-center gap-1">
                                <span>Ca Chiều</span>
                                <span className="text-slate-300">({afternoonSlots.length} ca)</span>
                              </div>
                              <div className="grid grid-cols-2 sm:grid-cols-4 md:grid-cols-5 gap-2">
                                {afternoonSlots.map((slot) => {
                                  const timeLabel = `${slot.startTime.substring(0, 5)} - ${slot.endTime.substring(0, 5)}`;
                                  const isBooked = slot.isBooked;

                                  return (
                                    <button
                                      key={slot.id}
                                      disabled={isBooked}
                                      onClick={() => onSelectSlot(doc, slot)}
                                      className={`px-2 py-2 rounded-xl text-xs font-semibold transition-all border flex flex-col items-center justify-center gap-0.5 ${
                                        isBooked
                                          ? 'bg-slate-100 text-slate-400 border-slate-200 cursor-not-allowed line-through'
                                          : 'bg-teal-50/60 hover:bg-teal-600 hover:text-white text-teal-800 border-teal-200/80 shadow-xs hover:shadow-md'
                                      }`}
                                    >
                                      <span>{timeLabel}</span>
                                      <span className="text-[10px] font-normal opacity-80">
                                        {isBooked ? 'Đã kín' : 'Chọn khám'}
                                      </span>
                                    </button>
                                  );
                                })}
                              </div>
                            </div>
                          )}
                        </div>
                      )}
                    </div>

                    {/* Action buttons footer on each card */}
                    <div className="mt-4 pt-3 border-t border-slate-100 flex flex-wrap items-center justify-between gap-2 text-xs">
                      <div className="flex items-center gap-2">
                        <button
                          onClick={() => setDetailDoctor(doc)}
                          className="inline-flex items-center gap-1 font-semibold text-slate-600 hover:text-teal-700 bg-slate-50 hover:bg-teal-50 px-2.5 py-1.5 rounded-lg border border-slate-200/60 transition-colors"
                        >
                          <Info className="w-3.5 h-3.5 text-teal-600" /> Hồ sơ chi tiết
                        </button>
                        <button
                          onClick={() => handleOpenScheduleModal(doc)}
                          className="inline-flex items-center gap-1 font-semibold text-slate-600 hover:text-teal-700 bg-slate-50 hover:bg-teal-50 px-2.5 py-1.5 rounded-lg border border-slate-200/60 transition-colors"
                        >
                          <CalendarRange className="w-3.5 h-3.5 text-indigo-600" /> Lịch tuần/tháng
                        </button>
                        <button
                          onClick={() => handleOpenAddSlotModal(doc)}
                          className="inline-flex items-center gap-1 font-semibold text-slate-600 hover:text-teal-700 bg-slate-50 hover:bg-teal-50 px-2.5 py-1.5 rounded-lg border border-slate-200/60 transition-colors"
                        >
                          <PlusCircle className="w-3.5 h-3.5 text-emerald-600" /> Thêm ca trực
                        </button>
                      </div>

                      <button
                        onClick={() => {
                          const firstFreeSlot = slots.find((s) => !s.isBooked);
                          if (firstFreeSlot) {
                            onSelectSlot(doc, firstFreeSlot);
                          } else {
                            notify('Bác sĩ đã kín lịch hôm nay. Vui lòng chọn ngày khác hoặc liên hệ hotline.', 'info');
                          }
                        }}
                        className="inline-flex items-center gap-1 font-bold text-teal-700 hover:text-teal-900 transition-colors ml-auto"
                      >
                        Đặt hẹn nhanh <ChevronRight className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* ============================================================== */}
      {/* MODAL 1: DOCTOR PROFILE DETAIL MODAL */}
      {/* ============================================================== */}
      {detailDoctor && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs">
          <div className="bg-white rounded-3xl max-w-2xl w-full max-h-[90vh] overflow-y-auto shadow-2xl border border-slate-200 animate-in fade-in zoom-in-95 duration-150">
            <div className="p-6 sm:p-8 space-y-6">
              {/* Header */}
              <div className="flex items-start justify-between">
                <div className="flex items-start gap-4">
                  <img
                    src={detailDoctor.avatarUrl || PRESET_AVATARS[0]}
                    alt={detailDoctor.name}
                    className="w-20 h-20 sm:w-24 sm:h-24 rounded-2xl object-cover ring-4 ring-teal-50 shadow-md shrink-0"
                  />
                  <div>
                    <span className="px-2.5 py-1 rounded-md bg-teal-50 text-teal-700 text-xs font-bold inline-block mb-1">
                      {detailDoctor.specialty}
                    </span>
                    <h3 className="text-xl font-bold text-slate-900">{detailDoctor.name}</h3>
                    <p className="text-xs font-semibold text-slate-500">{detailDoctor.qualification}</p>
                    <p className="text-xs text-slate-400 mt-1">{detailDoctor.department}</p>
                  </div>
                </div>
                <button
                  onClick={() => setDetailDoctor(null)}
                  className="p-2 rounded-xl text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              {/* Quick stats grid */}
              <div className="grid grid-cols-3 gap-3 p-4 rounded-2xl bg-slate-50 border border-slate-100 text-center">
                <div>
                  <span className="block text-xs text-slate-400 font-medium">Kinh nghiệm</span>
                  <strong className="text-base font-bold text-slate-800">
                    {detailDoctor.experienceYears} năm
                  </strong>
                </div>
                <div className="border-x border-slate-200">
                  <span className="block text-xs text-slate-400 font-medium">Phí khám</span>
                  <strong className="text-base font-bold text-teal-700">
                    {detailDoctor.consultationFee
                      ? detailDoctor.consultationFee.toLocaleString('vi-VN') + ' đ'
                      : 'Liên hệ'}
                  </strong>
                </div>
                <div>
                  <span className="block text-xs text-slate-400 font-medium">Vị trí</span>
                  <strong className="text-xs font-bold text-slate-800 truncate block">
                    {detailDoctor.roomNumber || 'Phòng 201'}
                  </strong>
                </div>
              </div>

              {/* Bio section */}
              <div>
                <h4 className="text-sm font-bold text-slate-800 mb-2">Tiểu sử & Quá trình công tác</h4>
                <p className="text-xs text-slate-600 leading-relaxed bg-slate-50/60 p-4 rounded-xl border border-slate-100">
                  {detailDoctor.bio || 'Bác sĩ có nhiều năm kinh nghiệm công tác tại các bệnh viện lớn tuyến trung ương, chuyên sâu trong chẩn đoán và điều trị đa dạng các bệnh lý chuyên khoa.'}
                </p>
              </div>

              {/* Clinical note */}
              <div className="p-4 rounded-xl bg-teal-50/60 border border-teal-100 text-teal-900 text-xs leading-relaxed space-y-1">
                <div className="font-bold flex items-center gap-1.5 text-teal-800">
                  <ShieldCheck className="w-4 h-4 text-teal-600" />
                  <span>Quy trình khám & tư vấn:</span>
                </div>
                <p className="text-teal-700">
                  Thời lượng mỗi ca khám tiêu chuẩn là 30 phút. Bệnh nhân vui lòng có mặt trước giờ hẹn 10 phút tại phòng {detailDoctor.roomNumber || 'khám'} để hoàn tất thủ tục đón tiếp.
                </p>
              </div>

              {/* Modal footer actions */}
              <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setDetailDoctor(null)}
                  className="px-4 py-2.5 rounded-xl border border-slate-200 text-slate-600 text-xs font-semibold hover:bg-slate-50 transition-colors"
                >
                  Đóng
                </button>
                <button
                  type="button"
                  onClick={() => {
                    const currentDoc = detailDoctor;
                    setDetailDoctor(null);
                    handleOpenScheduleModal(currentDoc);
                  }}
                  className="px-4 py-2.5 rounded-xl bg-teal-50 text-teal-700 hover:bg-teal-100 text-xs font-semibold transition-colors flex items-center gap-1.5"
                >
                  <CalendarRange className="w-4 h-4" />
                  <span>Xem lịch trực</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ============================================================== */}
      {/* MODAL 2: DOCTOR SCHEDULE (WEEK / MONTH) MODAL */}
      {/* ============================================================== */}
      {scheduleDoctor && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs">
          <div className="bg-white rounded-3xl max-w-4xl w-full max-h-[90vh] overflow-y-auto shadow-2xl border border-slate-200 animate-in fade-in zoom-in-95 duration-150">
            <div className="p-6 sm:p-8 space-y-5">
              {/* Header */}
              <div className="flex items-center justify-between pb-4 border-b border-slate-100">
                <div className="flex items-center gap-3">
                  <img
                    src={scheduleDoctor.avatarUrl || PRESET_AVATARS[0]}
                    alt={scheduleDoctor.name}
                    className="w-12 h-12 rounded-xl object-cover ring-2 ring-teal-50"
                  />
                  <div>
                    <h3 className="text-base font-bold text-slate-900">
                      Lịch Trực Chi Tiết: {scheduleDoctor.name}
                    </h3>
                    <p className="text-xs text-slate-500">
                      {scheduleDoctor.specialty} • {scheduleDoctor.department}
                    </p>
                  </div>
                </div>

                <button
                  onClick={() => setScheduleDoctor(null)}
                  className="p-2 rounded-xl text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              {/* View options & Date range navigation */}
              <div className="flex flex-col sm:flex-row items-center justify-between gap-3 bg-slate-50 p-3.5 rounded-2xl border border-slate-100">
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => handlePeriodChange('WEEK')}
                    className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-all ${
                      schedulePeriod === 'WEEK'
                        ? 'bg-teal-700 text-white shadow-xs'
                        : 'bg-white text-slate-600 hover:bg-slate-200'
                    }`}
                  >
                    Xem theo Tuần
                  </button>
                  <button
                    onClick={() => handlePeriodChange('MONTH')}
                    className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-all ${
                      schedulePeriod === 'MONTH'
                        ? 'bg-teal-700 text-white shadow-xs'
                        : 'bg-white text-slate-600 hover:bg-slate-200'
                    }`}
                  >
                    Xem theo Tháng
                  </button>
                </div>

                <div className="flex items-center gap-2">
                  <button
                    onClick={() => handleScheduleAnchorShift(schedulePeriod === 'WEEK' ? -7 : -30)}
                    className="p-1.5 bg-white hover:bg-slate-100 rounded-lg border border-slate-200 text-slate-600"
                    title="Khoảng trước"
                  >
                    <ChevronLeft className="w-4 h-4" />
                  </button>
                  <span className="text-xs font-bold text-slate-700 px-2">
                    {scheduleData?.fromDate} đến {scheduleData?.toDate}
                  </span>
                  <button
                    onClick={() => handleScheduleAnchorShift(schedulePeriod === 'WEEK' ? 7 : 30)}
                    className="p-1.5 bg-white hover:bg-slate-100 rounded-lg border border-slate-200 text-slate-600"
                    title="Khoảng sau"
                  >
                    <ChevronRight className="w-4 h-4" />
                  </button>
                </div>
              </div>

              {/* Schedule Days Grid */}
              {scheduleLoading ? (
                <div className="py-12 text-center text-slate-400">
                  <RefreshCw className="w-6 h-6 mx-auto animate-spin text-teal-600 mb-2" />
                  <p className="text-xs font-medium">Đang tải lịch trình từ Doctor Service...</p>
                </div>
              ) : !scheduleData?.days || scheduleData.days.length === 0 ? (
                <div className="py-12 text-center text-xs text-slate-400">
                  Không có dữ liệu ca trực trong khoảng thời gian này.
                </div>
              ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
                  {scheduleData.days.map((day) => {
                    const d = new Date(day.date);
                    const dayName = ['Chủ nhật', 'Thứ hai', 'Thứ ba', 'Thứ tư', 'Thứ năm', 'Thứ sáu', 'Thứ bảy'][
                      d.getDay()
                    ];
                    const slots = day.slots || [];
                    const freeCount = slots.filter((s) => !s.isBooked).length;

                    return (
                      <div
                        key={day.date}
                        className="bg-slate-50/70 rounded-2xl border border-slate-200/80 p-3.5 space-y-2.5"
                      >
                        <div className="flex items-center justify-between pb-2 border-b border-slate-200/60">
                          <div>
                            <span className="text-xs font-bold text-slate-800">{dayName}</span>
                            <span className="block text-[11px] text-slate-400">{day.date}</span>
                          </div>
                          <span className="text-[10px] font-bold px-2 py-0.5 rounded-md bg-teal-100 text-teal-800">
                            {freeCount} ca trống
                          </span>
                        </div>

                        {slots.length === 0 ? (
                          <p className="text-[11px] text-slate-400 text-center py-2">Nghỉ trực</p>
                        ) : (
                          <div className="space-y-1.5 max-h-48 overflow-y-auto pr-1">
                            {slots.map((s) => {
                              const label = `${s.startTime.substring(0, 5)} - ${s.endTime.substring(0, 5)}`;
                              return (
                                <button
                                  key={s.id}
                                  disabled={s.isBooked}
                                  onClick={() => {
                                    setScheduleDoctor(null);
                                    onSelectSlot(scheduleDoctor, s);
                                  }}
                                  className={`w-full text-left px-2 py-1.5 rounded-lg text-xs font-medium flex items-center justify-between border transition-all ${
                                    s.isBooked
                                      ? 'bg-slate-100 text-slate-400 border-slate-200 line-through cursor-not-allowed'
                                      : 'bg-white hover:bg-teal-600 hover:text-white text-slate-700 border-slate-200/80 shadow-2xs'
                                  }`}
                                >
                                  <span>{label}</span>
                                  <span className="text-[10px] opacity-75">
                                    {s.isBooked ? 'Đã kín' : 'Chọn'}
                                  </span>
                                </button>
                              );
                            })}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}

              {/* Close button */}
              <div className="flex justify-end pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setScheduleDoctor(null)}
                  className="px-4 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-semibold"
                >
                  Đóng
                </button>
              </div>
            </div>
          </div>
        </div>
      )}


      {/* ============================================================== */}
      {/* MODAL 4: CREATE TIME SLOT MODAL */}
      {/* ============================================================== */}
      {showAddSlotModal && slotTargetDoctor && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs">
          <div className="bg-white rounded-3xl max-w-md w-full shadow-2xl border border-slate-200 animate-in fade-in zoom-in-95 duration-150">
            <form onSubmit={handleCreateSlotSubmit} className="p-6 space-y-4">
              <div className="flex items-center justify-between pb-3 border-b border-slate-100">
                <div className="flex items-center gap-2 text-teal-800 font-bold text-sm">
                  <PlusCircle className="w-4 h-4 text-teal-600" />
                  <span>Tạo Khung Giờ Mới Cho Bác Sĩ</span>
                </div>
                <button
                  type="button"
                  onClick={() => setShowAddSlotModal(false)}
                  className="p-1 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-100"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              {/* Doctor target display */}
              <div className="flex items-center gap-3 p-3 bg-teal-50/60 rounded-xl border border-teal-100">
                <img
                  src={slotTargetDoctor.avatarUrl || PRESET_AVATARS[0]}
                  alt={slotTargetDoctor.name}
                  className="w-10 h-10 rounded-xl object-cover"
                />
                <div>
                  <h4 className="text-xs font-bold text-slate-900">{slotTargetDoctor.name}</h4>
                  <p className="text-[11px] text-teal-700">{slotTargetDoctor.specialty}</p>
                </div>
              </div>

              {/* Slot Date */}
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">
                  Ngày làm việc <span className="text-rose-500">*</span>
                </label>
                <input
                  type="date"
                  required
                  min={getRelativeDate(0)}
                  value={newSlotForm.slotDate}
                  onChange={(e) => setNewSlotForm({ ...newSlotForm, slotDate: e.target.value })}
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs font-medium text-slate-800 focus:outline-none focus:ring-2 focus:ring-teal-500 focus:bg-white"
                />
              </div>

              {/* Start & End Times */}
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">
                    Giờ bắt đầu <span className="text-rose-500">*</span>
                  </label>
                  <input
                    type="time"
                    required
                    step="3600"
                    value={newSlotForm.startTime}
                    onChange={(e) => {
                      const st = e.target.value;
                      const [h] = st.split(':');
                      const endH = String((Number(h) + 1) % 24).padStart(2, '0');
                      setNewSlotForm({ ...newSlotForm, startTime: st, endTime: `${endH}:00` });
                    }}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs font-medium text-slate-800 focus:outline-none focus:ring-2 focus:ring-teal-500 focus:bg-white"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">
                    Giờ kết thúc <span className="text-rose-500">*</span>
                  </label>
                  <input
                    type="time"
                    required
                    step="3600"
                    value={newSlotForm.endTime}
                    onChange={(e) => setNewSlotForm({ ...newSlotForm, endTime: e.target.value })}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs font-medium text-slate-800 focus:outline-none focus:ring-2 focus:ring-teal-500 focus:bg-white"
                  />
                </div>
              </div>

              {/* Quick Preset Slot Times */}
              <div>
                <span className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                  Chọn mẫu khung giờ 60 phút:
                </span>
                <div className="grid grid-cols-3 gap-1.5 text-center">
                  {[
                    ['08:00', '09:00'],
                    ['09:00', '10:00'],
                    ['10:00', '11:00'],
                    ['14:00', '15:00'],
                    ['15:00', '16:00'],
                    ['16:00', '17:00'],
                  ].map(([st, et]) => (
                    <button
                      key={st}
                      type="button"
                      onClick={() => setNewSlotForm({ ...newSlotForm, startTime: st, endTime: et })}
                      className="px-2 py-1 text-[11px] bg-slate-100 hover:bg-teal-50 hover:text-teal-700 rounded-lg text-slate-600 font-medium transition-colors"
                    >
                      {st} - {et}
                    </button>
                  ))}
                </div>
              </div>

              {/* Submit Buttons */}
              <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setShowAddSlotModal(false)}
                  className="px-3.5 py-2 rounded-xl border border-slate-200 text-slate-600 text-xs font-semibold hover:bg-slate-50"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={addSlotLoading}
                  className="px-4 py-2 rounded-xl bg-teal-700 hover:bg-teal-800 text-white text-xs font-bold shadow-md disabled:opacity-50 flex items-center gap-1.5"
                >
                  {addSlotLoading && <RefreshCw className="w-3 h-3 animate-spin" />}
                  <span>{addSlotLoading ? 'Đang tạo...' : 'Tạo Khung Giờ'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default DoctorSchedulePage;
