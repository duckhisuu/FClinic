import React, { useState } from 'react';
import { Navbar } from './Navbar';
import { Sidebar } from './Sidebar';
import { NotificationDrawer } from './NotificationDrawer';
import { ToastStack } from './Toast';

export const Layout = ({ activeTab, onTabChange, toasts = [], onDismissToast, children }) => {
  const [isNotifOpen, setIsNotifOpen] = useState(false);

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col font-sans">
      <Navbar onOpenNotifications={() => setIsNotifOpen(true)} />

      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex flex-col lg:flex-row gap-8 items-start">
          <Sidebar activeTab={activeTab} onTabChange={onTabChange} />
          <section className="flex-1 w-full min-w-0">{children}</section>
        </div>
      </main>

      <NotificationDrawer isOpen={isNotifOpen} onClose={() => setIsNotifOpen(false)} />
      <ToastStack toasts={toasts} onDismiss={onDismissToast} />

      <footer className="bg-white border-t border-slate-200/80 py-6 mt-12 text-center text-xs text-slate-500">
        <div className="max-w-7xl mx-auto px-4">
          <p className="font-semibold text-slate-700">© 2026 FClinic - Healthcare Appointment Platform</p>
          <p className="mt-1 text-slate-400">
            Hệ thống đặt lịch khám và chăm sóc sức khỏe trực tuyến tiêu chuẩn quốc tế
          </p>
        </div>
      </footer>
    </div>
  );
};

export default Layout;
