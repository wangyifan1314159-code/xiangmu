import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    // 不设置 allowedHosts：保留 Vite 的 Host 校验（DNS rebinding 防护）
    proxy: {
      // 大数据分析接口转发到 iot-data-service (8082)
      '/api/bigdata': {
        target: 'http://localhost:8082',
        changeOrigin: true
      },
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.removeHeader('host')
            proxyReq.setHeader('host', 'localhost:8080')
          })
        }
      }
    }
  }
})
