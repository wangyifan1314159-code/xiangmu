import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('../views/auth/LoginView.vue'),
      meta: { public: true }
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('../views/auth/RegisterView.vue'),
      meta: { public: true }
    },
    {
      path: '/',
      component: () => import('../layouts/MainLayout.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('../views/dashboard/DashboardView.vue'),
          meta: { title: '数据大屏', icon: 'DataLine' }
        },
        {
          path: 'bigdata',
          name: 'BigDataDashboard',
          component: () => import('../views/bigdata/BigDataDashboardView.vue'),
          meta: { title: '大数据湖仓大屏', icon: 'Histogram' }
        },
        {
          path: 'ai-assistant',
          name: 'AiAssistant',
          component: () => import('../views/ai/AiAssistantView.vue'),
          meta: { title: 'AI辅助系统', icon: 'MagicStick' }
        },
        {
          path: 'devices',
          name: 'Devices',
          component: () => import('../views/devices/DeviceListView.vue'),
          meta: { title: '设备管理', icon: 'Cpu' }
        },
        {
          path: 'devices/:id',
          name: 'DeviceDetail',
          component: () => import('../views/devices/DeviceDetailView.vue'),
          meta: { title: '设备详情', hidden: true }
        },
        {
          path: 'monitor',
          name: 'Monitor',
          component: () => import('../views/monitor/MonitorView.vue'),
          meta: { title: '实时监控', icon: 'TrendCharts' }
        },
        {
          path: 'video',
          name: 'VideoMonitoring',
          component: () => import('../views/monitor/VideoMonitoringView.vue'),
          meta: { title: '视频监控', icon: 'VideoCamera' }
        },
        {
          path: 'alerts',
          name: 'AlertCenter',
          component: () => import('../views/monitor/AlertCenterView.vue'),
          meta: { title: '报警中心', icon: 'Bell' }
        },
        {
          path: 'data',
          name: 'DataCenter',
          component: () => import('../views/monitor/DataCenterView.vue'),
          meta: { title: '数据中心', icon: 'DataAnalysis' }
        },
        {
          path: 'realtime',
          name: 'DataDisplay',
          component: () => import('../views/monitor/DataDisplayView.vue'),
          meta: { title: '实时数据', icon: 'Odometer' }
        },
        {
          path: 'connections',
          name: 'TcpConnections',
          component: () => import('../views/monitor/TcpConnectionsView.vue'),
          meta: { title: '连接实例', icon: 'Connection' }
        },
        {
          path: 'device-connect',
          name: 'TcpDeviceConnect',
          component: () => import('../views/monitor/TcpDeviceConnectView.vue'),
          meta: { title: '设备连接', icon: 'Connection' }
        },
        {
          path: 'user',
          name: 'UserProfile',
          component: () => import('../views/user/UserProfileView.vue'),
          meta: { title: '个人中心', icon: 'User' }
        },
        {
          path: 'settings',
          name: 'Settings',
          component: () => import('../views/user/SettingsView.vue'),
          meta: { title: '系统设置', icon: 'Setting' }
        },
        {
          path: 'docs',
          name: 'ApiDocs',
          component: () => import('../views/docs/ApiDocsView.vue'),
          meta: { title: '接口文档', icon: 'Document' }
        }
      ]
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/dashboard'
    }
  ]
})

router.beforeEach(async (to, _from, next) => {
  const authStore = useAuthStore()
  if (!authStore.token) {
    await authStore.initAuth()
  }

  if (to.meta.public && authStore.isLoggedIn) {
    next('/dashboard')
  } else if (!to.meta.public && !authStore.isLoggedIn) {
    next('/login')
  } else {
    next()
  }
})

export default router
