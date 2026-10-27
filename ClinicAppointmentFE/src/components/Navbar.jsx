import React, { useState, useEffect } from 'react';
import { HeartPulse, Bell, PhoneCall, ShieldCheck } from 'lucide-react';
import { notificationApi } from '../services/api';

export const Navbar = ({ onOpenNotifications }) => {
  const [unreadCount, setUnreadCount] = useState(2);
  const [currentTime, setCurrentTime] = useState(new Date());

  useEffect(() => {
    const timer = setInterval(() => setCurrentTime(new Date()), 1000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    notificationApi.getRecentNotifications(10).then((res) => {
      if (res && res.length) {
        setUnreadCount(res.length);
      }
    }).catch(() => {});
  }, []);

  return (
    <header className="sticky top-0 z-40 bg-white/95 backdrop-blur-md border-b border-slate-200/80 shadow-xs">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-20 flex items-center justify-between">
        {/* Brand */}
        <div className="flex items-center gap-3.5">
          <div className="w-12 h-12 rounded-2xl bg-gradient-to-br from-teal-500 via-teal-600 to-emerald-700 flex items-center justify-center shadow-lg shadow-teal-500/25 ring-4 ring-teal-50">
            <HeartPulse className="w-7 h-7 text-white animate-pulse" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-2xl font-black tracking-tight bg-gradient-to-r from-teal-700 via-teal-800 to-slate-900 bg-clip-text text-transparent">
                FClinic
              </span>
              <span className="hidden sm:inline-flex items-center gap-1 text-[11px] font-bold px-2 py-0.5 rounded-full bg-teal-100 text-teal-800 uppercase tracking-wider">
                <ShieldCheck className="w-3 h-3 text-teal-600" /> Medical Hub
              </span>
            </div>
            <p className="text-xs font-medium text-slate-500 hidden sm:block">
              Hệ Thống Đặt Lịch Khám & Chăm Sóc Sức Khỏe Tiêu Chuẩn Quốc Tế
            </p>
          </div>
        </div>

        {/* Right side actions */}
        <div className="flex items-center gap-3 sm:gap-5">
          {/* Status Badge */}
          <div className="hidden lg:flex items-center gap-2 px-3 py-1.5 rounded-xl bg-emerald-50 text-emerald-800 border border-emerald-200/60 text-xs font-semibold">
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
            <span>Hệ thống trực tuyến 24/7</span>
          </div>

          {/* Hotline */}
          <div className="hidden md:flex items-center gap-2 px-3 py-1.5 rounded-xl bg-rose-50 text-rose-700 border border-rose-100 text-xs font-bold">
            <PhoneCall className="w-4 h-4 text-rose-500" />
            <span>Hotline: 1900 6868</span>
          </div>

          {/* Notifications Button */}
          <button
            onClick={onOpenNotifications}
            className="relative p-2.5 rounded-xl bg-slate-100 hover:bg-teal-50 hover:text-teal-700 transition-colors border border-slate-200/80 text-slate-700"
            title="Xem thông báo"
          >
            <Bell className="w-5 h-5" />
            {unreadCount > 0 && (
              <span className="absolute -top-1 -right-1 px-1.5 py-0.5 min-w-[20px] h-5 bg-rose-500 text-white text-[11px] font-bold rounded-full flex items-center justify-center ring-2 ring-white">
                {unreadCount > 9 ? '9+' : unreadCount}
              </span>
            )}
          </button>
        </div>
      </div>
    </header>
  );
};
