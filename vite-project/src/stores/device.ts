import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api, isInFallback } from '../api'
import type { Device, DataPoint } from '../api/mockApi'
import { useWebSocket } from './websocket'

export const useDeviceStore = defineStore('device', () => {
  const devices = ref<Device[]>([])
  const currentDevice = ref<Device | null>(null)
  const deviceData = ref<DataPoint[]>([])
  const loading = ref(false)
  let wsUnsubscribe: (() => void) | null = null

  const onlineCount = computed(() => devices.value.filter(d => d.status === 'online').length)
  const offlineCount = computed(() => devices.value.filter(d => d.status === 'offline').length)
  const warningCount = computed(() => devices.value.filter(d => d.status === 'warning').length)
  const totalCount = computed(() => devices.value.length)

  async function fetchDevices() {
    loading.value = true
    try {
      const fresh = await api.getDevices()
      // 防御：mock 降级时 getDevices 可能返回空数组，绝不用它清掉已有真实数据
      if (isInFallback() && Array.isArray(fresh) && fresh.length === 0 && devices.value.length > 0) {
        return devices.value
      }
      devices.value = fresh
      useWebSocket().subscribeDevices(devices.value.map(d => d.id))
      return devices.value
    } finally {
      loading.value = false
    }
  }

  async function fetchDeviceById(id: string) {
    loading.value = true
    try {
      currentDevice.value = await api.getDeviceById(id)
      return currentDevice.value
    } finally {
      loading.value = false
    }
  }

  async function createDevice(deviceData: any) {
    loading.value = true
    try {
      const d = await api.createDevice(deviceData)
      devices.value.push(d)
      useWebSocket().subscribeDevice(d.id)
      return d
    } finally {
      loading.value = false
    }
  }

  async function updateDevice(id: string, updates: any) {
    loading.value = true
    try {
      const updated = await api.updateDevice(id, updates)
      const index = devices.value.findIndex(d => d.id === id)
      if (index !== -1) devices.value[index] = updated
      if (currentDevice.value?.id === id) currentDevice.value = updated
      return updated
    } finally {
      loading.value = false
    }
  }

  async function deleteDevice(id: string) {
    loading.value = true
    try {
      await api.deleteDevice(id)
      devices.value = devices.value.filter(d => d.id !== id)
      useWebSocket().unsubscribeDevice(id)
      if (currentDevice.value?.id === id) currentDevice.value = null
    } finally {
      loading.value = false
    }
  }

  async function updateDeviceStatus(id: string, status: string) {
    const updated = await api.updateDeviceStatus(id, status)
    const index = devices.value.findIndex(d => d.id === id)
    if (index !== -1) {
      devices.value[index].status = status.toLowerCase() as any
    }
    if (currentDevice.value?.id === id) {
      currentDevice.value.status = status.toLowerCase() as any
    }
    return updated
  }

  async function batchUpdateStatus(ids: string[], status: string) {
    loading.value = true
    try {
      await Promise.all(ids.map(id => api.updateDeviceStatus(id, status)))
      for (const id of ids) {
        const d = devices.value.find(item => item.id === id)
        if (d) d.status = status.toLowerCase() as any
      }
    } finally {
      loading.value = false
    }
  }

  async function batchDeleteDevices(ids: string[]) {
    loading.value = true
    try {
      await Promise.all(ids.map(id => api.deleteDevice(id)))
      devices.value = devices.value.filter(d => !ids.includes(d.id))
      if (currentDevice.value && ids.includes(currentDevice.value.id)) {
        currentDevice.value = null
      }
    } finally {
      loading.value = false
    }
  }

  async function sendCommand(deviceId: string, command: string, params?: Record<string, any>) {
    return await api.sendCommand(deviceId, command, params)
  }

  async function fetchDeviceData(deviceId?: string, sensorId?: string, limit?: number, from?: string, to?: string) {
    deviceData.value = await api.getDeviceData(deviceId, sensorId, limit, from, to)
    return deviceData.value
  }

  let pollTimer: ReturnType<typeof setInterval> | null = null

  function startRealtimeUpdates(interval: number = 3000) {
    if (pollTimer) clearInterval(pollTimer)

    // WebSocket 实时更新传感器值（优先）：订阅所有已加载设备的实时数据
    const ws = useWebSocket()
    ws.subscribeDevices(devices.value.map(d => d.id))
    wsUnsubscribe = ws.onAllDeviceData((data) => {
      const dev = devices.value.find(d => d.id === data.deviceId)
      if (dev) {
        // 更新设备状态
        dev.status = 'online'
        // 更新传感器值
        const sensor = (dev as any).sensors?.find((s: any) => s.id === data.sensorId)
        if (sensor) sensor.value = data.value
        // 更新执行器值
        const actuator = (dev as any).actuators?.find((a: any) => a.id === data.sensorId)
        if (actuator) actuator.value = data.value
      }
      // 同步更新当前设备
      if (currentDevice.value?.id === data.deviceId) {
        const cs = (currentDevice.value as any).sensors?.find((s: any) => s.id === data.sensorId)
        if (cs) cs.value = data.value
        const ca = (currentDevice.value as any).actuators?.find((a: any) => a.id === data.sensorId)
        if (ca) ca.value = data.value
      }
    })

    // HTTP 轮询兜底（5s 全量刷新，防止 WebSocket 丢包）
    pollTimer = setInterval(async () => {
      try {
        const fresh: Device[] = await api.getDevices()
        const prevMap = new Map(devices.value.map(d => [d.id, d]))
        for (const fd of fresh) {
          const existing = prevMap.get(fd.id)
          if (!existing) { devices.value.push(fd); continue }
          existing.status = fd.status
          existing.lastActive = fd.lastActive
        }
        if (currentDevice.value) {
          const updated = fresh.find((d: Device) => d.id === currentDevice.value!.id)
          if (updated) currentDevice.value = updated
        }
      } catch { /* ignore */ }
    }, 5000)
  }

  /** 静默更新设备值：只合并变化字段，不替换对象，避免整表重渲染 */
  async function silentRefreshDevices() {
    try {
      const fresh: any[] = await api.getDevices()
      for (const fd of fresh) {
        const existing = devices.value.find(d => d.id === fd.id)
        if (!existing) continue
        // 只更新变化的字段
        existing.status = fd.status
        existing.lastActive = fd.lastActive
        // 合并传感器值
        for (const fs of (fd.sensors || [])) {
          const es = (existing as any).sensors?.find((s: any) => s.id === fs.id)
          if (es) es.value = fs.value
        }
        // 合并执行器值
        for (const fa of (fd.actuators || [])) {
          const ea = (existing as any).actuators?.find((a: any) => a.id === fa.id)
          if (ea) ea.value = fa.value
        }
      }
    } catch { /* ignore */ }
  }

  function stopRealtimeUpdates() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
    if (wsUnsubscribe) {
      wsUnsubscribe()
      wsUnsubscribe = null
    }
  }

  /** 单设备自动刷新：只请求当前设备，比拉全部设备更高效 */
  function startPollDevice(deviceId: string, interval: number = 3000) {
    stopRealtimeUpdates()
    pollTimer = setInterval(async () => {
      try {
        const updated = await api.getDeviceById(deviceId)
        if (currentDevice.value?.id === deviceId) {
          currentDevice.value = updated
        }
      } catch { /* ignore */ }
    }, interval)
  }

  function reset() {
    stopRealtimeUpdates()
    devices.value = []
    currentDevice.value = null
    deviceData.value = []
    loading.value = false
  }

  return {
    devices, currentDevice, deviceData, loading,
    onlineCount, offlineCount, warningCount, totalCount,
    fetchDevices, fetchDeviceById,
    createDevice, updateDevice, deleteDevice,
    updateDeviceStatus, batchUpdateStatus, batchDeleteDevices,
    sendCommand, fetchDeviceData,
    startRealtimeUpdates, stopRealtimeUpdates, startPollDevice, silentRefreshDevices,
    reset
  }
})
