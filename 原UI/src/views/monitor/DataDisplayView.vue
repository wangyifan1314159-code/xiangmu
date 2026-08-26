<script setup lang="ts">
defineOptions({ name: 'DataDisplay' })
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useDeviceStore } from '../../stores/device'
import {
  Monitor, CircleCheck, Odometer, Clock, Refresh, VideoPlay, VideoPause
} from '@element-plus/icons-vue'

const deviceStore = useDeviceStore()

const selectedDeviceId = ref('')
const autoRefresh = ref(true)
const refreshInterval = ref(5)
const lastUpdateTime = ref('')

let pollTimer: ReturnType<typeof setInterval> | null = null

const deviceGroups = computed(() => {
  const devices = selectedDeviceId.value
    ? deviceStore.devices.filter(d => d.id === selectedDeviceId.value)
    : deviceStore.devices

  return devices.map(device => ({
    device,
    sensors: (device.sensors || []).filter((s: any) => !s.isActuator)
  })).filter(g => g.sensors.length > 0)
})

const totalSensorCount = computed(() =>
  deviceGroups.value.reduce((sum, g) => sum + g.sensors.length, 0)
)

const sensorTypeLabels: Record<string, string> = {
  temperature: '温度', humidity: '湿度', light: '光照', pm25: 'PM2.5',
  co2: 'CO₂', tvoc: 'TVOC', voltage: '电压', current: '电流',
  power: '功率', smoke: '烟雾', water: '水浸', pressure: '气压',
  windspeed: '风速', noise: '噪声'
}

function getSensorTypeLabel(type: string) {
  return sensorTypeLabels[type] || type
}

function getGaugeStatus(sensor: any) {
  const ratio = (sensor.value - sensor.min) / Math.max(sensor.max - sensor.min, 0.01)
  if (ratio > 1) return 'danger'
  if (ratio > 0.85) return 'warning'
  return 'success'
}

function getGaugePercent(sensor: any) {
  return Math.min(100, Math.max(0, (sensor.value - sensor.min) / Math.max(sensor.max - sensor.min, 0.01) * 100))
}

function getGaugeColor(status: string) {
  if (status === 'danger') return '#f56c6c'
  if (status === 'warning') return '#e6a23c'
  return 'var(--success)'
}

function getStatusText(status: string) {
  if (status === 'danger') return '超限'
  if (status === 'warning') return '预警'
  return '正常'
}

function formatTime(dateStr: string) {
  if (!dateStr) return '--'
  return new Date(dateStr).toLocaleTimeString('zh-CN', { hour12: false })
}

