import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 前端跑在 5173（preview 是 4173），后端跑在 8080。
// 用 proxy 把 /api 开头的请求转给后端，浏览器就不会跨域（虽然后端也配了 CORS，双保险）。
const proxy = {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true
  },
  // 头像等静态资源也在后端，不代理的话 <img src="/uploads/xxx.png"> 会 404
  '/uploads': {
    target: 'http://localhost:8080',
    changeOrigin: true
  }
}

export default defineConfig({
  plugins: [vue()],
  build: {
    // 目标环境是现代浏览器（自己用/演示用），不做低版本降级，
    // 顺便绕开 esbuild 在 Windows 临时目录上删文件失败的问题。
    target: 'esnext',
    chunkSizeWarningLimit: 2500
  },
  server: {
    port: 5173,
    proxy,
    watch: {
      // 某些编辑器/工具用「临时目录 + 原子替换」的方式写文件，
      // Windows 上监视这种目录会直接 EBUSY 把 dev server 打崩，忽略掉。
      ignored: ['**/.*.tmpdir/**', '**/*.tmp']
    }
  },
  preview: {
    port: 4173,
    proxy
  }
})
