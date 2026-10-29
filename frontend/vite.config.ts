import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    proxy: {
      '/api': {
        target: process.env.ALON_API_URL ?? 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
