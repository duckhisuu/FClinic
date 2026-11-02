import React, { useState, useCallback } from 'react';
import { Layout } from './components/Layout';
import { DoctorSchedulePage } from './pages/DoctorSchedulePage';
import { BookingPage } from './pages/BookingPage';
import { PatientHistoryPage } from './pages/PatientHistoryPage';
import { OutboxMonitorPage } from './pages/OutboxMonitorPage';

export const App = () => {
  const [activeTab, setActiveTab] = useState('doctors');
  const [selectedDoctor, setSelectedDoctor] = useState(null);
  const [selectedSlot, setSelectedSlot] = useState(null);
  const [toasts, setToasts] = useState([]);

  const notify = useCallback((message, options = {}) => {
    const id = crypto.randomUUID ? crypto.randomUUID() : 'toast-' + Date.now();
    setToasts((current) => [
      ...current,
      {
        id,
        message,
        title: options.title,
        type: options.type || 'info',
      },
    ]);

    setTimeout(() => {
      setToasts((current) => current.filter((t) => t.id !== id));
    }, options.duration || 5000);
  }, []);

  const dismissToast = useCallback((id) => {
    setToasts((current) => current.filter((t) => t.id !== id));
  }, []);

  const handleSelectSlot = (doctor, slot) => {
    setSelectedDoctor(doctor);
    setSelectedSlot(slot);
    setActiveTab('booking');
    notify(`Đã chọn khám với ${doctor.name} lúc ${slot.startTime.substring(0, 5)}. Vui lòng hoàn tất thông tin đặt hẹn.`, {
      type: 'info',
      title: 'Đã chọn khung giờ',
    });
  };

  const handleBookingSuccess = (appointment) => {
    notify(`Đặt lịch khám thành công! Mã hẹn: ${appointment.bookingCode}. Thông báo xác nhận đã được gửi đi.`, {
      type: 'success',
      title: 'Xác nhận thành công',
      duration: 6000,
    });
  };

  return (
    <Layout
      activeTab={activeTab}
      onTabChange={setActiveTab}
      toasts={toasts}
      onDismissToast={dismissToast}
    >
      {activeTab === 'doctors' && (
        <DoctorSchedulePage onSelectSlot={handleSelectSlot} onNotify={notify} />
      )}

      {activeTab === 'booking' && (
        <BookingPage
          selectedDoctor={selectedDoctor}
          selectedSlot={selectedSlot}
          onBookingSuccess={handleBookingSuccess}
          onBackToSchedule={() => setActiveTab('doctors')}
          onViewHistory={() => setActiveTab('history')}
        />
      )}

      {activeTab === 'history' && (
        <PatientHistoryPage onBookNew={() => setActiveTab('doctors')} />
      )}

      {activeTab === 'outbox' && (
        <OutboxMonitorPage />
      )}
    </Layout>
  );
};

export default App;
