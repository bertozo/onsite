import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    // Pinned so date-math tests (report periods, epoch days) are deterministic regardless
    // of the machine running them - local-timezone getters then agree with UTC ones.
    env: { TZ: 'UTC' },
  },
})
