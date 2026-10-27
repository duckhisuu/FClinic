import React, { useState, useEffect, useRef } from "react";
import { RefreshCw, History, ChevronLeft, ChevronRight, X } from "lucide-react";
import { appointmentApi, ApiError } from "./api";
import { ChangeDialog } from "./ChangeDialog";
import {
  money,
  dayText,
  timeText,
  statuses,
  Busy,
  Empty,
  ErrorNotice,
} from "./ui";
const actions = {
  CREATED: "Đặt lịch",
  CANCELLED: "Hủy lịch",
  RESCHEDULED: "Đổi giờ khám",
  COMPLETED: "Hoàn thành",
  NO_SHOW: "Không đến khám",
};
export function AppointmentHistory({ actorSubject, patientId, onSchedule }) {
  const [data, setData] = useState(null),
    [error, setError] = useState(null),
    [loading, setLoading] = useState(true),
    [page, setPage] = useState(0),
    [status, setStatus] = useState(""),
    [revision, setRevision] = useState(0),
    [change, setChange] = useState(null),
    [details, setDetails] = useState(null),
    [detailsError, setDetailsError] = useState(null);
  useEffect(() => {
    const c = new AbortController();
    setData(null);
    setError(null);
    setLoading(true);
    appointmentApi
      .list(
        {
          page,
          size: 10,
          ...(patientId ? { patientId } : {}),
          ...(status ? { status } : {}),
        },
        c.signal,
      )
      .then((p) => {
        if (!Array.isArray(p.content))
          throw new ApiError(
            "Danh sách lịch hẹn không hợp lệ.",
            503,
            "DEPENDENCY_UNAVAILABLE",
          );
        setData(p);
      })
      .catch((e) => {
        if (!c.signal.aborted) setError(e);
      })
      .finally(() => {
        if (!c.signal.aborted) setLoading(false);
      });
    return () => c.abort();
  }, [page, status, revision, patientId]);
  const detailRequest = useRef(0);
  const view = async (a) => {
    const request = ++detailRequest.current;
    setDetailsError(null);
    setDetails({ appointment: a, history: null });
    try {
      const [current, history] = await Promise.all([
        appointmentApi.detail(a.id),
        appointmentApi.history(a.id),
      ]);
      if (request === detailRequest.current)
        setDetails({ appointment: current, history });
    } catch (e) {
      if (request === detailRequest.current) setDetailsError(e);
    }
  };
  const canChange = (a) =>
    a.status === "CONFIRMED" &&
    new Date(`${a.appointmentDate}T${a.startTime}+07:00`).getTime() >
      Date.now();
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">THEO DÕI & QUẢN LÝ</span>
          <h1>Lịch hẹn phòng khám</h1>
          <p>Lịch đặt khám được quản lý riêng với hồ sơ và kết quả khám.</p>
        </div>
        <button
          className="button secondary"
          onClick={() => setRevision((r) => r + 1)}
        >
          <RefreshCw size={16} />
          Tải lại
        </button>
      </div>
      <section className="panel">
        <div className="section-row">
          <strong>
            {data
              ? `${data.totalElements} lịch hẹn phù hợp`
              : "Danh sách lịch hẹn"}
          </strong>
          <select
            aria-label="Lọc trạng thái"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
          >
            <option value="">Tất cả trạng thái</option>
            {Object.entries(statuses).map(([v, l]) => (
              <option key={v} value={v}>
                {l}
              </option>
            ))}
          </select>
        </div>
        <ErrorNotice error={error} onRetry={() => setRevision((r) => r + 1)} />
        {loading ? (
          <Busy />
        ) : !data?.content.length ? (
          !error && (
            <Empty title="Chưa có lịch hẹn">
              Khi đặt khám thành công, lịch hẹn sẽ được hiển thị tại đây.
              <button className="button primary" onClick={onSchedule}>
                Chọn giờ khám
              </button>
            </Empty>
          )
        ) : (
          <div className="appointment-list">
            {data.content.map((a) => (
              <article className="appointment-card" key={a.id}>
                <div className="appointment-date">
                  <b>{Number(a.appointmentDate.slice(-2))}</b>
                  <span>THÁNG {a.appointmentDate.slice(5, 7)}</span>
                  <small>{a.startTime.slice(0, 5)}</small>
                </div>
                <div className="appointment-info">
                  <div className="section-row">
                    <small className="code">{a.bookingCode}</small>
                    <span className={`badge ${a.status}`}>
                      {statuses[a.status]}
                    </span>
                  </div>
                  <h3>{a.doctorName}</h3>
                  <p>
                    {a.patientName} · {a.doctorSpecialty}
                  </p>
                  <small>
                    {dayText(a.appointmentDate)} · {a.startTime.slice(0, 5)}–
                    {a.endTime.slice(0, 5)} · {money(a.consultationFee)}
                  </small>
                  <div className="appointment-actions">
                    <button className="text-button" onClick={() => view(a)}>
                      <History size={15} />
                      Chi tiết & lịch sử
                    </button>
                    {canChange(a) && (
                      <>
                        <button
                          className="text-button"
                          onClick={() =>
                            setChange({ kind: "reschedule", appointment: a })
                          }
                        >
                          Đổi giờ khám
                        </button>
                        <button
                          className="text-button destructive"
                          onClick={() =>
                            setChange({ kind: "cancel", appointment: a })
                          }
                        >
                          Hủy lịch
                        </button>
                      </>
                    )}
                  </div>
                </div>
              </article>
            ))}
          </div>
        )}
        {data?.totalPages > 1 && (
          <div className="pagination">
            <button
              className="button secondary"
              disabled={page === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              <ChevronLeft size={16} />
              Trước
            </button>
            <span>
              Trang {page + 1} / {data.totalPages}
            </span>
            <button
              className="button secondary"
              disabled={page + 1 >= data.totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Tiếp
              <ChevronRight size={16} />
            </button>
          </div>
        )}
      </section>
      {change && (
        <ChangeDialog
          {...change}
          actorSubject={actorSubject}
          onClose={() => {
            setChange(null);
            setRevision((r) => r + 1);
          }}
          onChanged={() => {
            setChange(null);
            setRevision((r) => r + 1);
          }}
        />
      )}
      {details && (
        <div className="modal-backdrop">
          <section
            className="modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="history-title"
          >
            <div className="section-row">
              <h2 id="history-title">Lịch sử thay đổi</h2>
              <button
                className="icon-button"
                aria-label="Đóng chi tiết"
                onClick={() => {
                  detailRequest.current++;
                  setDetails(null);
                }}
              >
                <X size={21} />
              </button>
            </div>
            <p className="code">{details.appointment.bookingCode}</p>
            <p>
              {details.appointment.patientName} ·{" "}
              {details.appointment.doctorName}
            </p>
            {details.appointment.reasonForVisit && (
              <div className="reason-detail">
                <small>Lý do khám</small>
                <p>{details.appointment.reasonForVisit}</p>
              </div>
            )}
            <ErrorNotice error={detailsError} />
            {!details.history && !detailsError ? (
              <Busy />
            ) : (
              details.history?.map((h) => (
                <div className="history-entry" key={h.id}>
                  <span className="history-dot" />
                  <div>
                    <strong>{actions[h.action] || h.action}</strong>
                    <small>
                      {timeText(h.createdAt)} · {h.actorRole} · phiên bản{" "}
                      {h.version}
                    </small>
                    <p>
                      {h.oldDate
                        ? `${h.oldDate} ${h.oldStartTime?.slice(0, 5)} → `
                        : ""}
                      {h.newDate} {h.newStartTime?.slice(0, 5)}
                    </p>
                    {h.reason && <p>{h.reason}</p>}
                  </div>
                </div>
              ))
            )}
          </section>
        </div>
      )}
    </>
  );
}
