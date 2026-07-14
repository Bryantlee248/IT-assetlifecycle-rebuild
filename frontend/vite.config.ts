import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 后端运行在 http://localhost:8080，context-path 为 /api。
// 开发期将 /api 代理到后端，前端 baseURL 配置为 /api，最终请求 /api/v1/...
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    // P1：拆分 vendor chunk，消除单包 > 500kB 警告。视图已按需动态 import 拆分，
    // 此处将 element-plus / vue 生态 / 其他第三方各自独立成块，入口 index 包显著缩小。
    // element-plus 全量引入体积约 1MB，属预期，故将告警阈值设为 1200kB；
    // 进一步按需引入（unplugin 自动导入）属 MVP-2 优化范畴，不在本次整改范围。
    chunkSizeWarningLimit: 1200,
    rollupOptions: {
      output: {
        manualChunks(id: string) {
          if (!id.includes('node_modules')) return undefined
          if (id.includes('element-plus') || id.includes('@element-plus')) return 'element-plus'
          if (id.includes('@vue') || id.includes('vue') || id.includes('pinia') ||
              id.includes('vue-router') || id.includes('@vueuse')) return 'vue-vendor'
          if (id.includes('echarts') || id.includes('zrender')) return 'charts'
          return 'vendor'
        }
      }
    }
  }
})
