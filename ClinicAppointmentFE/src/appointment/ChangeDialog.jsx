import React, { useState } from "react";
import { Check, X, Loader2 } from "lucide-react";
import { appointmentApi } from "./api";
import { SlotGrid } from "./DoctorDirectory";
import { today, ErrorNotice, PendingNotice, useMutation } from "./ui";
export function ChangeDialog({
  kind,
  appointment,
  actorSubject,
  onClose,
  onChanged,
}) {
  const [date, setDate] = useState(appointment.appointmentDate),
    [slot, setSlot] = useState(null),
    [reason, setReason] = useState("");
  const mutation = useMutation(actorSubject, `${kind}:${appointment.id}`);
  const submit = (e) => {
    e.preventDefault();
    const payload = mutation.pending?.payload || {
      version: appointment.version,
      ...(kind === "reschedule" ? { slotId: slot.id } : {}),
      reason: reason.trim() || null,
    };
    mutation.run(
      payload,
      (body, key) => appointmentApi[kind](appointment.id, body, key),
      onChanged,
    );
  };
  return (
    <div className="modal-backdrop">
      <section
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="change-title"
      >
        <div className="section-row">
          <h2 id="change-title">
            {kind === "cancel" ? "Hủy lịch hẹn" : "Chọn giờ khám mới"}
          </h2>
          <button
            className="icon-button"
            aria-label="Đóng"
            disabled={mutation.busy}
            onClick={onClose}
          >
            <X size={21} />
          </button>
        </div>
        <p>
          {appointment.bookingCode} · {appointment.doctorName}
        </p>
        <form onSubmit={submit}>
          {kind === "reschedule" && !mutation.pending && (
            <>
              <label>
                Ngày khám mới
                <input
                  type="date"
                  min={today()}
                  value={date}
                  onChange={(e) => {
                    setDate(e.target.value);
                    setSlot(null);
                  }}
                  required
                />
              </label>
              <SlotGrid
                doctorId={appointment.doctorId}
                date={date}
                excludeId={
                  date === appointment.appointmentDate
                    ? appointment.slotId
                    : null
                }
                onPick={setSlot}
              />
              {slot && (
                <div className="notice success">
                  <Check size={17} />
                  Đã chọn {slot.startTime.slice(0, 5)}–
                  {slot.endTime.slice(0, 5)} ngày {slot.slotDate}
                </div>
              )}
              <p className="hint">
                Đổi lịch giữ nguyên bác sĩ, người khám và phí đã xác nhận.
              </p>
            </>
          )}
          <label>
            Lý do (không bắt buộc)
            <textarea
              maxLength={500}
              rows={3}
              disabled={!!mutation.pending}
              value={mutation.pending?.payload.reason ?? reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </label>
          <PendingNotice pending={mutation.pending} />
          <ErrorNotice error={mutation.error} />
          {mutation.error?.code === "VERSION_CONFLICT" && (
            <button type="button" className="text-button" onClick={onClose}>
              Đóng và tải lại lịch mới nhất trước khi thay đổi
            </button>
          )}
          <div className="button-row">
            <button
              type="button"
              className="button secondary"
              disabled={mutation.busy}
              onClick={onClose}
            >
              Đóng
            </button>
            <button
              className={`button ${kind === "cancel" ? "danger" : "primary"}`}
              disabled={
                mutation.busy ||
                (kind === "reschedule" && !slot && !mutation.pending)
              }
            >
              {mutation.busy && <Loader2 size={16} className="spin" />}
              {mutation.pending
                ? "Thử lại cùng yêu cầu"
                : kind === "cancel"
                  ? "Xác nhận hủy lịch"
                  : "Xác nhận đổi lịch"}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}
