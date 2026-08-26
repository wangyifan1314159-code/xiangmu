import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export type ThemeMode = 'light' | 'dark'

export const useThemeStore = defineStore('theme', () => {
  const saved = localStorage.getItem('iot_theme') as ThemeMode | null
  const mode = ref<ThemeMode>(saved === 'dark' ? 'dark' : 'light')

  function applyTheme(m: ThemeMode) {
    const root = document.documentElement
    root.setAttribute('data-theme', m)

    if (m === 'dark') {
      root.classList.add('dark')
      root.style.setProperty('--bg-page',          '#0f172a')
      root.style.setProperty('--bg-card',          '#1e293b')
      root.style.setProperty('--bg-sidebar',       '#0c1222')
      root.style.setProperty('--bg-header',        '#1e293b')
      root.style.setProperty('--bg-main',          '#0f172a')
      root.style.setProperty('--bg-hover',         '#1e293b')
      root.style.setProperty('--text-primary',     '#f1f5f9')
      root.style.setProperty('--text-secondary',   '#94a3b8')
      root.style.setProperty('--text-muted',       '#64748b')
      root.style.setProperty('--border-color',     '#1e293b')
      root.style.setProperty('--border-light',     '#1a2235')
      root.style.setProperty('--sidebar-border',   'rgba(255,255,255,0.08)')
      root.style.setProperty('--accent',           '#818cf8')
      root.style.setProperty('--accent-light',     '#a5b4fc')
      root.style.setProperty('--accent-glow',      'rgba(129,140,248,0.2)')
      root.style.setProperty('--success',          '#34d399')
      root.style.setProperty('--warning',          '#fbbf24')
      root.style.setProperty('--danger',           '#f87171')
      root.style.setProperty('--shadow',           '0 2px 8px rgba(0,0,0,0.3), 0 1px 3px rgba(0,0,0,0.2)')
      root.style.setProperty('--chart-grid',       '#1e293b')
      root.style.setProperty('--chart-axis',       '#64748b')
      root.style.setProperty('--sensor-color',     '#60a5fa')
      root.style.setProperty('--sensor-bg',        'rgba(96,165,250,0.15)')
      root.style.setProperty('--sensor-gradient',  'linear-gradient(135deg,rgba(96,165,250,0.2),rgba(96,165,250,0.05))')
      root.style.setProperty('--actuator-color',   '#fbbf24')
      root.style.setProperty('--actuator-bg',      'rgba(251,191,36,0.15)')
      root.style.setProperty('--actuator-gradient','linear-gradient(135deg,rgba(251,191,36,0.2),rgba(251,191,36,0.05))')
      root.style.setProperty('--status-online',    '#34d399')
      root.style.setProperty('--status-offline',   '#64748b')
      root.style.setProperty('--status-warning',   '#fbbf24')
    } else {
      root.classList.remove('dark')
      root.style.setProperty('--bg-page',          '#f3f4f6')
      root.style.setProperty('--bg-card',          '#ffffff')
      root.style.setProperty('--bg-sidebar',       '#f8fafc')
      root.style.setProperty('--bg-header',        '#ffffff')
      root.style.setProperty('--bg-main',          '#f3f4f6')
      root.style.setProperty('--bg-hover',         '#f1f5f9')
      root.style.setProperty('--text-primary',     '#1e293b')
      root.style.setProperty('--text-secondary',   '#64748b')
      root.style.setProperty('--text-muted',       '#94a3b8')
      root.style.setProperty('--border-color',     '#e2e8f0')
      root.style.setProperty('--border-light',     '#f1f5f9')
      root.style.setProperty('--sidebar-border',   '#e2e8f0')
      root.style.setProperty('--accent',           '#6366f1')
      root.style.setProperty('--accent-light',     '#818cf8')
      root.style.setProperty('--accent-glow',      'rgba(99,102,241,0.12)')
      root.style.setProperty('--success',          '#10b981')
      root.style.setProperty('--warning',          '#f59e0b')
      root.style.setProperty('--danger',           '#ef4444')
      root.style.setProperty('--shadow',           '0 1px 3px rgba(0,0,0,0.06), 0 1px 2px rgba(0,0,0,0.04)')
      root.style.setProperty('--chart-grid',       '#f1f5f9')
      root.style.setProperty('--chart-axis',       '#94a3b8')
      root.style.setProperty('--sensor-color',     '#3b82f6')
      root.style.setProperty('--sensor-bg',        'rgba(59,130,246,0.1)')
      root.style.setProperty('--sensor-gradient',  'linear-gradient(135deg,rgba(59,130,246,0.15),rgba(59,130,246,0.05))')
      root.style.setProperty('--actuator-color',   '#f59e0b')
      root.style.setProperty('--actuator-bg',      'rgba(245,158,11,0.1)')
      root.style.setProperty('--actuator-gradient','linear-gradient(135deg,rgba(245,158,11,0.15),rgba(245,158,11,0.05))')
      root.style.setProperty('--status-online',    '#10b981')
      root.style.setProperty('--status-offline',   '#94a3b8')
      root.style.setProperty('--status-warning',   '#f59e0b')
    }
  }

  function toggle() {
    mode.value = mode.value === 'dark' ? 'light' : 'dark'
  }

  watch(mode, (m) => {
    localStorage.setItem('iot_theme', m)
    applyTheme(m)
  }, { immediate: true })

  return { mode, toggle, applyTheme }
})
