import React, { useState } from "react";
import {
  Check,
  ChevronLeft,
  CalendarDays,
  Clock3,
  MapPin,
  ArrowRight,
  Stethoscope,
  CalendarCheck2,
  Loader2,
} from "lucide-react";
import { appointmentApi } from "./api";
import { PatientSelection } from "./PatientSelection";
import {
  money,
  dayText,
  Empty,
  ErrorNotice,
  PendingNotice,
  useMutation,
} from "./ui";
export function Booking({
  actorSubject,
  patientId,
  selection,
  onSchedule,
  onHistory,
}) {
  const [patient, setPatient] = useState(null),
    [reason, setReason] = useState(""),
    [result, setResult] = useState(null),
    [readError, setReadError] = useState(null),
    [verified, setVerified] = useState(false);
  const mutation = useMutation(actorSubject, "CREATE");
  const confirm = (e) => {
    e?.preventDefault();
    const body = mutation.pending?.payload || {
      patientId: patient?.id,
      doctorId: selection.doctor.id,
      slotId: selection.slot.id,
      reasonForVisit: reason.trim() || null,
    };
    mutation.run(body, appointmentApi.book, (a) => {
      setResult(a);
      setVerified(false);
    });
  };
  if (result)
    return (
      <section className="panel confirmation">
        <div className="confirmation-icon">
          <Check size={31} />
        </div>
        <span className="eyebrow">ĐÃ LƯU LỊCH HẸN</span>
        <h1>Hẹn gặp bạn tại phòng khám</h1>
        <p>
          Lịch hẹn đã được máy chủ xác nhận. Email thông báo được xử lý riêng.
        </p>
        <div className="booking-code">{result.bookingCode}</div>
        <h3>{result.doctorName}</h3>
        <p>
          {dayText(result.appointmentDate)} · {result.startTime.slice(0, 5)}–
          {result.endTime.slice(0, 5)}
        </p>
        <p>
          {result.patientName} · {money(result.consultationFee)}
        </p>
        <ErrorNotice error={readError} />
        {verified && (
          <div className="notice success">
            <Check size={17} />
            Đã đọc lại lịch hẹn từ cơ sở dữ liệu qua API.
          </div>
        )}
        <div className="button-row">
          <button
            className="button secondary"
            onClick={async () => {
              try {
                setResult(await appointmentApi.detail(result.id));
                setVerified(true);
                setReadError(null);
              } catch (e) {
                setReadError(e);
              }
            }}
          >
            Kiểm tra lại lịch đã lưu
          </button>
          <button className="button primary" onClick={onHistory}>
            Xem lịch hẹn
            <ArrowRight size={17} />
          </button>
        </div>
      </section>
    );
  if (mutation.pending || !selection)
    return (
      <section className="panel">
        {mutation.pending ? (
          <>
            <h2>
              {mutation.busy
                ? "Đang xác nhận lịch hẹn…"
                : "Kiểm tra yêu cầu đặt lịch đang chờ"}
            </h2>
            <p>
              Bác sĩ #{mutation.pending.payload.doctorId} · khung giờ #
              {mutation.pending.payload.slotId}
            </p>
            {!mutation.busy && <PendingNotice pending={mutation.pending} />}
            <ErrorNotice error={mutation.error} />
            <button
              className="button primary"
              disabled={mutation.busy}
              onClick={() => confirm()}
            >
              Thử lại nguyên yêu cầu đặt lịch
            </button>
            <button className="button secondary" onClick={onHistory}>
              Đối soát lịch hẹn
            </button>
          </>
        ) : (
          <>
            <Empty title="Bạn chưa chọn giờ khám">
              Chọn bác sĩ và khung giờ trước khi đặt lịch.
            </Empty>
            <button className="button primary center" onClick={onSchedule}>
              Chọn bác sĩ & giờ khám
              <ArrowRight size={17} />
            </button>
          </>
        )}
      </section>
    );
  const { doctor, slot } = selection;
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">XÁC NHẬN THÔNG TIN</span>
          <h1>Hoàn tất lịch hẹn</h1>
          <p>Kiểm tra người đến khám và giờ đã chọn.</p>
        </div>
        <button className="button secondary" onClick={onSchedule}>
          <ChevronLeft size={17} />
          Chọn lại giờ
        </button>
      </div>
      <div className="booking-layout">
        <div>
          <PatientSelection
            patientId={patientId}
            selectedPatient={patient}
            onSelect={setPatient}
          />
          <form className="panel reason-form" onSubmit={confirm}>
            <label htmlFor="reason">
              <h2>
                Lý do khám <small>(không bắt buộc)</small>
              </h2>
            </label>
            <textarea
              id="reason"
              value={mutation.pending?.payload.reasonForVisit ?? reason}
              disabled={!!mutation.pending}
              onChange={(e) => setReason(e.target.value)}
              rows={4}
              placeholder="Nội dung bạn muốn trao đổi với bác sĩ…"
            />
            <p className="hint">
              Thông tin này không được đưa vào email thông báo lịch hẹn.
            </p>
            <PendingNotice pending={mutation.pending} />
            <ErrorNotice error={mutation.error} />
            <div className="button-row">
              <button type="button" className="text-button" onClick={onHistory}>
                Đối soát lịch hẹn
              </button>
              <button
                className="button primary"
                disabled={
                  mutation.busy ||
                  (!mutation.pending && patient?.status !== "ACTIVE")
                }
              >
                {mutation.busy ? (
                  <Loader2 size={18} className="spin" />
                ) : (
                  <CalendarCheck2 size={18} />
                )}{" "}
                {mutation.pending
                  ? "Thử lại cùng yêu cầu"
                  : "Xác nhận đặt lịch"}
              </button>
            </div>
          </form>
        </div>
        <aside className="panel booking-summary">
          <span className="eyebrow">LỊCH KHÁM ĐÃ CHỌN</span>
          <div className="avatar">
            <Stethoscope size={29} />
          </div>
          <h2>{doctor.name}</h2>
          <p>{doctor.specialty}</p>
          <hr />
          <div>
            <CalendarDays size={18} />
            <span>{dayText(slot.slotDate)}</span>
          </div>
          <div>
            <Clock3 size={18} />
            <span>
              {slot.startTime.slice(0, 5)}–{slot.endTime.slice(0, 5)} · 60 phút
            </span>
          </div>
          <div>
            <MapPin size={18} />
            <span>{doctor.roomNumber || doctor.department}</span>
          </div>
          <hr />
          <div className="fee">
            <span>Phí tư vấn tham khảo</span>
            <b>{money(doctor.consultationFee)}</b>
          </div>
          <p className="hint">
            Thông tin và phí được máy chủ xác minh lại khi đặt lịch.
          </p>
        </aside>
      </div>
    </>
  );
}
