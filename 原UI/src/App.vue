<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useThemeStore } from './stores/theme'
import { useWebSocket } from './stores/websocket'

const themeStore = useThemeStore()
themeStore.applyTheme(themeStore.mode)

const ws = useWebSocket()
onMounted(() => ws.connect())
onUnmounted(() => ws.disconnect())
</script>

<template>
  <router-view />
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: var(--text-primary);
  background-color: var(--bg-page);
}

/* 主题过渡：仅对使用 CSS 变量的关键容器生效，避免全局 wildcard 卡顿 */
.el-card, .el-table, .el-menu, .sidebar, .header, .main-content,
.el-dialog, .el-popover, .el-dropdown-menu, .el-input__wrapper,
.sensor-card, .actuator-card, .data-panel, .stat-card {
  transition: background-color 0.25s ease, border-color 0.25s ease, color 0.2s ease !important;
}

/* Element Plus 暗色覆盖 */
.dark {
  --el-bg-color: var(--bg-card);
  --el-bg-color-overlay: var(--bg-card);
  --el-text-color-primary: var(--text-primary);
  --el-text-color-regular: var(--text-secondary);
  --el-border-color: var(--border-color);
  --el-border-color-light: var(--border-light);
  --el-fill-color-blank: var(--bg-card);
  --el-fill-color-light: var(--bg-hover);
  --el-color-primary: var(--accent);
  --el-mask-color: rgba(0, 0, 0, 0.6);
}

/* 滚动条暗色适配 */
.dark ::-webkit-scrollbar { width: 6px; }
.dark ::-webkit-scrollbar-track { background: var(--bg-main); }
.dark ::-webkit-scrollbar-thumb { background: var(--border-color); border-radius: 3px; }

/* 滚动性能优化 */
.el-main.main-content {
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

/* GPU 加速提示 */
.sidebar { will-change: width; }
.main-content { will-change: contents; }
</style>
