import React, { useState, useEffect } from 'react';
import { Search, Calendar, Clock, MapPin, Award, CheckCircle2, ChevronRight, Stethoscope, RefreshCw } from 'lucide-react';
import { doctorApi } from '../services/api';

const SPECIALTIES = ['Tất cả', 'Tim Mạch', 'Nhi Khoa', 'Răng Hàm Mặt', 'Cơ Xương Khớp'];

export const DoctorSchedulePage = ({ onSelectSlot }) => {
  const [doctors, setDoctors] = useState([]);
  const [selectedSpecialty, setSelectedSpecialty] = useState('Tất cả');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedDate, setSelectedDate] = useState(() => new Date().toISOString().split('T')[0]);
  const [doctorSlots, setDoctorSlots] = useState({});
  const [loading, setLoading] = useState(true);

  // Load doctors
  const loadDoctors = async () => {
    setLoading(true);
    try {
      const spec = selectedSpecialty === 'Tất cả' ? undefined : selectedSpecialty;
      const data = await doctorApi.getDoctors(spec);
      setDoctors(data || []);

      // Fetch slots for each doctor
      const slotsMap = {};
      await Promise.all(
        (data || []).map(async (doc) => {
          try {
            const slots = await doctorApi.getDoctorSlots(doc.id, selectedDate);
            slotsMap[doc.id] = slots || [];
          } catch {
            slotsMap[doc.id] = [];
          }
        })
      );
      setDoctorSlots(slotsMap);
    } catch {
      // error handled by api fallback
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadDoctors();
  }, [selectedSpecialty, selectedDate]);

  // Filtered doctors by search query
  const filteredDoctors = doctors.filter((doc) => {
    const matchQuery =
      doc.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      doc.department.toLowerCase().includes(searchQuery.toLowerCase()) ||
      doc.specialty.toLowerCase().includes(searchQuery.toLowerCase());
    return matchQuery;
  });

  return (
    <div className="space-y-6">
      {/* Top Banner */}
      <div className="bg-gradient-to-r from-teal-700 via-teal-800 to-slate-900 rounded-3xl p-6 sm:p-8 text-white relative overflow-hidden shadow-xl">
        <div className="relative z-10 max-w-2xl">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-teal-500/30 text-teal-200 text-xs font-bold uppercase tracking-wider mb-4 border border-teal-400/20 backdrop-blur-md">
            <Stethoscope className="w-3.5 h-3.5" /> Bác Sĩ Chuyên Khoa Hàng Đầu
          </div>
          <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight">
            Đặt Lịch Khám Cùng Chuyên Gia
          </h1>
          <p className="mt-2 text-sm sm:text-base text-teal-100/90 leading-relaxed">
            Chọn bác sĩ phù hợp, theo dõi khung giờ khám thực tế và hoàn tất thủ tục đặt hẹn nhanh chóng, chính xác.
          </p>
        </div>

        {/* Decorative background shape */}
        <div className="absolute right-0 top-0 bottom-0 w-1/3 bg-radial from-teal-500/20 to-transparent pointer-events-none" />
      </div>

      {/* Filters and Search Bar */}
      <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs space-y-4">
        <div className="flex flex-col md:flex-row gap-4 justify-between items-stretch md:items-center">
          {/* Search */}
          <div className="relative flex-1">
            <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Tìm theo tên bác sĩ, khoa phòng, triệu chứng..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-10 pr-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-teal-500 focus:bg-white transition-all text-slate-800"
            />
          </div>

          {/* Date Picker */}
          <div className="flex items-center gap-2 shrink-0">
            <Calendar className="w-4 h-4 text-teal-600" />
            <span className="text-xs font-semibold text-slate-600">Ngày khám:</span>
            <input
              type="date"
              value={selectedDate}
              min={new Date().toISOString().split('T')[0]}
              onChange={(e) => setSelectedDate(e.target.value)}
              className="px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm font-medium text-slate-800 focus:outline-none focus:ring-2 focus:ring-teal-500"
            />
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

      {/* Doctor Cards List */}
      {loading ? (
        <div className="bg-white rounded-2xl border border-slate-200/80 p-12 text-center text-slate-400">
          <RefreshCw className="w-8 h-8 mx-auto animate-spin text-teal-600 mb-3" />
          <p className="text-sm font-medium">Đang tải danh sách bác sĩ & khung giờ...</p>
        </div>
      ) : filteredDoctors.length === 0 ? (
        <div className="bg-white rounded-2xl border border-slate-200/80 p-12 text-center text-slate-400">
          <Stethoscope className="w-12 h-12 mx-auto stroke-1 text-slate-300 mb-3" />
          <p className="text-base font-semibold text-slate-700">Không tìm thấy bác sĩ phù hợp</p>
          <p className="text-xs text-slate-400 mt-1">Vui lòng thay đổi từ khóa tìm kiếm hoặc chọn chuyên khoa khác.</p>
        </div>
      ) : (
        <div className="space-y-5">
          {filteredDoctors.map((doc) => {
            const slots = doctorSlots[doc.id] || [];

            return (
              <div
                key={doc.id}
                className="bg-white rounded-2xl border border-slate-200/80 p-5 sm:p-6 shadow-xs hover:shadow-md transition-all duration-200"
              >
                <div className="flex flex-col lg:flex-row gap-6">
                  {/* Doctor Info Left */}
                  <div className="flex items-start gap-4 lg:w-80 shrink-0">
                    <img
                      src={doc.avatarUrl}
                      alt={doc.name}
                      className="w-20 h-20 sm:w-24 sm:h-24 rounded-2xl object-cover ring-2 ring-slate-100 shadow-sm shrink-0"
                    />
                    <div className="min-w-0">
                      <div className="inline-block px-2.5 py-0.5 rounded-md bg-teal-50 text-teal-700 text-[11px] font-bold mb-1">
                        {doc.specialty}
                      </div>
                      <h2 className="text-base sm:text-lg font-bold text-slate-900 leading-snug">
                        {doc.name}
                      </h2>
                      <p className="text-xs text-slate-500 font-medium mt-0.5">{doc.qualification}</p>

                      <div className="mt-2.5 space-y-1 text-xs text-slate-600">
                        <div className="flex items-center gap-1.5">
                          <Award className="w-3.5 h-3.5 text-amber-500" />
                          <span>{doc.experienceYears} năm kinh nghiệm</span>
                        </div>
                        <div className="flex items-center gap-1.5">
                          <MapPin className="w-3.5 h-3.5 text-teal-600" />
                          <span>{doc.roomNumber || 'Phòng khám đa khoa'}</span>
                        </div>
                      </div>

                      <div className="mt-3 text-xs">
                        <span className="text-slate-400">Phí khám: </span>
                        <strong className="text-teal-700 font-bold text-sm">
                          {doc.consultationFee ? doc.consultationFee.toLocaleString('vi-VN') + ' đ' : 'Liên hệ'}
                        </strong>
                      </div>
                    </div>
                  </div>

                  {/* Doctor Bio and Slots Right */}
                  <div className="flex-1 lg:border-l lg:border-slate-100 lg:pl-6 flex flex-col justify-between">
                    <div>
                      <p className="text-xs text-slate-600 leading-relaxed line-clamp-2 mb-4">
                        {doc.bio}
                      </p>

                      {/* Available Slots Title */}
                      <div className="flex items-center justify-between mb-2.5">
                        <div className="flex items-center gap-2 text-xs font-bold text-slate-700">
                          <Clock className="w-4 h-4 text-teal-600" />
                          <span>Khung giờ khám ngày {new Date(selectedDate).toLocaleDateString('vi-VN')}</span>
                        </div>
                        <span className="text-[11px] text-slate-400">
                          {slots.filter((s) => !s.isBooked).length} khung giờ trống
                        </span>
                      </div>

                      {/* Slot Buttons Grid */}
                      <div className="grid grid-cols-2 sm:grid-cols-4 md:grid-cols-5 gap-2">
                        {slots.length === 0 ? (
                          <div className="col-span-full py-4 text-center text-xs text-slate-400 bg-slate-50 rounded-xl">
                            Chưa có lịch trực trong ngày này. Vui lòng chọn ngày khác.
                          </div>
                        ) : (
                          slots.map((slot) => {
                            const timeLabel = `${slot.startTime.substring(0, 5)} - ${slot.endTime.substring(0, 5)}`;
                            const isBooked = slot.isBooked;

                            return (
                              <button
                                key={slot.id}
                                disabled={isBooked}
                                onClick={() => onSelectSlot(doc, slot)}
                                className={`px-2.5 py-2 rounded-xl text-xs font-semibold transition-all border flex flex-col items-center justify-center gap-0.5 ${
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
                          })
                        )}
                      </div>
                    </div>

                    <div className="mt-4 pt-3 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500">
                      <span>{doc.department}</span>
                      <button
                        onClick={() => {
                          const firstSlot = slots.find((s) => !s.isBooked);
                          if (firstSlot) onSelectSlot(doc, firstSlot);
                        }}
                        className="inline-flex items-center gap-1 font-semibold text-teal-700 hover:text-teal-900 transition-colors"
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
    </div>
  );
};

export default DoctorSchedulePage;
