import React, { useCallback, useRef, useState, useEffect } from "react";
import {
  HeartPulse,
  CalendarDays,
  CalendarCheck2,
  ChevronRight,
  Clock3,
  Stethoscope,
  MapPin,
  Bell,
  UsersRound,
  Building2,
  ShieldCheck,
  LogOut,
  User,
} from "lucide-react";
import { AuthProvider, useAuth } from "./context/AuthContext";
import { LoginPage } from "./pages/LoginPage";
import { StaffWalkinPage } from "./pages/StaffWalkinPage";
import { AdminUsersPage } from "./pages/AdminUsersPage";
import { DoctorDashboardPage } from "./pages/DoctorDashboardPage";
import { DoctorDirectory } from "./appointment/DoctorDirectory";
import { Booking } from "./appointment/Booking";
import { AppointmentHistory } from "./appointment/AppointmentHistory";
import { NotificationDrawer } from "./components/NotificationDrawer";
import { PatientModule } from "./patient/PatientModule";

function getRoleLabel(role) {
  switch (role) {
    case "ROLE_ADMIN":
      return { text: "Quản trị viên", class: "admin" };
    case "ROLE_DOCTOR":
      return { text: "Bác sĩ", class: "doctor" };
    case "ROLE_STAFF":
      return { text: "Tiếp tân quầy", class: "staff" };
    case "ROLE_PATIENT":
    default:
      return { text: "Bệnh nhân", class: "patient" };
  }
}

function getRoleMenu(role) {
  switch (role) {
    case "ROLE_DOCTOR":
      return [
        {
          id: "doctor-dashboard",
          icon: Stethoscope,
          title: "Lịch khám Bác sĩ",
          desc: "Ca trực & danh sách hẹn phụ trách",
        },
        {
          id: "patient-records",
          icon: UsersRound,
          title: "Hồ sơ bệnh án",
          desc: "Tra cứu & cập nhật bệnh án",
        },
      ];

    case "ROLE_STAFF":
      return [
        {
          id: "staff-walkin",
          icon: Building2,
          title: "Tiếp nhận tại quầy",
          desc: "Tra cứu & tạo bệnh nhân",
        },
        {
          id: "patient-records",
          icon: UsersRound,
          title: "Hồ sơ bệnh án",
          desc: "Tra cứu & cập nhật bệnh án",
        },
        {
          id: "doctors",
          icon: Stethoscope,
          title: "Bác sĩ & giờ khám",
          desc: "Chọn khung giờ khám",
        },
        {
          id: "booking",
          icon: CalendarCheck2,
          title: "Đặt lịch tại quầy",
          desc: "Xác nhận lịch hẹn",
        },
        {
          id: "history",
          icon: CalendarDays,
          title: "Quản lý lịch hẹn",
          desc: "Toàn bộ lịch hẹn phòng khám",
        },
      ];

    case "ROLE_ADMIN":
      return [
        {
          id: "admin-users",
          icon: ShieldCheck,
          title: "Quản trị người dùng",
          desc: "Tài khoản & phân quyền Keycloak",
        },
      ];

    case "ROLE_PATIENT":
    default:
      return [
        {
          id: "doctors",
          icon: Stethoscope,
          title: "Bác sĩ & giờ khám",
          desc: "Tìm bác sĩ & khung giờ phù hợp",
        },
        {
          id: "booking",
          icon: CalendarCheck2,
          title: "Đặt lịch khám",
          desc: "Xác nhận thông tin đặt lịch",
        },
        {
          id: "history",
          icon: CalendarDays,
          title: "Lịch hẹn của tôi",
          desc: "Theo dõi, đổi hoặc hủy lịch",
        },
      ];
  }
}

function getDefaultTab(role) {
  switch (role) {
    case "ROLE_DOCTOR":
      return "doctor-dashboard";
    case "ROLE_STAFF":
      return "staff-walkin";
    case "ROLE_ADMIN":
      return "admin-users";
    case "ROLE_PATIENT":
    default:
      return "doctors";
  }
}

