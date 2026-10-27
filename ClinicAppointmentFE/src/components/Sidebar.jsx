import React from 'react';
import { Stethoscope, CalendarCheck, Clock, Sparkles } from 'lucide-react';

export const Sidebar = ({ activeTab, onTabChange }) => {
  const menuItems = [
    {
      id: 'doctors',
      label: 'Đội ngũ Bác sĩ & Lịch khám',
      icon: Stethoscope,
      description: 'Danh sách bác sĩ, chuyên khoa & giờ trực',
    },
    {
      id: 'booking',
      label: 'Đặt lịch khám trực tuyến',
      icon: CalendarCheck,
      description: 'Quy trình đặt hẹn nhanh chóng & tiện lợi',
    },
    {
      id: 'history',
      label: 'Tra cứu & Quản lý lịch hẹn',
      icon: Clock,
      description: 'Kiểm tra mã FC, trạng thái & hủy lịch',
    },
  ];

  return (
    <aside className="w-full lg:w-72 shrink-0">
      <div className="bg-white rounded-2xl border border-slate-200/80 p-4 shadow-sm space-y-1 sticky top-28">
        <div className="px-3 py-2 text-xs font-bold text-slate-400 uppercase tracking-wider">
          Phân Hệ Chức Năng
        </div>

        {menuItems.map((item) => {
          const Icon = item.icon;
          const isActive = activeTab === item.id;

          return (
            <button
              key={item.id}
              onClick={() => onTabChange(item.id)}
              className={`w-full text-left p-3.5 rounded-xl transition-all duration-200 flex items-start gap-3.5 group relative ${
                isActive
                  ? 'bg-gradient-to-r from-teal-600 to-teal-700 text-white shadow-md shadow-teal-700/20'
                  : 'hover:bg-slate-50 text-slate-700 hover:text-teal-700'
              }`}
            >
              <div
                className={`p-2 rounded-lg transition-colors ${
                  isActive ? 'bg-white/20 text-white' : 'bg-slate-100 text-slate-500 group-hover:bg-teal-50 group-hover:text-teal-600'
                }`}
              >
                <Icon className="w-5 h-5 shrink-0" />
              </div>

              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-sm truncate">{item.label}</span>
                  {item.badge && (
                    <span
                      className={`text-[10px] font-extrabold px-1.5 py-0.5 rounded-md ${
                        isActive
                          ? 'bg-white/25 text-white'
                          : 'bg-emerald-100 text-emerald-800'
                      }`}
                    >
                      {item.badge}
                    </span>
                  )}
                </div>
                <p
                  className={`text-xs mt-0.5 truncate ${
                    isActive ? 'text-teal-100' : 'text-slate-400 group-hover:text-slate-500'
                  }`}
                >
                  {item.description}
                </p>
              </div>
            </button>
          );
        })}

        {/* Info card */}
        <div className="mt-6 pt-4 border-t border-slate-100">
          <div className="p-4 rounded-xl bg-gradient-to-br from-teal-50 to-emerald-50/50 border border-teal-100 text-slate-700">
            <div className="flex items-center gap-2 text-teal-800 font-bold text-xs mb-1.5">
              <Sparkles className="w-4 h-4 text-teal-600" />
              <span>Phòng khám FClinic</span>
            </div>
            <p className="text-xs text-slate-600 leading-relaxed">
              Hệ thống đặt lịch khám bệnh trực tuyến và chăm sóc sức khỏe hiện đại, an toàn và nhanh chóng.
            </p>
          </div>
        </div>
      </div>
    </aside>
  );
};
