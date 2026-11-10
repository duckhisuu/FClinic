import React, { useCallback, useRef, useState, useEffect, useMemo } from "react";
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
  UserPlus,
} from "lucide-react";
import { AuthProvider, useAuth } from "./context/AuthContext";
import { LoginPage } from "./pages/LoginPage";
import { accessTokenForIdentity } from './services/session';
import { StaffWalkinPage } from "./pages/StaffWalkinPage";
import { AdminUsersPage } from "./pages/AdminUsersPage";
import { DoctorDashboardPage } from "./pages/DoctorDashboardPage";
import { DoctorDirectory } from "./appointment/DoctorDirectory";
import { Booking } from "./appointment/Booking";
import { AppointmentHistory } from "./appointment/AppointmentHistory";
import { NotificationDrawer } from "./components/NotificationDrawer";
import { PatientModule } from "./patient/PatientModule";
import { PatientOnboardingPage } from "./pages/PatientOnboardingPage";

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

function getRoleMenu(userOrRole) {
  const role = typeof userOrRole === "string" ? userOrRole : userOrRole?.role;
  const user = typeof userOrRole === "object" ? userOrRole : null;
  switch (role) {
    case "ROLE_DOCTOR":
      return [
        {
          id: "doctor-dashboard",
          icon: Stethoscope,
          title: "Lịch khám Bác sĩ",
          desc: "Ca trực & danh sách hẹn phụ trách",
        },
      ];

    case "ROLE_STAFF":
      return [
        {
          id: "staff-walkin",
          icon: Building2,
          title: "Tiếp nhận tại quầy",
          desc: "Chọn hồ sơ bệnh nhân",
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
          desc: "Lịch hẹn trong phạm vi phân công",
        },
        { id: "patient-records", icon: UsersRound, title: "Quản lý bệnh nhân", desc: "Hồ sơ theo quyền được cấp" },
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
    default: {
      const items = [];
      if (user && !user.patientId) {
        items.push({
          id: "patient-onboarding",
          icon: UserPlus,
          title: "Khởi tạo hồ sơ",
          desc: "Hoàn tất hồ sơ để đặt lịch",
        });
      }
      items.push(
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
        }
      );
      return items;
    }
  }
}

function getDefaultTab(user) {
  const role = typeof user === "string" ? user : user?.role;
  switch (role) {
    case "ROLE_DOCTOR":
      return "doctor-dashboard";
    case "ROLE_STAFF":
      return "staff-walkin";
    case "ROLE_ADMIN":
      return "admin-users";
    case "ROLE_PATIENT":
    default:
      return typeof user === "object" && !user?.patientId ? "patient-onboarding" : "doctors";
  }
}

