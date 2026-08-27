<script setup lang="ts">
defineOptions({ name: 'Monitor' })
import { ref, onMounted, onUnmounted, onActivated, onDeactivated, computed, watch, nextTick } from 'vue'
import { useDeviceStore } from '../../stores/device'
import { useWebSocket, type WsDeviceData } from '../../stores/websocket'
import { realApi } from '../../api/realApi'
import { TrendCharts } from '@element-plus/icons-vue'
import * as echarts from 'echarts'

const deviceStore = useDeviceStore()
const selectedDeviceId = ref('')
const chartRefs = ref<Map<string, HTMLDivElement>>(new Map())
const chartInstances = ref<Map<string, echarts.ECharts>>(new Map())

function setChartRef(el: unknown, key: string) {
  if (el instanceof HTMLDivElement) {
    chartRefs.value.set(key, el)
  } else if (!el) {
    chartRefs.value.delete(key)
  }
}
const chartData = ref<Map<string, { times: string[]; values: number[] }>>(new Map())

const MAX_POINTS = 40
let pollTimer: ReturnType<typeof setInterval> | null = null

const onlineDevices = computed(() => (deviceStore.devices || []).filter(d => d && d.status === 'online'))

const selectedDevice = computed(() => {
  if (!selectedDeviceId.value) return null
  return (deviceStore.devices || []).find(d => d && d.id === selectedDeviceId.value) || null
})

const sortedSensors = computed(() => {
  const dev = selectedDevice.value
  if (!dev || !Array.isArray(dev.sensors)) return []
  
  return [...dev.sensors].sort((a, b) => {
    const aName = a?.name || ''
    const bName = b?.name || ''
    const aType = a?.type || ''
    const bType = b?.type || ''
    
    // 环境传感器在前面
    const aIsEnv = aName.includes('环境') || aType.includes('环境')
    const bIsEnv = bName.includes('环境') || bType.includes('环境')
    if (aIsEnv && !bIsEnv) return -1
    if (!aIsEnv && bIsEnv) return 1
    
    // 设备传感器在后面
    const aIsDev = aName.includes('设备') || aType.includes('设备')
    const bIsDev = bName.includes('设备') || bType.includes('设备')
    if (aIsDev && !bIsDev) return 1
    if (!aIsDev && bIsDev) return -1
    
    // 默认按 ID 排序保持稳定
    const aId = a?.id || ''
    const bId = b?.id || ''
    return aId.localeCompare(bId)
  })
})

function selectDevice(deviceId: string) {
  if (!deviceId || selectedDeviceId.value === deviceId) return
  console.log(`[Monitor ${new Date().toLocaleTimeString()}] Switch selected device:`, deviceId)
  selectedDeviceId.value = deviceId
  nextTick(() => {
    try {
      initCharts()
      updateCharts()
    } catch (err) {
      console.error('[Monitor] selectDevice chart init error:', err)
    }
  })
}

function getSensorKey(deviceId: string, sensorId: string) {
  return `${deviceId || 'dev'}_${sensorId || 'sensor'}`
}

function pushDataPoint(sensorKey: string, value: number) {
  // 【白屏防御】过滤无效数值，防止 NaN/Infinity 导致图表渲染异常
  if (typeof value !== 'number' || !Number.isFinite(value)) return
  const existing = chartData.value.get(sensorKey)
  const time = new Date().toLocaleTimeString()
  if (existing) {
    existing.times.push(time)
    existing.values.push(value)
    if (existing.times.length > MAX_POINTS) {
      existing.times.shift()
      existing.values.shift()
    }
  } else {
    chartData.value.set(sensorKey, {
      times: [time],
      values: [value]
    })
  }
}

