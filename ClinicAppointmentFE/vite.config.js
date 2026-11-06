import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
export default defineConfig(({ mode }) => {
  const env = { ...loadEnv(mode, process.cwd(), ""), ...process.env };
  return {
    plugins: [react()],
    server: {
      host: "127.0.0.1",
      port: 5173,
      proxy: {
        "/api/v2/appointments": {
          target: env.VITE_APPOINTMENT_PROXY_TARGET || "http://localhost:8083",
        },
        "/api/v1/appointments": {
          target: env.VITE_APPOINTMENT_PROXY_TARGET || "http://localhost:8083",
        },
        "/api/v2/patients": {
          target: env.VITE_PATIENT_PROXY_TARGET || "http://localhost:8081",
        },
        "/api/v1/patients": {
          target: env.VITE_PATIENT_PROXY_TARGET || "http://localhost:8081",
        },
        "/api/v1/doctors": {
          target: env.VITE_DOCTOR_PROXY_TARGET || "http://localhost:8082",
        },
        "/api/v1/notifications": {
          target: env.VITE_NOTIFICATION_PROXY_TARGET || "http://localhost:8084",
        },
        "/api/v1/admin/notifications": {
          target: env.VITE_NOTIFICATION_PROXY_TARGET || "http://localhost:8084",
        },
      },
    },
  };
});
