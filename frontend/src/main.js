import { createApp } from 'vue'
import ElementPlus, { ElMessage } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import './assets/main.css'
import App from './App.vue'
import router from './router'
import { setErrorNotifier, setUnauthorizedHandler } from './api/http'
import { resetSession } from './utils/session'

const app = createApp(App)

app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 统一错误提示：拦截器已把后端的 message 归一化到 error.message
setErrorNotifier((error) => {
  ElMessage.error(error.message)
})

// 会话失效（401）：清理本地登录态并回到登录页
setUnauthorizedHandler(() => {
  resetSession()
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    router.replace({ name: 'login', query: { redirect: current.fullPath } })
  }
})

app.mount('#app')
