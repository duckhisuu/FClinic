import axios from "axios";
import { refreshSession } from "../services/session";

export class ApiError extends Error {
  constructor(
    message,
    status = 0,
    code = "NETWORK_ERROR",
    fieldErrors = [],
    requestId = "",
  ) {
    super(message);
    Object.assign(this, { status, code, fieldErrors, requestId });
  }
}
// Appointment HTTP adapter only; authentication stays in the existing host application.
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_GATEWAY_URL || "",
  timeout: 60000,
  withCredentials: true,
});
apiClient.interceptors.request.use((config) => {
  config.headers["X-Request-Id"] = crypto.randomUUID();
  const token = localStorage.getItem("fclinic_token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  config._sessionUserId ??= JSON.parse(localStorage.getItem("fclinic_user") || "null")?.id;
  config._sessionId ??= localStorage.getItem('fclinic_session_id');
  return config;
});
apiClient.interceptors.response.use(
  (response) => {
    const body = response.data;
    if (
      !body ||
      body.status !== response.status ||
      !Object.hasOwn(body, "data") ||
      typeof body.message !== "string"
    )
      throw new ApiError(
        "Dịch vụ trả dữ liệu không đúng hợp đồng.",
        503,
        "DEPENDENCY_UNAVAILABLE",
      );
    return body.data;
  },
  async (error) => {
    if (axios.isCancel(error)) return Promise.reject(error);
    if (error.response?.status === 401 && !error.config?._sessionRetried && !/\/(login|register|refresh|logout|google|oauth2\/token)$/.test(error.config?.url || '')) {
      const originalToken = error.config?.headers?.Authorization;
      const sameUser = () => error.config?._sessionUserId && error.config._sessionUserId === JSON.parse(localStorage.getItem("fclinic_user") || "null")?.id
        && error.config._sessionId === localStorage.getItem('fclinic_session_id');
      const alreadyRefreshed = () => originalToken !== `Bearer ${localStorage.getItem("fclinic_token")}`;
      if (sameUser() && (alreadyRefreshed() || await refreshSession(originalToken)) && sameUser()) {
        error.config._sessionRetried = true;
        return apiClient.request(error.config);
      }
    }
    const body = error.response?.data;
    return Promise.reject(
      new ApiError(
        body?.message ||
          "Chưa nhận được kết quả từ máy chủ. Vui lòng kiểm tra kết nối.",
        error.response?.status || 0,
        body?.code || (error.response ? "HTTP_ERROR" : "NETWORK_ERROR"),
        body?.fieldErrors || [],
        error.response?.headers?.["x-request-id"] || "",
      ),
    );
  },
);
const mutation = (method, url, data, key) =>
  apiClient.request({ method, url, data, headers: { "Idempotency-Key": key } });
export const appointmentApi = {
  list: (params, signal) =>
    apiClient.get("/api/v2/appointments", { params, signal }),
  detail: (id) => apiClient.get(`/api/v2/appointments/${id}`),
  history: (id) => apiClient.get(`/api/v2/appointments/${id}/history`),
  available: (doctorId, date, signal) =>
    apiClient.get("/api/v2/appointments/available-slots", {
      params: { doctorId, date },
      signal,
    }),
  book: (body, key) => mutation("post", "/api/v2/appointments", body, key),
  cancel: (id, body, key) =>
    mutation("put", `/api/v2/appointments/${id}/cancel`, body, key),
  reschedule: (id, body, key) =>
    mutation("put", `/api/v2/appointments/${id}/reschedule`, body, key),
};
export const doctorApi = {
  list: (signal) => apiClient.get("/api/v1/doctors", { signal }),
  getDoctors: (specialty) => apiClient.get("/api/v1/doctors", { params: { specialty } }),
  getDoctorSlots: (id, date) => apiClient.get(`/api/v1/doctors/${id}/slots`, { params: { date } }),
  getDoctorSchedule: (id, period, anchorDate, signal) => apiClient.get(`/api/v1/doctors/${id}/schedule`, { params: { period, anchorDate }, signal }),
  createDoctorSlot: (id, body) => apiClient.post(`/api/v1/doctors/${id}/slots`, body),
};
export const patientApi = {
  search: (params, signal) =>
    apiClient.get("/api/v3/patients", { params, signal }),
  get: (id, signal) => apiClient.get(`/api/v3/patients/${id}`, { signal }),
  create: (body) => apiClient.post("/api/v3/patients", body),
};
