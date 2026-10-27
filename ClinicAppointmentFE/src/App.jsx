import React, { useState } from "react";
import {
  HeartPulse,
  CalendarDays,
  CalendarCheck2,
  ChevronRight,
  Clock3,
  Stethoscope,
  MapPin,
} from "lucide-react";
import { DoctorDirectory } from "./appointment/DoctorDirectory";
import { Booking } from "./appointment/Booking";
import { AppointmentHistory } from "./appointment/AppointmentHistory";

// The host may supply its existing user context. Appointment does not implement login or roles.
export default function App({
  patientId = null,
  actorSubject = "appointment-ui",
}) {
  const [tab, setTab] = useState("doctors"),
    [selection, setSelection] = useState(null);
  const menu = [
    {
      id: "doctors",
      icon: Stethoscope,
      title: "Bác sĩ & giờ khám",
      desc: "Tìm lịch phù hợp",
    },
    {
      id: "booking",
      icon: CalendarCheck2,
      title: "Đặt lịch khám",
      desc: "Xác nhận thông tin",
    },
    {
      id: "history",
      icon: CalendarDays,
      title: "Quản lý lịch hẹn",
      desc: "Theo dõi, đổi hoặc hủy lịch",
    },
  ];
  return (
    <div className="app">
      <header className="header">
        <a
          className="brand"
          href="/"
          onClick={(e) => {
            e.preventDefault();
            setTab("doctors");
          }}
        >
          <span>
            <HeartPulse size={27} />
          </span>
          <div>
            FClinic<small>CLINIC APPOINTMENTS</small>
          </div>
        </a>
        <div className="header-right">
          <span className="clinic-label">
            <MapPin size={15} />
            Phòng khám FClinic
          </span>
        </div>
      </header>
      <div className="app-body">
        <aside className="sidebar">
          <span className="sidebar-title">LỊCH HẸN PHÒNG KHÁM</span>
          <nav>
            {menu.map((item) => (
              <button
                key={item.id}
                className={tab === item.id ? "active" : ""}
                onClick={() => setTab(item.id)}
              >
                <item.icon size={21} />
                <span>
                  <strong>{item.title}</strong>
                  <small>{item.desc}</small>
                </span>
                {tab === item.id && <ChevronRight size={16} />}
              </button>
            ))}
          </nav>
          <div className="sidebar-note">
            <HeartPulse size={21} />
            <h3>
              Chăm sóc bắt đầu
              <br />
              từ một cuộc hẹn.
            </h3>
            <p>Lựa chọn thời gian phù hợp để chủ động chăm sóc sức khỏe.</p>
          </div>
          <div className="sidebar-bottom">
            <Clock3 size={15} />
            Giờ phòng khám: Asia/Saigon
          </div>
        </aside>
        <main className="main">
          {tab === "doctors" ? (
            <DoctorDirectory
              onPick={(doctor, slot) => {
                setSelection({ doctor, slot });
                setTab("booking");
              }}
            />
          ) : tab === "booking" ? (
            <Booking
              actorSubject={actorSubject}
              patientId={patientId}
              selection={selection}
              onSchedule={() => setTab("doctors")}
              onHistory={() => setTab("history")}
            />
          ) : (
            <AppointmentHistory
              actorSubject={actorSubject}
              patientId={patientId}
              onSchedule={() => setTab("doctors")}
            />
          )}
          <footer className="footer">
            <span>© 2026 FClinic</span>
            <span>Lịch hẹn và lịch sử khám được quản lý riêng biệt.</span>
          </footer>
        </main>
      </div>
    </div>
  );
}