function initCharts() {
  const dev = selectedDevice.value
  if (!dev || !Array.isArray(dev.sensors)) return

  for (const sensor of (dev.sensors || [])) {
    if (!sensor || !sensor.id) continue
    const sensorKey = getSensorKey(selectedDeviceId.value, sensor.id)
    const container = chartRefs.value.get(sensorKey)
    if (!container || !container.isConnected) continue

    if (!chartData.value.has(sensorKey)) {
      chartData.value.set(sensorKey, { times: [], values: [] })
    }

    let instance = chartInstances.value.get(sensorKey)
    if (instance) {
      try { instance.dispose() } catch {}
    }

    try {
      instance = echarts.init(container, undefined, { devicePixelRatio: window.devicePixelRatio || 1 })
      chartInstances.value.set(sensorKey, instance)

      const maxVal = typeof sensor.max === 'number' && Number.isFinite(sensor.max) ? sensor.max : 100
      const minVal = typeof sensor.min === 'number' && Number.isFinite(sensor.min) ? sensor.min : 0
      const currentCd = chartData.value.get(sensorKey)

      instance.setOption({
        tooltip: {
          trigger: 'axis',
          backgroundColor: 'rgba(13,31,60,0.95)',
          borderColor: '#1890ff',
          textStyle: { color: '#e0e6ed', fontSize: 12 }
        },
        grid: { left: 45, right: 15, top: 15, bottom: 25 },
        xAxis: {
          type: 'category',
          data: currentCd?.times || [],
          axisLine: { lineStyle: { color: '#1a3050' } },
          axisTick: { show: false },
          axisLabel: { color: '#8b9cb5', fontSize: 10, show: false },
          splitLine: { show: false }
        },
        yAxis: {
          type: 'value',
          name: sensor.unit || '',
          min: minVal,
          max: maxVal,
          splitLine: { lineStyle: { color: '#1a3050', type: 'dashed' } },
          axisLabel: { color: '#8b9cb5', fontSize: 10 },
          nameTextStyle: { color: '#8b9cb5', fontSize: 10 }
        },
        series: [{
          data: currentCd?.values || [],
          type: 'line',
          smooth: true,
          showSymbol: false,
          lineStyle: { color: '#1890ff', width: 2 },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(24,144,255,0.35)' },
              { offset: 1, color: 'rgba(24,144,255,0.02)' }
            ])
          },
          markLine: {
            silent: true,
            symbol: 'none',
            lineStyle: { color: '#f56c6c', type: 'dashed', width: 1 },
            data: [
              { yAxis: maxVal * 0.9, label: { formatter: '阈值', color: '#f56c6c', fontSize: 10 } }
            ]
          }
        }]
      })
    } catch (err) {
      console.error('[Monitor] initCharts error for sensor:', sensorKey, err)
    }
  }
}

function updateCharts() {
  const dev = selectedDevice.value
  if (!dev || !Array.isArray(dev.sensors)) return

  for (const sensor of (dev.sensors || [])) {
    if (!sensor || !sensor.id) continue
    const sensorKey = getSensorKey(selectedDeviceId.value, sensor.id)
    const safeValue = typeof sensor.value === 'number' && Number.isFinite(sensor.value) ? sensor.value : 0
    pushDataPoint(sensorKey, safeValue)

    const instance = chartInstances.value.get(sensorKey)
    const data = chartData.value.get(sensorKey)
    if (!instance || !data) continue

    try {
      instance.setOption({
        xAxis: { data: data.times },
        series: [{ data: data.values }]
      })
    } catch (err) {
      console.error('[Monitor] updateCharts error for sensor:', sensorKey, err)
    }
  }
}

/**
 * 【优化】单设备快速轮询（1.5s 响应），直接调用单设备接口，保留上次有效数据防白屏
 */
async function pollDeviceData() {
  if (!selectedDeviceId.value) return

  try {
    const fresh = await realApi.getDeviceById(selectedDeviceId.value)
    if (fresh && fresh.id === selectedDeviceId.value) {
      const dev = (deviceStore.devices || []).find(d => d && d.id === selectedDeviceId.value)
      if (dev) {
        dev.status = fresh.status
        dev.sensors = fresh.sensors || []
      }
      for (const sensor of (fresh.sensors || [])) {
        if (!sensor || !sensor.id) continue
        const sensorKey = getSensorKey(fresh.id, sensor.id)
        const safeValue = typeof sensor.value === 'number' && Number.isFinite(sensor.value) ? sensor.value : 0
        pushDataPoint(sensorKey, safeValue)
      }
      updateCharts()
    }
  } catch (err) {
    // 接口临时失败时静默捕获，保留上一次有效数据，绝不白屏
    console.warn('[Monitor] pollDeviceData fetch warning (retaining last state):', err)
  }
}

