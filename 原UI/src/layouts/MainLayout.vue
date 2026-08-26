<!-- 布局组件 -->
<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useThemeStore } from '../stores/theme'
import { realApi } from '../api/realApi'
import { ElMessage } from 'element-plus'
import {
  DataLine,
  Cpu,
  TrendCharts,
  DataAnalysis,
  Odometer,
  User,
  Setting,
  Fold,
  Expand,
  Bell,
  ArrowDown,
  SwitchButton,
  Monitor,
  Sunny,
  Moon,
  Document,
  Connection,
  WarningFilled,
  CircleCheckFilled,
  InfoFilled
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()

// ========== 告警通知 ==========
const alertRecords = ref<any[]>([])
const alertLoading = ref(false)
const alertPopVisible = ref(false)
let alertTimer: ReturnType<typeof setInterval> | null = null

const unreadAlertCount = computed(() =>
  alertRecords.value.filter(a => a.status === 'TRIGGERED').length
)

async function fetchAlerts() {
  try {
    const page = await realApi.getAlertRecords({ status: 'TRIGGERED', size: 10, page: 0 })
    alertRecords.value = page?.content || []
  } catch { /* ignore */ }
}

async function handleAcknowledge(id: number) {
  const alert = alertRecords.value.find(a => a.id === id)
  // 乐观移除：先从列表中消失
  alertRecords.value = alertRecords.value.filter(a => a.id !== id)
  try {
    await realApi.acknowledgeAlert(id)
    ElMessage.success(alert ? `已确认: ${alert.title?.split('] ')[1] || alert.title}` : '告警已确认')
  } catch {
    // 失败则恢复
    fetchAlerts()
    ElMessage.error('操作失败')
  }
}

async function handleResolve(id: number) {
  const alert = alertRecords.value.find(a => a.id === id)
  alertRecords.value = alertRecords.value.filter(a => a.id !== id)
  try {
    await realApi.resolveAlert(id)
    ElMessage.success(alert ? `已解决: ${alert.title?.split('] ')[1] || alert.title}` : '告警已解决')
  } catch {
    fetchAlerts()
    ElMessage.error('操作失败')
  }
}

function getLevelTag(level: string) {
  if (level === 'CRITICAL') return 'danger'
  if (level === 'WARNING') return 'warning'
  return 'info'
}

function getLevelIcon(level: string) {
  if (level === 'CRITICAL') return WarningFilled
  if (level === 'WARNING') return InfoFilled
  return CircleCheckFilled
}

function getLevelText(level: string) {
  if (level === 'CRITICAL') return '严重'
  if (level === 'WARNING') return '警告'
  return '提示'
}

function formatTime(ts: string) {
  if (!ts) return ''
  const d = new Date(ts)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`
  return d.toLocaleDateString()
}

const isCollapse = ref(false)
const activeMenu = computed(() => route.path)

const breadcrumb = computed(() => {
  const items: { title: string; path?: string }[] = []
  const matched = route.matched
  for (const m of matched) {
    if (m.meta?.title) {
      items.push({ title: m.meta.title as string })
    }
  }
  const nameMap: Record<string, string> = {
    '/dashboard': '数据大屏',
    '/devices': '设备管理',
    '/monitor': '实时监控',
    '/realtime': '实时数据',
    '/data': '数据中心',
    '/connections': '连接实例',
    '/user': '个人中心',
    '/settings': '系统设置',
    '/docs': '接口文档'
  }
  if (nameMap[route.path]) {
    items.push({ title: nameMap[route.path] })
  }
  return items
})

const menuItems = [
  { path: '/dashboard', title: '数据大屏', icon: DataLine },
  { path: '/devices', title: '设备管理', icon: Cpu },
  { path: '/monitor', title: '实时监控', icon: TrendCharts },
  { path: '/realtime', title: '实时数据', icon: Odometer },
  { path: '/data', title: '数据中心', icon: DataAnalysis },
  { path: '/connections', title: '连接实例', icon: Connection },
  { path: '/user', title: '个人中心', icon: User },
  { path: '/settings', title: '系统设置', icon: Setting },
  { path: '/docs', title: '接口文档', icon: Document }
]

const sidebarWidth = ref(220)
const isDragging = ref(false)

function toggleSidebar() {
  isCollapse.value = !isCollapse.value
}

function onDragStart(e: MouseEvent) {
  isDragging.value = true
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  e.preventDefault()
}

function onDragMove(e: MouseEvent) {
  if (!isDragging.value) return
  const w = Math.min(400, Math.max(160, e.clientX))
  sidebarWidth.value = w
  if (w <= 180) isCollapse.value = true
  else isCollapse.value = false
}

function onDragEnd() {
  isDragging.value = false
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

onMounted(() => {
  document.addEventListener('mousemove', onDragMove)
  document.addEventListener('mouseup', onDragEnd)
  fetchAlerts()
  alertTimer = setInterval(fetchAlerts, 15000)  // 每15秒轮询告警
})

onUnmounted(() => {
  document.removeEventListener('mousemove', onDragMove)
  document.removeEventListener('mouseup', onDragEnd)
  if (alertTimer) clearInterval(alertTimer)
})

function handleCommand(command: string) {
  if (command === 'logout') {
    authStore.logout()
    router.push('/login')
  } else if (command === 'profile') {
    router.push('/user')
  }
}

function handleMenuSelect(index: string) {
  router.push(index)
}
</script>

<template>
  <el-container class="main-layout">
    <el-aside
      :width="(isCollapse ? 64 : sidebarWidth) + 'px'"
      :class="['sidebar', { dragging: isDragging }]"
    >
      <div class="logo">
        <el-icon :size="isCollapse ? 24 : 28" class="logo-icon"><Monitor /></el-icon>
        <transition name="logo-fade">
          <span v-show="!isCollapse" class="logo-text">IoT云平台</span>
        </transition>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        router
        class="sidebar-menu"
        background-color="var(--bg-sidebar)"
        text-color="var(--text-secondary)"
        active-text-color="#fff"
        @select="handleMenuSelect"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu>
      <div class="sidebar-resize-handle" @mousedown="onDragStart" />
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="toggle-btn" @click="toggleSidebar">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
          <div class="breadcrumb">
            <span v-for="(item, i) in breadcrumb" :key="i" class="breadcrumb-group">
              <span v-if="i > 0" class="breadcrumb-sep">/</span>
              <span :class="['breadcrumb-item', { current: i === breadcrumb.length - 1 }]">{{ item.title }}</span>
            </span>
          </div>
        </div>
        <div class="header-right">
          <el-tooltip :content="themeStore.mode === 'dark' ? '切换亮色模式' : '切换暗色模式'" placement="bottom">
            <el-button link class="theme-toggle" @click="themeStore.toggle()">
              <el-icon :size="20">
                <Sunny v-if="themeStore.mode === 'dark'" />
                <Moon v-else />
              </el-icon>
            </el-button>
          </el-tooltip>
          <el-popover
            :visible="alertPopVisible"
            placement="bottom-end"
            :width="380"
            trigger="click"
            @show="fetchAlerts"
            @update:visible="alertPopVisible = $event"
          >
            <template #reference>
              <el-badge :value="unreadAlertCount" :hidden="unreadAlertCount === 0" class="message-badge">
                <el-icon :size="20"><Bell /></el-icon>
              </el-badge>
            </template>

            <div class="alert-popover">
              <div class="alert-pop-header">
                <span class="alert-pop-title">
                  <el-icon :size="16"><Bell /></el-icon>
                  告警通知
                </span>
                <span v-if="unreadAlertCount > 0" class="alert-pop-count">{{ unreadAlertCount }} 条待处理</span>
              </div>

              <div class="alert-pop-body" v-loading="alertLoading">
                <template v-if="alertRecords.length > 0">
                  <div
                    v-for="alert in alertRecords"
                    :key="alert.id"
                    class="alert-item"
                    :class="'alert-' + alert.level?.toLowerCase()"
                  >
                    <div class="alert-item-left">
                      <el-icon :size="16" :color="alert.level === 'CRITICAL' ? '#f87171' : alert.level === 'WARNING' ? '#fbbf24' : '#60a5fa'">
                        <component :is="getLevelIcon(alert.level)" />
                      </el-icon>
                    </div>
                    <div class="alert-item-body">
                      <div class="alert-item-title">
                        <el-tag :type="getLevelTag(alert.level)" size="small" effect="dark">
                          {{ getLevelText(alert.level) }}
                        </el-tag>
                        <span>{{ alert.title }}</span>
                      </div>
                      <div class="alert-item-meta">
                        <span>{{ alert.deviceName || alert.deviceId }}</span>
                        <span>{{ formatTime(alert.triggeredAt) }}</span>
                      </div>
                      <div v-if="alert.detail" class="alert-item-detail">{{ alert.detail }}</div>
                    </div>
                    <div class="alert-item-actions">
                      <el-button size="small" text type="primary" @click="handleAcknowledge(alert.id)">确认</el-button>
                      <el-button size="small" text type="success" @click="handleResolve(alert.id)">解决</el-button>
                    </div>
                  </div>
                </template>
                <div v-else class="alert-empty">
                  <el-icon :size="36" color="var(--text-muted)"><Bell /></el-icon>
                  <p>暂无告警消息</p>
                </div>
              </div>

              <div class="alert-pop-footer" v-if="alertRecords.length > 0">
                <el-button size="small" text @click="alertPopVisible = false">关闭</el-button>
              </div>
            </div>
          </el-popover>
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="32" :icon="User" />
              <span class="username">{{ authStore.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="page-fade">
            <keep-alive :max="8">
              <component :is="Component" />
            </keep-alive>
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.main-layout {
  height: 100vh;
  background: var(--bg-page);
}

.sidebar {
  background-color: var(--bg-sidebar);
  border-right: 1px solid var(--sidebar-border);
  transition: width 0.22s cubic-bezier(0.25, 0.1, 0.25, 1);
  position: relative;
  flex-shrink: 0;
  overflow: hidden;
  will-change: width;
  contain: layout style;
  backface-visibility: hidden;
  transform: translateZ(0);
}

.sidebar.dragging {
  transition: none !important;
}

/* 折叠时 logo 文字和菜单文字平滑隐藏 */
.logo-text {
  transition: opacity 0.15s ease;
}

.sidebar-menu :deep(.el-menu-item > span) {
  transition: opacity 0.15s ease;
}

.sidebar-resize-handle {
  position: absolute;
  top: 0;
  right: 0;
  width: 4px;
  height: 100%;
  cursor: col-resize;
  z-index: 20;
  opacity: 0;
  transition: opacity 0.15s, background-color 0.2s;
}

.sidebar:hover .sidebar-resize-handle {
  opacity: 1;
}

.sidebar-resize-handle:hover {
  background-color: var(--accent);
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-primary);
  font-size: 18px;
  font-weight: bold;
  border-bottom: 1px solid var(--sidebar-border);
  overflow: hidden;
}

.logo-icon {
  color: var(--accent-light);
  flex-shrink: 0;
}

.logo-text {
  margin-left: 10px;
  white-space: nowrap;
}

.logo-fade-enter-active,
.logo-fade-leave-active {
  transition: opacity 0.3s ease;
}

.logo-fade-enter-from,
.logo-fade-leave-to {
  opacity: 0;
}

.sidebar-menu {
  border-right: none;
}

.sidebar-menu :deep(.el-menu-item:hover) {
  background-color: var(--accent-glow) !important;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background-color: var(--accent) !important;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: var(--bg-header);
  box-shadow: var(--shadow);
  z-index: 10;
  border-bottom: 1px solid var(--border-color);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 15px;
}

.toggle-btn {
  font-size: 20px;
  cursor: pointer;
  color: var(--text-secondary);
}

.toggle-btn:hover {
  color: var(--accent);
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
}

.breadcrumb-item {
  color: var(--text-muted);
}

.breadcrumb-item.current {
  color: var(--text-primary);
  font-weight: 500;
}

.breadcrumb-group {
  display: contents;
}

.breadcrumb-sep {
  color: var(--text-muted);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.theme-toggle {
  color: var(--text-secondary);
  font-size: 20px;
  padding: 4px;
  transition: color 0.3s;
}

.theme-toggle:hover {
  color: var(--accent);
}

.message-badge {
  cursor: pointer;
  color: var(--text-secondary);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 5px 10px;
  border-radius: 4px;
  transition: background-color 0.3s;
}

.user-info:hover {
  background-color: var(--bg-hover);
}

.username {
  font-size: 14px;
  color: var(--text-secondary);
}

.main-content {
  background-color: var(--bg-main);
  padding: 20px;
  overflow-y: auto;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* 页面切换：仅 opacity，GPU composite 层，无 layout/paint */
.page-fade-enter-active { transition: opacity 0.1s ease-out; }
.page-fade-leave-active { transition: opacity 0.06s ease-in; position: absolute; }
.page-fade-enter-from,
.page-fade-leave-to { opacity: 0; }

/* Global theme overrides for Element Plus */
:deep(.el-card) {
  background-color: var(--bg-card);
  border-color: var(--border-color);
  color: var(--text-primary);
}

:deep(.el-card__header) {
  border-bottom-color: var(--border-light);
  color: var(--text-primary);
}

:deep(.el-table) {
  --el-table-bg-color: var(--bg-card);
  --el-table-tr-bg-color: var(--bg-card);
  --el-table-header-bg-color: var(--bg-hover);
  --el-table-border-color: var(--border-color);
  --el-table-text-color: var(--text-primary);
  --el-table-header-text-color: var(--text-secondary);
}

:deep(.el-table th.el-table__cell) {
  background-color: var(--bg-hover);
}

:deep(.el-pagination) {
  color: var(--text-secondary);
}

:deep(.el-descriptions__label) {
  color: var(--text-secondary);
}

:deep(.el-descriptions__content) {
  color: var(--text-primary);
}

:deep(.el-tabs__item) {
  color: var(--text-secondary);
}

:deep(.el-tabs__item.is-active) {
  color: var(--accent);
}

:deep(.el-input__wrapper) {
  background-color: var(--bg-card);
}

:deep(.el-select .el-input__wrapper) {
  background-color: var(--bg-card);
}
</style>

<!-- Alert popover styles (unscoped — popover renders outside component) -->
<style>
.alert-popover {
  display: flex;
  flex-direction: column;
  max-height: 460px;
}

.alert-pop-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border-color);
  margin-bottom: 8px;
}

.alert-pop-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 14px;
  color: var(--text-primary);
}

.alert-pop-count {
  font-size: 12px;
  color: var(--danger);
  font-weight: 500;
}

.alert-pop-body {
  flex: 1;
  overflow-y: auto;
  min-height: 60px;
}

.alert-pop-footer {
  text-align: right;
  padding-top: 8px;
  border-top: 1px solid var(--border-color);
  margin-top: 8px;
}

.alert-item {
  display: flex;
  gap: 10px;
  padding: 10px 8px;
  border-radius: 6px;
  margin-bottom: 4px;
  transition: background 0.15s;
}

.alert-item:hover {
  background: var(--bg-hover);
}

.alert-item.alert-critical {
  border-left: 3px solid #f87171;
}

.alert-item.alert-warning {
  border-left: 3px solid #fbbf24;
}

.alert-item.alert-info {
  border-left: 3px solid #60a5fa;
}

.alert-item-left {
  flex-shrink: 0;
  padding-top: 2px;
}

.alert-item-body {
  flex: 1;
  min-width: 0;
}

.alert-item-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.alert-item-meta {
  display: flex;
  gap: 8px;
  font-size: 11px;
  color: var(--text-muted);
  margin-bottom: 2px;
}

.alert-item-detail {
  font-size: 12px;
  color: var(--text-secondary);
  word-break: break-all;
  margin-top: 2px;
}

.alert-item-actions {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex-shrink: 0;
}

.alert-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 30px 0;
  color: var(--text-muted);
  gap: 8px;
}

.alert-empty p {
  margin: 0;
  font-size: 13px;
}
</style>
