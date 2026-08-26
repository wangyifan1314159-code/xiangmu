import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export type ThemeMode = 'light' | 'dark'

export const useThemeStore = defineStore('theme', () => {
  const saved = localStorage.getItem('iot_theme') as ThemeMode | null
  const mode = ref<ThemeMode>(saved === 'light' ? 'light' : 'dark')

  function applyTheme(next: ThemeMode) {
    const root = document.documentElement
    root.dataset.theme = next
    root.classList.toggle('dark', next === 'dark')
  }

  function toggle() {
    mode.value = mode.value === 'dark' ? 'light' : 'dark'
  }

  watch(mode, (next) => {
    localStorage.setItem('iot_theme', next)
    applyTheme(next)
  }, { immediate: true })

  return { mode, toggle, applyTheme }
})
