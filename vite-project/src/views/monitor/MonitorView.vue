<script setup lang="ts">
defineOptions({ name: 'Monitor' })
import { ref, onMounted, onUnmounted, computed, watch, nextTick } from 'vue'
import { useDeviceStore } from '../../stores/device'
import { useWebSocket, type WsDeviceData } from '../../stores/websocket'
import { TrendCharts } from '@element-plus/icons-vue'
import * as echarts from 'echarts'

const deviceStore = useDeviceStore()
const selectedDeviceId = ref('')
const chartRefs = ref<Map<string, HTMLDivElement>>(new Map())
const chartInstances = ref<Map<string, echarts.ECharts>>(new Map())

function setChartRef(el: unknown, key: string) {
  if (el instanceof HTMLDivElement) {
    chartRefs.value.set(key, el)
  }
}
const chartData = ref<Map<string, { times: string[]; values: number[] }>>(new Map())

const MAX_POINTS = 40
let pollTimer: ReturnType<typeof setInterval> | null = null

const onlineDevices = computed(() => deviceStore.devices.filter(d => d.status === 'online'))

const selectedDevice = computed(() => {
  return deviceStore.devices.find(d => d.id === selectedDeviceId.value)
})

function selectDevice(deviceId: string) {
  selectedDeviceId.value = deviceId
  nextTick(() => initCharts())
}

function getSensorKey(deviceId: string, sensorId: string) {
  return `${deviceId}_${sensorId}`
}

function pushDataPoint(sensorKey: string, value: number) {
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
  if (!selectedDevice.value) return

  for (const sensor of selectedDevice.value.sensors) {
    const sensorKey = getSensorKey(selectedDeviceId.value, sensor.id)
    const container = chartRefs.value.get(sensorKey)
    if (!container) continue

    if (!chartData.value.has(sensorKey)) {
      chartData.value.set(sensorKey, { times: [], values: [] })
    }

    let instance = chartInstances.value.get(sensorKey)
    if (instance) {
      instance.dispose()
    }

    instance = echarts.init(container, undefined, { devicePixelRatio: window.devicePixelRatio })
    chartInstances.value.set(sensorKey, instance)

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
        data: [],
        axisLine: { lineStyle: { color: '#1a3050' } },
        axisTick: { show: false },
        axisLabel: { color: '#8b9cb5', fontSize: 10, show: false },
        splitLine: { show: false }
      },
      yAxis: {
        type: 'value',
        name: sensor.unit,
        min: sensor.min,
        max: sensor.max,
        splitLine: { lineStyle: { color: '#1a3050', type: 'dashed' } },
        axisLabel: { color: '#8b9cb5', fontSize: 10 },
        nameTextStyle: { color: '#8b9cb5', fontSize: 10 }
      },
      series: [{
        data: [],
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
            { yAxis: sensor.max * 0.9, label: { formatter: '阈值', color: '#f56c6c', fontSize: 10 } }
          ]
        }
      }]
    })
  }
}

function updateCharts() {
  if (!selectedDevice.value) return

  for (const sensor of selectedDevice.value.sensors) {
    const sensorKey = getSensorKey(selectedDeviceId.value, sensor.id)
    pushDataPoint(sensorKey, sensor.value)

    const instance = chartInstances.value.get(sensorKey)
    const data = chartData.value.get(sensorKey)
    if (!instance || !data) continue

    instance.setOption({
      xAxis: { data: data.times },
      series: [{ data: data.values }]
    })
  }
}

function pollDeviceData() {
  if (!selectedDevice.value || !selectedDeviceId.value) return

  const fresh = deviceStore.devices.find(d => d.id === selectedDeviceId.value)
  if (fresh) {
    for (const sensor of fresh.sensors) {
      const sensorKey = getSensorKey(selectedDeviceId.value, sensor.id)
      pushDataPoint(sensorKey, sensor.value)
    }
    updateCharts()
  }
}

// WebSocket 推送即时重绘：数据到达毫秒级刷新曲线，不再等待轮询周期
let unsubWsData: (() => void) | null = null

