import React, { useRef, useState } from "react";
import { AlertCircle, CalendarDays, Clock3, Loader2 } from "lucide-react";
import { ApiError } from "./api";
export const today = () =>
  new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Saigon",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
export const money = (n) =>
  new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(
    n,
  );
export const dayText = (d) =>
  new Intl.DateTimeFormat("vi-VN", {
    timeZone: "Asia/Saigon",
    weekday: "long",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(new Date(`${d}T12:00:00Z`));
export const timeText = (i) =>
  new Intl.DateTimeFormat("vi-VN", {
    timeZone: "Asia/Saigon",
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(i));
export const shift = (d, n) => {
  const v = new Date(`${d}T12:00:00Z`);
  v.setUTCDate(v.getUTCDate() + n);
  return v.toISOString().slice(0, 10);
};
export const statuses = {
  CONFIRMED: "Đã xác nhận",
  CANCELLED: "Đã hủy",
  COMPLETED: "Đã khám",
  NO_SHOW: "Không đến khám",
};
export function ErrorNotice({ error, onRetry }) {
  return (
    error && (
      <div role="alert" className="notice error">
        <AlertCircle size={19} />
        <div>
          <strong>{error.message}</strong>
          {error.code && (
            <small>
              {error.code}
              {error.requestId ? ` · Mã tra cứu: ${error.requestId}` : ""}
            </small>
          )}
          {error.fieldErrors?.map((f, i) => (
            <small key={i}>
              {f.field}: {f.message}
            </small>
          ))}
        </div>
        {onRetry && (
          <button type="button" className="text-button" onClick={onRetry}>
            Thử lại
          </button>
        )}
      </div>
    )
  );
}
export const Busy = ({ text = "Đang tải dữ liệu…" }) => (
  <div className="empty loading" role="status">
    <Loader2 className="spin" size={25} />
    <p>{text}</p>
  </div>
);
export const Empty = ({ title, children }) => (
  <div className="empty">
    <CalendarDays size={38} />
    <h3>{title}</h3>
    <div>{children}</div>
  </div>
);
export const PendingNotice = ({ pending }) =>
  pending && (
    <div className="notice warning">
      <Clock3 size={18} />
      <div>
        Chưa rõ kết quả yêu cầu. Thử lại sẽ giữ nguyên mã và nội dung, không tạo
        lần đặt mới. Bạn có thể đối soát trong danh sách lịch hẹn.
      </div>
    </div>
  );
export function clearPending() {
  for (const k of Object.keys(sessionStorage))
    if (k.startsWith("fclinic.pending.")) sessionStorage.removeItem(k);
}
export function useMutation(subject, operation) {
  const storage = `fclinic.pending.${subject}.${operation}`;
  const [pending, setPending] = useState(() => {
    try {
      return JSON.parse(sessionStorage.getItem(storage) || "null");
    } catch {
      return null;
    }
  });
  const [busy, setBusy] = useState(false),
    [error, setError] = useState(null);
  const running = useRef(false);
  const run = async (payload, execute, success) => {
    if (running.current) return;
    const command = pending || { key: crypto.randomUUID(), payload };
    if (
      pending &&
      JSON.stringify(pending.payload) !== JSON.stringify(payload)
    ) {
      setError(
        new ApiError(
          "Hãy thử lại nguyên yêu cầu trước đó hoặc đối soát lịch hẹn.",
          409,
          "PENDING_REQUEST",
        ),
      );
      return;
    }
    running.current = true;
    setBusy(true);
    setError(null);
    setPending(command);
    try {
      sessionStorage.setItem(storage, JSON.stringify(command));
    } catch {
      setPending(null);
      setError(
        new ApiError(
          "Không thể lưu mã yêu cầu trong trình duyệt. Yêu cầu chưa được gửi.",
          400,
          "BROWSER_STORAGE_UNAVAILABLE",
        ),
      );
      running.current = false;
      setBusy(false);
      return;
    }
    try {
      const result = await execute(command.payload, command.key);
      sessionStorage.removeItem(storage);
      setPending(null);
      success(result);
    } catch (e) {
      setError(e);
      if (
        [400, 403, 404, 409].includes(e.status) &&
        !["OPERATION_IN_PROGRESS", "IDEMPOTENCY_CONFLICT"].includes(e.code)
      ) {
        sessionStorage.removeItem(storage);
        setPending(null);
      }
    } finally {
      running.current = false;
      setBusy(false);
    }
  };
  return { pending, busy, error, run };
}