// WebSocket 推送即时重绘：数据到达毫秒级刷新曲线，不再等待轮询周期
let unsubWsData: (() => void) | null = null

function handleWsData(data: WsDeviceData) {
  if (!data || data.type !== 'data' || !selectedDeviceId.value) return
  if (data.deviceId !== selectedDeviceId.value) return

  // 【关键日志】记录 WS 推送到达
  console.log(`[Monitor ${new Date().toLocaleTimeString()}] WS push received:`, data.deviceId, data.sensorId, data.value)

  // 【白屏防御】检查数值有效性
  if (typeof data.value !== 'number' || !Number.isFinite(data.value)) {
    console.warn('[Monitor] invalid WS value, skipped:', data)
    return
  }

  const dev = selectedDevice.value
  const sensor = (dev?.sensors || []).find(s => s && s.id === data.sensorId)
  if (!sensor) {
    console.debug('[Monitor] sensor not found on current device view:', data.sensorId)
    return
  }

  sensor.value = data.value
  const sensorKey = getSensorKey(data.deviceId, data.sensorId)
  pushDataPoint(sensorKey, data.value)

  const instance = chartInstances.value.get(sensorKey)
  const cd = chartData.value.get(sensorKey)
  if (instance && cd) {
    try {
      instance.setOption({
        xAxis: { data: cd.times },
        series: [{ data: cd.values }]
      })
    } catch (err) {
      console.error('[Monitor] handleWsData chart update error:', err)
    }
  }
}

function handleResize() {
  for (const instance of chartInstances.value.values()) {
    try { instance.resize() } catch {}
  }
}

watch(selectedDevice, (dev) => {
  if (dev) {
    nextTick(() => {
      try {
        initCharts()
        updateCharts()
      } catch (err) {
        console.error('[Monitor] watch selectedDevice chart error:', err)
      }
    })
  }
})

function startRealtime() {
  stopRealtime()
  useWebSocket().connect()
  deviceStore.startRealtimeUpdates(1500)
  unsubWsData = useWebSocket().onAllDeviceData(handleWsData)
  // 【优化】单设备轮询提升至 1.5s，保证即使无 WS 时刷新也在 1~2s 之间
  pollTimer = setInterval(pollDeviceData, 1500)
}

