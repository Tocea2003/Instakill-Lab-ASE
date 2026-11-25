import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Backend the dev server proxies to. Defaults to a local backend; point it at a
// backend running elsewhere (e.g. a Linux VM / WSL) without editing this file:
//   BACKEND_URL=http://192.168.64.5:8080 npm run dev
const backend = process.env.BACKEND_URL ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': backend,
      '/auth': backend,
      '/media': backend
    }
  }
})