function handleWsData(data: WsDeviceData) {
  if (data.type !== 'data' || !selectedDeviceId.value) return
  if (data.deviceId !== selectedDeviceId.value) return

  const dev = selectedDevice.value
  const sensor = dev?.sensors.find(s => s.id === data.sensorId)
  if (!sensor) return

  sensor.value = data.value
  const sensorKey = getSensorKey(data.deviceId, data.sensorId)
  pushDataPoint(sensorKey, data.value)

  const instance = chartInstances.value.get(sensorKey)
  const cd = chartData.value.get(sensorKey)
  if (instance && cd) {
    instance.setOption({
      xAxis: { data: cd.times },
      series: [{ data: cd.values }]
    })
  }
}

function handleResize() {
  for (const instance of chartInstances.value.values()) {
    instance.resize()
  }
}

watch(selectedDevice, (dev) => {
  if (dev) {
    nextTick(() => {
      initCharts()
      updateCharts()
    })
  }
})

onMounted(async () => {
  await deviceStore.fetchDevices()
  if (onlineDevices.value.length > 0 && !selectedDeviceId.value) {
    selectedDeviceId.value = onlineDevices.value[0].id
    await nextTick()
    initCharts()
  }
  deviceStore.startRealtimeUpdates(2000)
  unsubWsData = useWebSocket().onAllDeviceData(handleWsData)
  pollTimer = setInterval(pollDeviceData, 10000)
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  deviceStore.stopRealtimeUpdates()
  if (unsubWsData) { unsubWsData(); unsubWsData = null }
  if (pollTimer) clearInterval(pollTimer)
  window.removeEventListener('resize', handleResize)
  for (const instance of chartInstances.value.values()) {
    instance.dispose()
  }
  chartInstances.value.clear()
})
</script>

<template>
  <div class="monitor">
    <div class="page-header">
      <h2>实时监控</h2>
      <p>查看设备传感器数据实时变化曲线</p>
    </div>

    <el-row :gutter="20">
      <el-col :span="5">
        <div class="device-list-card">
          <div class="card-title">在线设备</div>
          <div class="device-list">
            <div
              v-for="device in onlineDevices"
              :key="device.id"
              :class="['device-item', { active: selectedDeviceId === device.id }]"
              @click="selectDevice(device.id)"
            >
              <div class="device-indicator" />
              <div class="device-item-info">
                <div class="device-item-name">{{ device.name }}</div>
                <div class="device-item-type">{{ device.type }}</div>
              </div>
              <div class="device-sensor-count">{{ device.sensors.length }} 传感器</div>
            </div>
            <el-empty v-if="onlineDevices.length === 0" description="暂无在线设备" />
          </div>
        </div>
      </el-col>

      <el-col :span="19">
        <div v-if="selectedDevice" class="charts-area">
          <div class="charts-header">
            <span class="charts-title">{{ selectedDevice.name }} — 实时数据曲线</span>
            <el-tag type="success" size="small" effect="dark" round>
              <span class="pulse-dot" /> 实时更新中
            </el-tag>
          </div>

          <el-row :gutter="16">
            <el-col
              v-for="sensor in selectedDevice.sensors"
              :key="sensor.id"
              :span="selectedDevice.sensors.length === 1 ? 24 : selectedDevice.sensors.length === 2 ? 12 : 12"
            >
              <div class="chart-card">
                <div class="chart-card-header">
                  <span class="sensor-name">{{ sensor.name }}</span>
                  <span class="sensor-current">
                    {{ sensor.value.toFixed(2) }}
                    <span class="sensor-unit">{{ sensor.unit }}</span>
                  </span>
                </div>
                <div
                  :ref="(el) => setChartRef(el, `${selectedDeviceId}_${sensor.id}`)"
                  class="chart-container"
                />
              </div>
            </el-col>
          </el-row>

          <div class="sensor-detail-table">
            <h4>传感器数值详情</h4>
            <el-table :data="selectedDevice.sensors" style="width: 100%">
              <el-table-column prop="name" label="传感器名称" />
              <el-table-column prop="type" label="类型" />
              <el-table-column label="当前值">
                <template #default="{ row }">
                  <span :class="['value-tag', { alert: row.value > row.max * 0.9 }]">
                    {{ row.value.toFixed(2) }}
                  </span>
                  {{ row.unit }}
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.value > row.max * 0.9 ? 'danger' : 'success'" size="small">
                    {{ row.value > row.max * 0.9 ? '超限' : '正常' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="min" label="最小值" />
              <el-table-column prop="max" label="最大值" />
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
