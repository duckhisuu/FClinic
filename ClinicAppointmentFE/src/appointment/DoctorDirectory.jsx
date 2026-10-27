import React, { useEffect, useState } from "react";
import {
  Search,
  Stethoscope,
  Clock3,
  MapPin,
  ChevronLeft,
  ChevronRight,
  CalendarDays,
} from "lucide-react";
import { appointmentApi, doctorApi, ApiError } from "./api";
import { today, dayText, money, shift, ErrorNotice, Busy, Empty } from "./ui";
function CalendarPicker({ value, onChange }) {
  const [mode, setMode] = useState("week");
  const d = new Date(`${value}T12:00:00Z`),
    first =
      mode === "week"
        ? shift(value, -((d.getUTCDay() + 6) % 7))
        : `${value.slice(0, 7)}-01`;
  const days =
    mode === "week"
      ? 7
      : new Date(
          Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 0),
        ).getUTCDate();
  const move = (n) => {
    if (mode === "week") onChange(shift(value, n * 7));
    else {
      const m = new Date(`${value.slice(0, 7)}-01T12:00:00Z`);
      m.setUTCMonth(m.getUTCMonth() + n);
      onChange(m.toISOString().slice(0, 10));
    }
  };
  return (
    <div className="calendar-box">
      <div className="section-row">
        <div className="segmented">
          <button
            className={mode === "week" ? "active" : ""}
            onClick={() => setMode("week")}
          >
            Tuần
          </button>
          <button
            className={mode === "month" ? "active" : ""}
            onClick={() => setMode("month")}
          >
            Tháng
          </button>
        </div>
        <div className="calendar-nav">
          <button aria-label="Khoảng trước" onClick={() => move(-1)}>
            <ChevronLeft size={18} />
          </button>
          <strong>
            Tháng {Number(value.slice(5, 7))} / {value.slice(0, 4)}
          </strong>
          <button aria-label="Khoảng tiếp" onClick={() => move(1)}>
            <ChevronRight size={18} />
          </button>
        </div>
      </div>
      <div className={`calendar-grid ${mode}`}>
        {Array.from({ length: days }, (_, i) => {
          const date = shift(first, i),
            day = new Date(`${date}T12:00:00Z`);
          return (
            <button
              key={date}
              style={
                mode === "month" && i === 0
                  ? {
                      gridColumnStart:
                        ((new Date(`${first}T12:00:00Z`).getUTCDay() + 6) % 7) +
                        1,
                    }
                  : {}
              }
              disabled={date < today()}
              className={`${date === value ? "selected" : ""} ${date === today() ? "today" : ""}`}
              onClick={() => onChange(date)}
            >
              <small>
                {["CN", "T2", "T3", "T4", "T5", "T6", "T7"][day.getUTCDay()]}
              </small>
              <b>{day.getUTCDate()}</b>
              {date === today() && <span>Hôm nay</span>}
            </button>
          );
        })}
      </div>
      <label className="date-jump">
        Chọn ngày cụ thể
        <input
          aria-label="Ngày khám"
          type="date"
          min={today()}
          value={value}
          onChange={(e) => e.target.value && onChange(e.target.value)}
        />
      </label>
    </div>
  );
}
export function SlotGrid({ doctorId, date, onPick, excludeId }) {
  const [data, setData] = useState(null),
    [error, setError] = useState(null),
    [loading, setLoading] = useState(true),
    [revision, setRevision] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setData(null);
    setError(null);
    setLoading(true);
    appointmentApi
      .available(doctorId, date, controller.signal)
      .then((r) => {
        if (
          !Array.isArray(r.slots) ||
          r.doctorId !== doctorId ||
          r.date !== date
        )
          throw new ApiError(
            "Lịch trả về không hợp lệ.",
            503,
            "DEPENDENCY_UNAVAILABLE",
          );
        setData(r);
      })
      .catch((e) => {
        if (!controller.signal.aborted) setError(e);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [doctorId, date, revision]);
  if (loading) return <Busy text="Đang kiểm tra khung giờ còn trống…" />;
  return (
    <>
      <ErrorNotice error={error} onRetry={() => setRevision((r) => r + 1)} />
      {data &&
        (data.slots.length ? (
          <>
            <div className="slot-grid">
              {data.slots.map((s) => (
                <button
                  type="button"
                  key={s.id}
                  disabled={!s.available || s.id === excludeId}
                  className="slot"
                  onClick={() => onPick(s)}
                >
                  <Clock3 size={16} />
                  <b>{s.startTime.slice(0, 5)}</b>
                  <span>– {s.endTime.slice(0, 5)}</span>
                  <small>
                    {s.id === excludeId
                      ? "Lịch hiện tại"
                      : s.available
                        ? "Còn trống"
                        : "Không thể đặt"}
                  </small>
                </button>
              ))}
            </div>
            <p className="hint">
              Giờ Việt Nam · Mỗi lượt khám 60 phút. Khung giờ được xác nhận khi
              đặt lịch thành công.
            </p>
          </>
        ) : (
          <Empty title="Chưa có giờ khám phù hợp">
            Bác sĩ chưa công bố khung giờ 60 phút cho ngày này. Hãy chọn ngày
            khác.
          </Empty>
        ))}
    </>
  );
}
export function DoctorDirectory({ onPick }) {
  const [doctors, setDoctors] = useState([]),
    [loading, setLoading] = useState(true),
    [error, setError] = useState(null),
    [revision, setRevision] = useState(0),
    [selected, setSelected] = useState(null),
    [query, setQuery] = useState(""),
    [specialty, setSpecialty] = useState(""),
    [date, setDate] = useState(today());
  useEffect(() => {
    const c = new AbortController();
    setLoading(true);
    setError(null);
    doctorApi
      .list(c.signal)
      .then((d) => {
        if (!Array.isArray(d))
          throw new ApiError(
            "Danh sách bác sĩ không hợp lệ.",
            503,
            "DEPENDENCY_UNAVAILABLE",
          );
        setDoctors(d.filter((x) => x.active));
      })
      .catch((e) => {
        if (!c.signal.aborted) setError(e);
      })
      .finally(() => {
        if (!c.signal.aborted) setLoading(false);
      });
    return () => c.abort();
  }, [revision]);
  const shown = doctors.filter(
    (d) =>
      (!specialty || d.specialty === specialty) &&
      `${d.name} ${d.department}`
        .toLocaleLowerCase("vi")
        .includes(query.toLocaleLowerCase("vi")),
  );
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">CHỌN BÁC SĨ & GIỜ KHÁM</span>
          <h1>Chủ động lịch khám của bạn</h1>
          <p>Tìm bác sĩ phù hợp và chọn khung giờ thuận tiện.</p>
        </div>
        <span className="timezone">
          <Clock3 size={15} />
          Asia/Saigon
        </span>
      </div>
      <div className="directory-layout">
        <section>
          <div className="filter-bar">
            <div className="search-field">
              <Search size={18} />
              <input
                aria-label="Tìm bác sĩ"
                placeholder="Tên bác sĩ, khoa…"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />
            </div>
            <select
              aria-label="Chuyên khoa"
              value={specialty}
              onChange={(e) => setSpecialty(e.target.value)}
            >
              <option value="">Tất cả chuyên khoa</option>
              {[...new Set(doctors.map((d) => d.specialty))].map((s) => (
                <option key={s}>{s}</option>
              ))}
            </select>
          </div>
          <ErrorNotice
            error={error}
            onRetry={() => setRevision((r) => r + 1)}
          />
          {loading ? (
            <Busy />
          ) : (
            <div className="doctor-list">
              {shown.map((d) => (
                <button
                  key={d.id}
                  className={`doctor-card ${selected?.id === d.id ? "chosen" : ""}`}
                  onClick={() => setSelected(d)}
                >
                  <div className="avatar">
                    <Stethoscope size={27} />
                  </div>
                  <div className="doctor-info">
                    <span className="specialty">{d.specialty}</span>
                    <h3>{d.name}</h3>
                    <p>{d.qualification || d.department}</p>
                    <small>
                      <MapPin size={13} />
                      {d.roomNumber || d.department}
                    </small>
                  </div>
                  <div className="doctor-price">
                    {d.consultationFee != null && (
                      <>
                        <b>{money(d.consultationFee)}</b>
                        <small>/ lượt khám</small>
                      </>
                    )}
                    <span className="choose-text">
                      {selected?.id === d.id ? "Đã chọn" : "Xem lịch"}
                      <ChevronRight size={15} />
                    </span>
                  </div>
                </button>
              ))}
              {!shown.length && !error && (
                <Empty title="Không tìm thấy bác sĩ">
                  Thử thay đổi tên hoặc chuyên khoa.
                </Empty>
              )}
            </div>
          )}
        </section>
        <section className="panel schedule-panel">
          <div className="panel-title">
            <CalendarDays size={21} />
            <h2>Khung giờ khám</h2>
          </div>
          <CalendarPicker value={date} onChange={setDate} />
          <div className="schedule-date">
            <strong>{dayText(date)}</strong>
            <small>
              {selected ? selected.name : "Chọn bác sĩ để xem giờ còn trống"}
            </small>
          </div>
          {selected ? (
            <SlotGrid
              doctorId={selected.id}
              date={date}
              onPick={(slot) => onPick(selected, slot)}
            />
          ) : (
            <Empty title="Bắt đầu từ một bác sĩ">
              Chọn bác sĩ ở danh sách bên cạnh để xem lịch.
            </Empty>
          )}
        </section>
      </div>
    </>
  );
}
