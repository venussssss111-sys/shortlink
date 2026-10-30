import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
// Element Plus 自带的暗色变量（会被 styles.css 里的 html.dark 覆盖成 Linear 调色板）
import 'element-plus/theme-chalk/dark/css-vars.css'

import App from './App.vue'
import './styles.css'

// 整个应用走 Linear 的深色画布
document.documentElement.classList.add('dark')

createApp(App).use(ElementPlus, { locale: zhCn }).mount('#app')