function MainApp() {
  const { user, capabilities = {}, refreshCapabilities, isAuthenticated, isLoading, logout } = useAuth();
  const [tab, setTab] = useState(() => getDefaultTab(user));
  const [selection, setSelection] = useState(null);
  const [isNotificationOpen, setIsNotificationOpen] = useState(false);
  const [walkinPatient, setWalkinPatient] = useState(null);
  const patientSession = useMemo(() => ({ getAccessToken: () => accessTokenForIdentity(user),
    patientId: user?.patientId, doctorId: user?.doctorId,
    onPatientAccess: async id => {
      if (user?.role === 'ROLE_STAFF' && !capabilities.updatePatientIds?.includes(id)) await refreshCapabilities?.();
    },
    permissions: { ...capabilities,
      canUpdatePatient: id => capabilities.updatePatientIds?.includes(id) === true,
      canCreateRecord: id => capabilities.createRecordPatientIds?.includes(id) === true,
      canReadRecord: id => (user?.role === 'ROLE_PATIENT' && user?.patientId === id)
        || capabilities.readRecordPatientIds?.includes(id) === true,
      canUpdateRecord: id => capabilities.updateRecordPatientIds?.includes(id) === true,
    } }), [user?.id, user?.role, user?.patientId, user?.doctorId, capabilities, refreshCapabilities]);

  const patientLeave = useRef(null);
  const registerPatientGuard = useCallback((guard) => {
    patientLeave.current = guard;
  }, []);

  // Update default tab when user role changes
  useEffect(() => {
    if (user?.role) {
      const requested = window.location.hash.slice(1).split('?')[0];
      const allowed = getRoleMenu(user).map(item => item.id);
      if (user.role === 'ROLE_DOCTOR' || (user.role === 'ROLE_PATIENT' && user.patientId)) allowed.push('patient-records');
      setTab(allowed.includes(requested) ? requested : getDefaultTab(user));
    }
  }, [user?.role, user?.patientId]);

  const selectTab = (next) => {
    if (tab !== "patient-records" || !patientLeave.current || patientLeave.current()) {
      window.history.pushState({}, "", "#" + next);
      setTab(next);
    }
  };

  useEffect(() => {
    const restore = () => {
      const requested = window.location.hash.slice(1).split('?')[0];
      const allowed = getRoleMenu(user).map(item => item.id);
      if (user?.role === 'ROLE_DOCTOR' || (user?.role === 'ROLE_PATIENT' && user.patientId)) allowed.push('patient-records');
      if (allowed.includes(requested) && (tab !== "patient-records" || !patientLeave.current || patientLeave.current())) setTab(requested);
    };
    window.addEventListener('popstate', restore);
    return () => window.removeEventListener('popstate', restore);
  }, [user?.id, user?.role, tab]);
  const openPatient = id => {
    if (tab === 'patient-records' && patientLeave.current && !patientLeave.current()) return;
    window.history.pushState({}, '', '#patient-records?' + new URLSearchParams({ view: 'profile', patientId: id }));
    setTab('patient-records');
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

  const menu = getRoleMenu(user);
  if (user?.role === 'ROLE_DOCTOR' || (user?.role === 'ROLE_PATIENT' && user.patientId)) menu.push({ id: 'patient-records', icon: UsersRound, title: 'Hồ sơ & bệnh án', desc: 'Hồ sơ trong phạm vi được cấp' });
  const roleBadge = getRoleLabel(user?.role);
  const patientId = user?.role === "ROLE_STAFF" ? walkinPatient?.id || null : user?.patientId || null;
  const actorSubject = user?.id;

  return (
    <div className="app">
      <header className="header">
        <a
          className="brand"
          href="/"
          onClick={(e) => {
            e.preventDefault();
            selectTab(getDefaultTab(user));
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
            onClick={() => { if (tab !== "patient-records" || !patientLeave.current || patientLeave.current()) logout(); }}
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
          {user?.role === "ROLE_STAFF" && walkinPatient && ["booking", "history", "doctors"].includes(tab) &&
            <div className="notice"><span>Hồ sơ đang chọn: {walkinPatient.fullName} · {walkinPatient.patientCode}</span>
              <button className="text-button" onClick={() => { setWalkinPatient(null); setSelection(null); }}>Bỏ chọn hồ sơ</button></div>}
          {/* ROLE ADMIN VIEW */}
          {tab === "admin-users" ? (
            <AdminUsersPage />
          ) : tab === "staff-walkin" ? (
            /* ROLE STAFF VIEW */
            <StaffWalkinPage
              onManageProfiles={() => selectTab("patient-records")}
              onSelectPatientForBooking={(selectedPatient) => {
                setWalkinPatient(selectedPatient);
                setSelection(null);
                selectTab("doctors");
              }}
            />
          ) : tab === "doctor-dashboard" ? (
            /* ROLE DOCTOR VIEW */
            <DoctorDashboardPage onOpenPatient={openPatient} />
          ) : tab === "patient-records" ? (
            /* PATIENT RECORDS VIEW */
            <PatientModule session={patientSession} onBeforeLeave={registerPatientGuard} />
          ) : tab === "patient-onboarding" ? (
            /* PATIENT ONBOARDING VIEW */
            <PatientOnboardingPage onComplete={() => selectTab("doctors")} />
          ) : tab === "doctors" ? (
            /* DOCTORS DIRECTORY */
            <DoctorDirectory
              onPick={(doctor, slot) => {
                setSelection({ doctor, slot });
                selectTab("booking");
              }}
            />
          ) : tab === "booking" ? (
            /* BOOKING VIEW */
            <Booking
              key={patientId || "choose-patient"}
              role={user?.role}
              actorSubject={actorSubject}
              patientId={patientId}
              selection={selection}
              onSchedule={() => selectTab("doctors")}
              onHistory={() => selectTab("history")}
            />
          ) : (
            /* APPOINTMENT HISTORY */
            <AppointmentHistory
              key={patientId || "assigned-patients"}
              role={user?.role}
              actorSubject={actorSubject}
              patientId={patientId}
              onSchedule={() => selectTab("doctors")}
              onOpenPatient={openPatient}
              onRequireProfile={() => selectTab("patient-onboarding")}
            />
          )}

          <footer className="footer">
            <span>© 2026 FClinic Healthcare</span>
            <span>Hệ thống Quản lý Phòng khám & Đặt lịch đa vai trò.</span>
          </footer>
        </main>
      </div>

      <NotificationDrawer
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
      <SessionApp />
    </AuthProvider>
  );
}

function SessionApp() {
  const { user } = useAuth();
  return <MainApp key={user ? `${user.id}:${user.role}:${user.patientId}:${user.doctorId}` : "signed-out"} />;
}