function MainApp() {
  const { user, token, isAuthenticated, isLoading, logout } = useAuth();
  const [tab, setTab] = useState("doctors");
  const [selection, setSelection] = useState(null);
  const [isNotificationOpen, setIsNotificationOpen] = useState(false);
  const [walkinPatient, setWalkinPatient] = useState(null);

  const patientLeave = useRef(null);
  const registerPatientGuard = useCallback((guard) => {
    patientLeave.current = guard;
  }, []);

  // Update default tab when user role changes
  useEffect(() => {
    if (user?.role) {
      setTab(getDefaultTab(user.role));
    }
  }, [user?.role]);

  const selectTab = (next) => {
    if (tab !== "patient-records" || !patientLeave.current || patientLeave.current()) {
      setTab(next);
    }
  };

  // If loading auth session
  if (isLoading) {
    return (
      <div className="login-page-wrapper">
        <div style={{ textAlign: "center" }}>
          <HeartPulse size={48} color="#0d9488" className="animate-pulse mx-auto mb-4" />
          <h2 style={{ color: "#0f766e", fontSize: 20, fontWeight: 700 }}>FClinic Healthcare</h2>
          <p style={{ color: "#64748b", marginTop: 8 }}>Đang khởi tạo phiên làm việc...</p>
        </div>
      </div>
    );
  }

  // If not authenticated, always show LoginPage
  if (!isAuthenticated) {
    return <LoginPage />;
  }

  const menu = getRoleMenu(user?.role);
  const roleBadge = getRoleLabel(user?.role);
  const patientId = walkinPatient?.patientId || user?.patientId || null;
  const actorSubject = user?.fullName || user?.username || "authenticated-user";
  const patientSession = {
    user,
    getAccessToken: () => token,
    permissions: {
      createPatient: user?.role === "ROLE_STAFF",
      canUpdatePatient: () => user?.role === "ROLE_STAFF",
    },
  };

  return (
    <div className="app">
      <header className="header">
        <a
          className="brand"
          href="/"
          onClick={(e) => {
            e.preventDefault();
            selectTab(getDefaultTab(user?.role));
          }}
        >
          <span>
            <HeartPulse size={27} />
          </span>
          <div>
            FClinic<small>CLINIC APPOINTMENTS</small>
          </div>
        </a>

        <div className="header-right" style={{ display: "flex", alignItems: "center", gap: "1rem" }}>
          <span className="clinic-label">
            <MapPin size={15} />
            Phòng khám FClinic
          </span>

          {/* User profile & Role badge */}
          <div className="header-user-badge-wrap">
            <div className="user-identity-box">
              <span className="user-identity-name">{user?.fullName || user?.username}</span>
              <span className={`badge-role ${roleBadge.class}`}>{roleBadge.text}</span>
            </div>
          </div>

          {/* Notifications button */}
          <button
            type="button"
            className="noti-btn"
            onClick={() => setIsNotificationOpen(true)}
            title="Xem thông báo hệ thống"
            style={{
              display: "flex",
              alignItems: "center",
              gap: "0.4rem",
              background: "#f8fafc",
              border: "1px solid #cbd5e1",
              padding: "0.45rem 0.75rem",
              borderRadius: "0.5rem",
              cursor: "pointer",
              color: "#1e293b",
              fontWeight: 500,
              fontSize: "0.875rem",
            }}
          >
            <Bell size={16} />
            <span>Thông báo</span>
          </button>

          {/* Logout button */}
          <button
            type="button"
            className="btn-logout"
            onClick={logout}
            title="Đăng xuất khỏi hệ thống"
          >
            <LogOut size={15} />
            <span>Đăng xuất</span>
          </button>
        </div>
      </header>

      <div className="app-body">
        <aside className="sidebar">
          <span className="sidebar-title">MENU HỆ THỐNG</span>
          <nav>
            {menu.map((item) => (
              <button
                key={item.id}
                className={tab === item.id ? "active" : ""}
                onClick={() => selectTab(item.id)}
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
              Chăm sóc sức khỏe
              <br />
              toàn diện & tận tâm.
            </h3>
            <p>Hệ thống hỗ trợ quản trị và đặt lịch khám tức thời.</p>
          </div>

          <div className="sidebar-bottom">
            <Clock3 size={15} />
            Giờ phòng khám: Asia/Saigon
          </div>
        </aside>

        <main className="main">
          {/* ROLE ADMIN VIEW */}
          {tab === "admin-users" ? (
            <AdminUsersPage />
          ) : tab === "staff-walkin" ? (
            /* ROLE STAFF VIEW */
            <StaffWalkinPage
              onSelectPatientForBooking={(selectedPatient) => {
                setWalkinPatient(selectedPatient);
                selectTab("doctors");
              }}
            />
          ) : tab === "doctor-dashboard" ? (
            /* ROLE DOCTOR VIEW */
            <DoctorDashboardPage />
          ) : tab === "patient-records" ? (
            /* PATIENT RECORDS VIEW */
            <PatientModule session={patientSession} onBeforeLeave={registerPatientGuard} />
          ) : tab === "doctors" ? (
            /* DOCTORS DIRECTORY */
            <DoctorDirectory
              onPick={(doctor, slot) => {
                setSelection({ doctor, slot });
                setTab("booking");
              }}
            />
          ) : tab === "booking" ? (
            /* BOOKING VIEW */
            <Booking
              actorSubject={actorSubject}
              patientId={patientId}
              selection={selection}
              onSchedule={() => setTab("doctors")}
              onHistory={() => setTab("history")}
            />
          ) : (
            /* APPOINTMENT HISTORY */
            <AppointmentHistory
              actorSubject={actorSubject}
              patientId={patientId}
              onSchedule={() => setTab("doctors")}
            />
          )}

          <footer className="footer">
            <span>© 2026 FClinic Healthcare</span>
            <span>Hệ thống Quản lý Phòng khám & Đặt lịch đa vai trò.</span>
          </footer>
        </main>
      </div>

      <NotificationDrawer
        key={`${user?.id}:${token}`}
        isOpen={isNotificationOpen}
        onClose={() => setIsNotificationOpen(false)}
        onNotify={(msg) => console.log(msg)}
      />
    </div>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <MainApp />
    </AuthProvider>
  );
}