function stopRealtime() {
  deviceStore.stopRealtimeUpdates()
  if (unsubWsData) { unsubWsData(); unsubWsData = null }
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

onMounted(async () => {
  try {
    await deviceStore.fetchDevices()
    if (onlineDevices.value.length > 0 && !selectedDeviceId.value) {
      selectedDeviceId.value = onlineDevices.value[0].id
      await nextTick()
      initCharts()
    }
  } catch (err) {
    console.error('[Monitor] onMounted init error:', err)
  }
  startRealtime()
  window.addEventListener('resize', handleResize)
})

onActivated(() => { startRealtime() })
onDeactivated(() => { stopRealtime() })

onUnmounted(() => {
  stopRealtime()
  window.removeEventListener('resize', handleResize)
  for (const instance of chartInstances.value.values()) {
    try { instance.dispose() } catch {}
  }
  chartInstances.value.clear()
})
</script>

<template>
  <div class="monitor">
    <div class="page-header">
      <h2>实时监控</h2>
      <p>查看设备传感器数据实时变化曲线（刷新延迟 &lt; 2s，多层空值保护）</p>
    </div>

    <el-row :gutter="20">
      <el-col :span="5">
        <div class="device-list-card">
          <div class="card-title">在线设备</div>
          <div class="device-list">
            <div
              v-for="device in onlineDevices"
              :key="device?.id || Math.random()"
              :class="['device-item', { active: selectedDeviceId === device?.id }]"
              @click="device?.id && selectDevice(device.id)"
            >
              <div class="device-indicator" />
              <div class="device-item-info">
                <div class="device-item-name">{{ device?.name || '未知设备' }}</div>
                <div class="device-item-type">{{ device?.type || '--' }}</div>
              </div>
              <div class="device-sensor-count">{{ (device?.sensors || []).length }} 传感器</div>
            </div>
            <el-empty v-if="onlineDevices.length === 0" description="暂无在线设备" />
          </div>
        </div>
      </el-col>

      <el-col :span="19">
        <div v-if="selectedDevice" class="charts-area">
          <div class="charts-header">
            <span class="charts-title">{{ selectedDevice?.name || selectedDeviceId }} — 实时数据曲线</span>
            <el-tag type="success" size="small" effect="dark" round>
              <span class="pulse-dot" /> 实时更新中
            </el-tag>
          </div>

          <el-row :gutter="16">
            <el-col
              v-for="sensor in sortedSensors"
              :key="sensor?.id || Math.random()"
              :span="sortedSensors.length === 1 ? 24 : sortedSensors.length === 2 ? 12 : 12"
            >
              <div class="chart-card">
                <div class="chart-card-header">
                  <span class="sensor-name">{{ sensor?.name || '传感器' }}</span>
                  <span class="sensor-current">
                    {{ (typeof sensor?.value === 'number' && Number.isFinite(sensor.value) ? sensor.value : 0).toFixed(2) }}
                    <span class="sensor-unit">{{ sensor?.unit || '' }}</span>
                  </span>
                </div>
                <div
                  :ref="(el) => setChartRef(el, `${selectedDeviceId}_${sensor?.id}`)"
                  class="chart-container"
                />
              </div>
            </el-col>
          </el-row>

          <div class="sensor-detail-table">
            <h4>传感器数值详情</h4>
            <el-table :data="sortedSensors" style="width: 100%" empty-text="该设备暂无传感器数据">
              <el-table-column prop="name" label="传感器名称">
                <template #default="{ row }">{{ row?.name || '--' }}</template>
              </el-table-column>
              <el-table-column prop="type" label="类型">
                <template #default="{ row }">{{ row?.type || '--' }}</template>
              </el-table-column>
              <el-table-column label="当前值">
                <template #default="{ row }">
                  <span :class="['value-tag', { alert: typeof row?.value === 'number' && typeof row?.max === 'number' && row.value > row.max * 0.9 }]">
                    {{ (typeof row?.value === 'number' && Number.isFinite(row.value) ? row.value : 0).toFixed(2) }}
                  </span>
                  {{ row?.unit || '' }}
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="typeof row?.value === 'number' && typeof row?.max === 'number' && row.value > row.max * 0.9 ? 'danger' : 'success'" size="small">
                    {{ typeof row?.value === 'number' && typeof row?.max === 'number' && row.value > row.max * 0.9 ? '超限' : '正常' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="min" label="最小值">
                <template #default="{ row }">{{ row?.min != null ? row.min : '--' }}</template>
              </el-table-column>
              <el-table-column prop="max" label="最大值">
                <template #default="{ row }">{{ row?.max != null ? row.max : '--' }}</template>
              </el-table-column>
            </el-table>
          </div>
        </div>

        <div v-else class="empty-charts">
          <div class="empty-charts-inner">
            <el-icon :size="64" color="var(--text-muted)"><TrendCharts /></el-icon>
            <p>请选择一个在线设备以查看实时数据曲线</p>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 6px;
  font-size: 22px;
  color: var(--text-primary);
}

.page-header p {
  margin: 0;
  color: var(--text-muted);
  font-size: 14px;
}

.device-list-card {
  background: var(--bg-card);
  border-radius: 8px;
  border: 1px solid var(--border-color);
  overflow: hidden;
}

.card-title {
  padding: 14px 16px;
  font-weight: 600;
  font-size: 15px;
  color: var(--text-primary);
  border-bottom: 1px solid var(--border-light);
}

.device-list {
  max-height: 640px;
  overflow-y: auto;
  padding: 8px;
}

.device-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.25s;
  margin-bottom: 4px;
  border: 1px solid transparent;
}

.device-item:hover {
  border-color: var(--accent);
  background: var(--bg-hover);
}

.device-item.active {
  border-color: var(--accent);
  background: var(--accent-glow);
}

.device-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #36cf77;
  flex-shrink: 0;
  box-shadow: 0 0 8px rgba(54,207,119,0.5);
  animation: pulse-indicator 2s infinite;
}

