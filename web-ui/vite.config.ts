import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api/auth': 'http://localhost:8080',
      '/api/customers': 'http://localhost:8080',
      '/api/products': 'http://localhost:8081',
      '/api/esim-plans': 'http://localhost:8081',
      '/api/me': 'http://localhost:8087',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    css: true,
  },
})
