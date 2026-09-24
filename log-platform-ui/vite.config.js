import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': new URL('./src', import.meta.url).pathname
    }
  },
  server: {
    host: true,
    port: 3100,
    strictPort: true,
    proxy: {
      '/monitor': {
        target: 'http://127.0.0.1:8103',
        changeOrigin: true
      }
    }
  }
})
