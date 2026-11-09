import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig(({ mode }) => {
  const env = { ...loadEnv(mode, process.cwd(), ''), ...process.env };
  return {
    plugins: [react()],
    server: {
      host: '127.0.0.1',
      port: 5173,
      strictPort: true,
      proxy: {
        '/api/v1/auth': {
          target: env.VITE_AUTH_PROXY_TARGET || 'http://localhost:8085',
          changeOrigin: true,
        },
        '/api/v3/patients': {
          target: env.VITE_PATIENT_PROXY_TARGET || 'http://localhost:8081',
          changeOrigin: true,
        },
        '/api/v1/patients': {
          target: env.VITE_PATIENT_PROXY_TARGET || 'http://localhost:8081',
          changeOrigin: true,
        },
        '/api/v2/appointments': {
          target: env.VITE_APPOINTMENT_PROXY_TARGET || 'http://localhost:8083',
          changeOrigin: true,
        },
        '/api/v1/appointments': {
          target: env.VITE_APPOINTMENT_PROXY_TARGET || 'http://localhost:8083',
          changeOrigin: true,
        },
        '/api/v1/doctors': {
          target: env.VITE_DOCTOR_PROXY_TARGET || 'http://localhost:8082',
          changeOrigin: true,
        },
        '/api/v1/notifications': {
          target: env.VITE_NOTIFICATION_PROXY_TARGET || 'http://localhost:8084',
          changeOrigin: true,
        },
        '/api': {
          target: env.VITE_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  };
});
