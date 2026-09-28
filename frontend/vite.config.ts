import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Same origin for the browser: /api goes to Spring Boot, so the backend needs no CORS config.
    proxy: { '/api': 'http://localhost:8080' },
  },
})
