import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api/customers': 'http://localhost:8080',
      '/api/products': 'http://localhost:8081',
      '/api/esim-plans': 'http://localhost:8081',
      '/api/orders': 'http://localhost:8082',
      '/api/payments': 'http://localhost:8083',
      '/api/activations': 'http://localhost:8084',
      '/api/fulfillments': 'http://localhost:8085',
      '/api/subscriptions': 'http://localhost:8086',
      '/api/workflows': 'http://localhost:8087',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    css: true,
  },
})
