import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// During development, forward /api requests to the Spring Boot backend.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8081',
    },
  },
})
