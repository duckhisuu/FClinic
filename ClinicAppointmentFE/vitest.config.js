import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
export default defineConfig({ plugins: [react()], test: { environment: 'jsdom', setupFiles: ['./src/patient/__tests__/setup.js'], include: ['src/patient/__tests__/*.{test,spec}.{js,jsx}'], maxWorkers: 2, testTimeout: 15000 } });
