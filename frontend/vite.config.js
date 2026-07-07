import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/auth': 'http://localhost:8082',
      '/analytics': 'http://localhost:8082',
      '/events': 'http://localhost:8082',
      '/health': 'http://localhost:8082',
      '/queue': 'http://localhost:8082',
      '/redis': 'http://localhost:8082',
      '/ws': {
        target: 'http://localhost:8082',
        ws: true,
      },
    },
  },
})