async function refreshData() {
  await deviceStore.fetchDevices()
  lastUpdateTime.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(refreshData, refreshInterval.value * 1000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function toggleAutoRefresh() {
  autoRefresh.value = !autoRefresh.value
  if (autoRefresh.value) {
    startPolling()
  } else {
    stopPolling()
  }
}

onMounted(async () => {
  await deviceStore.fetchDevices()
  lastUpdateTime.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  if (autoRefresh.value) startPolling()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <div class="data-display">
    <div class="page-header">
      <div>
        <h2>实时数据展示</h2>
        <p>监测所有设备的传感器实时数据</p>
      </div>
      <div class="header-actions">
        <span v-if="lastUpdateTime" class="last-update">
          <el-icon :size="14"><Clock /></el-icon>
          上次更新: {{ lastUpdateTime }}
        </span>
        <el-button :icon="Refresh" @click="refreshData" :loading="deviceStore.loading">
          刷新
        </el-button>
      </div>
    </div>

    <!-- Stat Cards -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon-box" style="background: var(--sensor-bg); color: var(--sensor-color);">
            <el-icon :size="22"><Monitor /></el-icon>
          </div>
          <div class="stat-body">
            <div class="stat-value">{{ deviceStore.totalCount }}</div>
            <div class="stat-label">设备总数</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon-box" style="background: rgba(54,207,119,0.1); color: var(--success);">
            <el-icon :size="22"><CircleCheck /></el-icon>
          </div>
          <div class="stat-body">
            <div class="stat-value">{{ deviceStore.onlineCount }}</div>
            <div class="stat-label">在线设备</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon-box" style="background: var(--sensor-bg); color: var(--sensor-color);">
            <el-icon :size="22"><Odometer /></el-icon>
          </div>
          <div class="stat-body">
            <div class="stat-value">{{ totalSensorCount }}</div>
            <div class="stat-label">数据采集点</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon-box" style="background: rgba(230,162,60,0.1); color: #e6a23c;">
            <el-icon :size="22"><Clock /></el-icon>
          </div>
          <div class="stat-body">
            <div class="stat-value">{{ refreshInterval }}s</div>
            <div class="stat-label">刷新间隔</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- Filter & Control Bar -->
    <div class="filter-bar">
      <div class="filter-left">
        <el-select v-model="selectedDeviceId" placeholder="全部设备" clearable style="width: 200px" size="default">
          <el-option
            v-for="d in deviceStore.devices"
            :key="d.id"
            :label="d.name"
            :value="d.id"
          >
            <span style="display: flex; align-items: center; gap: 6px;">
              <span class="status-dot" :class="d.status" />
              {{ d.name }}
            </span>
          </el-option>
        </el-select>
        <span class="device-count">{{ deviceGroups.length }} 台设备，{{ totalSensorCount }} 个传感器</span>
      </div>
      <div class="filter-right">
        <el-input-number
          v-model="refreshInterval"
          :min="1"
          :max="60"
          size="default"
          style="width: 110px"
          :disabled="autoRefresh"
        />
        <span class="interval-unit">秒</span>
        <el-button
          :type="autoRefresh ? 'danger' : 'success'"
          @click="toggleAutoRefresh"
        >
          <el-icon><component :is="autoRefresh ? VideoPause : VideoPlay" /></el-icon>
          {{ autoRefresh ? '停止自动刷新' : '开启自动刷新' }}
        </el-button>
      </div>
    </div>

    <!-- Empty State -->
    <div v-if="deviceGroups.length === 0" class="empty-state">
      <div class="empty-state-inner">
        <div class="empty-icon-ring">
          <el-icon :size="48" color="var(--text-muted)"><Odometer /></el-icon>
        </div>
        <h3>暂无传感器数据</h3>
        <p>请先在设备管理中配置传感器与执行器</p>
      </div>
    </div>

    <!-- Device Sensor Gauge Cards -->
    <div v-else class="gauge-grid">
      <div
        v-for="group in deviceGroups"
        :key="group.device.id"
        class="device-gauge-card"
      >
        <div class="card-header">
          <div class="card-header-left">
            <span class="card-status-dot" :class="group.device.status" />
            <div>
              <div class="card-device-name">{{ group.device.name }}</div>
              <div class="card-device-type">{{ group.device.type }}</div>
            </div>
          </div>
          <div class="card-header-right">
            <span
              v-if="autoRefresh && group.device.status === 'online'"
              class="live-badge"
            >
              <span class="live-dot" /> 实时
            </span>
            <span class="card-time">{{ formatTime(group.device.lastActive) }}</span>
          </div>
        </div>

        <div class="gauge-row">
          <div
            v-for="sensor in group.sensors"
            :key="sensor.id"
            class="gauge-item"
          >
            <div
              :class="['gauge-circle', 'gauge-' + getGaugeStatus(sensor)]"
              :style="{
                '--percent': getGaugePercent(sensor) + '%',
                '--color': getGaugeColor(getGaugeStatus(sensor))
              }"
            >
              <div class="gauge-inner">
                <span :class="['gauge-value', 'text-' + getGaugeStatus(sensor)]">
                  {{ sensor.value.toFixed(1) }}
                </span>
                <span v-if="sensor.unit" class="gauge-unit">{{ sensor.unit }}</span>
              </div>
            </div>
            <div class="gauge-info">
              <div class="gauge-name">{{ sensor.name }}</div>
              <div class="gauge-meta">
                <span :class="['gauge-tag', 'tag-' + getGaugeStatus(sensor)]">
                  {{ getStatusText(getGaugeStatus(sensor)) }}
                </span>
                <span class="gauge-type">{{ getSensorTypeLabel(sensor.type) }}</span>
              </div>
              <div class="gauge-range">范围: {{ sensor.min }} ~ {{ sensor.max }} {{ sensor.unit }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.data-display {
  padding-bottom: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
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

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.last-update {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--text-muted);
}

/* Stat Cards */
.stat-row {
  margin-bottom: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  transition: transform 0.2s, box-shadow 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow);
}

.stat-icon-box {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.stat-label {
  font-size: 13px;
  color: var(--text-muted);
  margin-top: 2px;
}

/* Filter Bar */
.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  margin-bottom: 20px;
}

.filter-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.filter-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.interval-unit {
  font-size: 13px;
  color: var(--text-muted);
}

.device-count {
  font-size: 13px;
  color: var(--text-muted);
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
  display: inline-block;
}

.status-dot.online { background: var(--success); box-shadow: 0 0 5px rgba(54,207,119,0.5); }
.status-dot.offline { background: #909399; }
.status-dot.warning { background: #e6a23c; box-shadow: 0 0 5px rgba(230,162,60,0.5); }

/* Empty State */
.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 420px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.empty-state-inner {
  text-align: center;
}

.empty-icon-ring {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  background: var(--bg-hover);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px;
}

.empty-state-inner h3 {
  margin: 0 0 8px;
  font-size: 18px;
  color: var(--text-primary);
  font-weight: 600;
}

.empty-state-inner p {
  color: var(--text-muted);
  font-size: 14px;
}

/* Device Gauge Cards */
.gauge-grid {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.device-gauge-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  overflow: hidden;
  border-top: 3px solid var(--sensor-color);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  background: var(--bg-hover);
  border-bottom: 1px solid var(--border-light);
}

.card-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.card-status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.card-status-dot.online {
  background: var(--success);
  box-shadow: 0 0 8px rgba(54,207,119,0.5);
  animation: pulse-dot 2s infinite;
}

.card-status-dot.offline { background: #909399; }

.card-status-dot.warning {
  background: #e6a23c;
  box-shadow: 0 0 8px rgba(230,162,60,0.5);
  animation: pulse-dot 2s infinite;
}

@keyframes pulse-dot {
  0%, 100% { box-shadow: 0 0 4px currentColor; }
  50% { box-shadow: 0 0 12px currentColor; }
}

.card-device-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.3;
}

.card-device-type {
  font-size: 12px;
  color: var(--text-muted);
}

.card-header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.live-badge {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--success);
  font-weight: 500;
  background: rgba(54,207,119,0.1);
  padding: 3px 10px;
  border-radius: 12px;
}

.live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--success);
  animation: pulse-dot 1.5s infinite;
}

.card-time {
  font-size: 12px;
  color: var(--text-muted);
}

/* Gauge Row */
.gauge-row {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  padding: 24px 20px;
  justify-content: center;
}

/* Individual Gauge */
.gauge-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  min-width: 120px;
}

.gauge-circle {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  position: relative;
  background: conic-gradient(
    var(--color) 0deg,
    var(--color) calc(var(--percent) * 3.6),
    var(--border-light) calc(var(--percent) * 3.6),
    var(--border-light) 360deg
  );
  transition: background 0.6s ease;
}

.gauge-circle.gauge-success { --color: var(--success); }
.gauge-circle.gauge-warning { --color: #e6a23c; }
.gauge-circle.gauge-danger  { --color: #f56c6c; }

.gauge-inner {
  position: absolute;
  top: 12px;
  left: 12px;
  right: 12px;
  bottom: 12px;
  background: var(--bg-card);
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
}

.gauge-value {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.gauge-value.text-success { color: var(--success); }
.gauge-value.text-warning { color: #e6a23c; }
.gauge-value.text-danger  { color: #f56c6c; }

.gauge-unit {
  font-size: 11px;
  color: var(--text-muted);
}

.gauge-info {
  text-align: center;
}

.gauge-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.gauge-meta {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-bottom: 2px;
}

.gauge-tag {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.gauge-tag.tag-success {
  background: rgba(54,207,119,0.12);
  color: var(--success);
}

.gauge-tag.tag-warning {
  background: rgba(230,162,60,0.12);
  color: #e6a23c;
}

.gauge-tag.tag-danger {
  background: rgba(245,108,108,0.12);
  color: #f56c6c;
}

.gauge-type {
  font-size: 11px;
  color: var(--text-muted);
}

.gauge-range {
  font-size: 11px;
  color: var(--text-muted);
  opacity: 0.7;
}
</style>