@keyframes pulse-indicator {
  0%, 100% { box-shadow: 0 0 4px rgba(54,207,119,0.5); }
  50% { box-shadow: 0 0 12px rgba(54,207,119,0.8); }
}

.device-item-info {
  flex: 1;
  min-width: 0;
}

.device-item-name {
  font-weight: 500;
  color: var(--text-primary);
  font-size: 14px;
  margin-bottom: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.device-item-type {
  font-size: 12px;
  color: var(--text-muted);
}

.device-sensor-count {
  font-size: 11px;
  color: var(--accent-light);
  white-space: nowrap;
}

.charts-area {
  background: var(--bg-card);
  border-radius: 8px;
  border: 1px solid var(--border-color);
  padding: 16px;
}

.charts-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-light);
}

.charts-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.pulse-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #36cf77;
  margin-right: 4px;
  vertical-align: middle;
  animation: pulse-indicator 2s infinite;
}

.chart-card {
  background: var(--bg-hover);
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 16px;
  border: 1px solid var(--border-light);
}

.chart-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.sensor-name {
  font-size: 14px;
  color: var(--text-secondary);
  font-weight: 500;
}

.sensor-current {
  font-size: 18px;
  font-weight: bold;
  color: var(--accent-light);
}

.sensor-unit {
  font-size: 12px;
  color: var(--text-muted);
  font-weight: normal;
}

.chart-container {
  width: 100%;
  height: 220px;
}

.sensor-detail-table {
  margin-top: 10px;
}

.sensor-detail-table h4 {
  margin: 0 0 12px;
  font-size: 15px;
  color: var(--text-primary);
}

.value-tag {
  font-weight: bold;
  color: var(--success);
}

.value-tag.alert {
  color: var(--danger);
}

.empty-charts {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 500px;
  background: var(--bg-card);
  border-radius: 8px;
  border: 1px solid var(--border-color);
}

.empty-charts-inner {
  text-align: center;
  color: var(--text-muted);
}

.empty-charts-inner p {
  margin-top: 16px;
  font-size: 15px;
}
</style>

<style scoped>
.monitor { max-width: 1440px; margin: 0 auto; }.page-header { margin-bottom: 24px; }.page-header h2 { font-size: 26px; letter-spacing: -.02em; }.page-header h2::after { content: 'REAL-TIME MONITORING'; display: block; margin-top: 5px; color: var(--color-cyan); font: 10px/1 'Roboto Mono', monospace; letter-spacing: .14em; }.page-header p { margin-top: 10px; color: var(--text-secondary); }
.device-list-card, .charts-area, .empty-charts { border-radius: 8px; background: var(--bg-card); box-shadow: none; }.card-title { padding: 17px; color: var(--text-primary); font-size: 14px; }.card-title::after { content: ' ONLINE NODES'; color: var(--text-muted); font: 10px 'Roboto Mono', monospace; letter-spacing: .08em; }.device-item { border-radius: 6px; }.device-item:hover { border-color: var(--border-color); background: var(--bg-hover); }.device-item.active { border-color: rgba(22,119,255,.5); background: var(--color-primary-soft); box-shadow: inset 3px 0 0 var(--color-primary); }.device-indicator, .pulse-dot { background: var(--color-success); box-shadow: 0 0 0 3px rgba(34,197,94,.12); animation: none; }.device-sensor-count { color: var(--color-cyan); font-family: 'Roboto Mono', monospace; }.charts-area { padding: 20px; }.charts-title { font-size: 16px; }.chart-card { border-radius: 7px; background: var(--bg-secondary); border-color: var(--border-color); }.sensor-current { color: var(--text-primary); font-family: 'Roboto Mono', monospace; }.sensor-detail-table h4 { color: var(--text-primary); }
@media (max-width: 900px) { .monitor :deep(.el-col) { width: 100%; max-width: 100%; flex: 0 0 100%; margin-bottom: 12px; }.device-list { max-height: 220px; }.chart-container { height: 190px; } }
</style>
