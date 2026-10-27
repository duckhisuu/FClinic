import axios from "axios";

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
  timeout: 10000,
  withCredentials: true,
});
apiClient.interceptors.request.use((config) => {
  config.headers["X-Request-Id"] = crypto.randomUUID();
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
  (error) => {
    if (axios.isCancel(error)) return Promise.reject(error);
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
};
export const patientApi = {
  search: (params, signal) =>
    apiClient.get("/api/v2/patients", { params, signal }),
  get: (id, signal) => apiClient.get(`/api/v2/patients/${id}`, { signal }),
};
