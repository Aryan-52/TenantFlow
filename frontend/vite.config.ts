import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // Fixed local dev port (allowed by the backend's default CORS origins). strictPort
  // fails fast instead of silently moving to another port if 5173 is taken.
  server: {
    port: 5173,
    strictPort: true,
  },
})
