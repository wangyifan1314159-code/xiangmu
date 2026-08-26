<script setup lang="ts">
defineOptions({ name: 'Dashboard' })
import { ref, onMounted, onUnmounted, markRaw } from 'vue'
import { useDeviceStore } from '../../stores/device'
import { useRouter } from 'vue-router'
import { realApi } from '../../api/realApi'
import {
  Monitor,
  Warning,
  CircleCheck,
  CircleClose
} from '@element-plus/icons-vue'

const deviceStore = useDeviceStore()
const router = useRouter()

const statCards = ref([
  { title: '设备总数', value: 0, icon: markRaw(Monitor), color: 'var(--sensor-color)', bg: 'var(--sensor-bg)', key: 'total' },
  { title: '在线设备', value: 0, icon: markRaw(CircleCheck), color: 'var(--success)', bg: 'rgba(52,211,153,0.12)', key: 'online' },
  { title: '离线设备', value: 0, icon: markRaw(CircleClose), color: 'var(--text-muted)', bg: 'var(--bg-hover)', key: 'offline' },
  { title: '告警设备', value: 0, icon: markRaw(Warning), color: 'var(--warning)', bg: 'rgba(251,191,36,0.12)', key: 'warning' }
])

const recentAlerts = ref<{ id: number; device: string; type: string; message: string; time: string }[]>([])

let updateTimer: ReturnType<typeof setInterval>
let alertTimer: ReturnType<typeof setInterval>

function formatRelativeTime(ts: string) {
  const diff = Date.now() - new Date(ts).getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`
  return new Date(ts).toLocaleDateString()
}

async function fetchRecentAlerts() {
  try {
    const page = await realApi.getAlertRecords({ status: 'TRIGGERED', size: 5, page: 0 })
    const records = page?.content || []
    recentAlerts.value = records.map((r: any) => ({
      id: r.id,
      device: r.deviceName || r.deviceId,
      type: r.level === 'CRITICAL' ? 'error' : r.level === 'WARNING' ? 'warning' : 'info',
      message: r.ruleName || r.title?.split('] ')[1] || '告警触发',
      time: formatRelativeTime(r.triggeredAt)
    }))
  } catch { /* ignore */ }
}

function updateStats() {
  statCards.value[0].value = deviceStore.totalCount
  statCards.value[1].value = deviceStore.onlineCount
  statCards.value[2].value = deviceStore.offlineCount
  statCards.value[3].value = deviceStore.warningCount
}

function goToDevices() {
  router.push('/devices')
}

function goToMonitor() {
  router.push('/monitor')
}

onMounted(async () => {
  await deviceStore.fetchDevices()
  updateStats()
  await fetchRecentAlerts()
  deviceStore.startRealtimeUpdates(5000)
  updateTimer = setInterval(updateStats, 5000)
  alertTimer = setInterval(fetchRecentAlerts, 15000)
})

onUnmounted(() => {
  deviceStore.stopRealtimeUpdates()
  clearInterval(updateTimer)
  clearInterval(alertTimer)
})
</script>

<template>
  <div class="dashboard">
    <div class="page-header">
      <h2>数据大屏</h2>
      <p>实时监控平台运行状态</p>
    </div>

    <el-row :gutter="20" class="stat-row">
      <el-col :span="6" v-for="card in statCards" :key="card.key">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon" :style="{ backgroundColor: card.bg, color: card.color }">
              <el-icon :size="28"><component :is="card.icon" /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ card.value }}</div>
              <div class="stat-title">{{ card.title }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="content-row">
      <el-col :span="16">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>设备状态分布</span>
              <el-button link type="primary" @click="goToDevices">查看全部</el-button>
            </div>
          </template>
          <div class="device-status-chart">
            <div class="status-item">
              <div class="status-circle online" :style="{ '--percent': (deviceStore.onlineCount / Math.max(deviceStore.totalCount, 1) * 100) + '%' }">
                <div class="status-inner">
                  <span class="status-num">{{ deviceStore.onlineCount }}</span>
                  <span class="status-label">在线</span>
                </div>
              </div>
            </div>
            <div class="status-item">
              <div class="status-circle offline" :style="{ '--percent': (deviceStore.offlineCount / Math.max(deviceStore.totalCount, 1) * 100) + '%' }">
                <div class="status-inner">
                  <span class="status-num">{{ deviceStore.offlineCount }}</span>
                  <span class="status-label">离线</span>
                </div>
              </div>
            </div>
            <div class="status-item">
              <div class="status-circle warning" :style="{ '--percent': (deviceStore.warningCount / Math.max(deviceStore.totalCount, 1) * 100) + '%' }">
                <div class="status-inner">
                  <span class="status-num">{{ deviceStore.warningCount }}</span>
                  <span class="status-label">告警</span>
                </div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card class="alert-card">
          <template #header>
            <div class="card-header">
              <span>最近告警</span>
              <el-button link type="primary" @click="goToMonitor">更多</el-button>
            </div>
          </template>
          <div class="alert-list">
            <div v-for="alert in recentAlerts" :key="alert.id" class="alert-item">
              <el-tag :type="alert.type === 'warning' ? 'warning' : alert.type === 'error' ? 'danger' : 'info'" size="small">
                {{ alert.type === 'warning' ? '警告' : alert.type === 'error' ? '异常' : '信息' }}
              </el-tag>
              <div class="alert-content">
                <div class="alert-device">{{ alert.device }}</div>
                <div class="alert-message">{{ alert.message }}</div>
              </div>
              <div class="alert-time">{{ alert.time }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="content-row">
      <el-col :span="24">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>设备列表概览</span>
              <el-button link type="primary" @click="goToDevices">管理设备</el-button>
            </div>
          </template>
          <el-table :data="deviceStore.devices.slice(0, 5)" style="width: 100%">
            <el-table-column prop="name" label="设备名称" min-width="150" />
            <el-table-column prop="type" label="设备类型" min-width="120" />
            <el-table-column prop="location" label="位置" min-width="120" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'online' ? 'success' : row.status === 'offline' ? 'info' : 'warning'" size="small">
                  {{ row.status === 'online' ? '在线' : row.status === 'offline' ? '离线' : '告警' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="传感器数据" min-width="200">
              <template #default="{ row }">
                <div class="sensor-tags">
                  <el-tag v-for="sensor in row.sensors" :key="sensor.id" size="small" type="info" class="sensor-tag">
                    {{ sensor.name }}: {{ sensor.value.toFixed(1) }}{{ sensor.unit }}
                  </el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="lastActive" label="最后活跃" min-width="160">
              <template #default="{ row }">
                {{ new Date(row.lastActive).toLocaleString() }}
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.dashboard {
  padding-bottom: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 8px;
  font-size: 22px;
  color: var(--text-primary);
}

.page-header p {
  margin: 0;
  color: var(--text-muted);
  font-size: 14px;
}

.stat-row {
  margin-bottom: 20px;
}

.stat-card {
  cursor: pointer;
  transition: transform 0.3s;
}

.stat-card:hover {
  transform: translateY(-3px);
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 15px;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: var(--text-primary);
  line-height: 1.2;
}

.stat-title {
  font-size: 14px;
  color: var(--text-muted);
  margin-top: 4px;
}

.content-row {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 500;
}

.device-status-chart {
  display: flex;
  justify-content: space-around;
  padding: 30px 0;
}

.status-item {
  text-align: center;
}

.status-circle {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  background: conic-gradient(var(--color) var(--percent), #f0f2f5 var(--percent));
}

.status-circle.online { --color: var(--success); }
.status-circle.offline { --color: var(--text-muted); }
.status-circle.warning { --color: var(--warning); }

.status-inner {
  width: 90px;
  height: 90px;
  background: var(--bg-card);
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.status-num {
  font-size: 24px;
  font-weight: bold;
  color: var(--text-primary);
}

.status-label {
  font-size: 13px;
  color: var(--text-muted);
  margin-top: 2px;
}

.alert-list {
  max-height: 300px;
  overflow-y: auto;
}

.alert-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border-light);
}

.alert-item:last-child {
  border-bottom: none;
}

.alert-content {
  flex: 1;
  min-width: 0;
}

.alert-device {
  font-size: 14px;
  color: var(--text-primary);
  font-weight: 500;
}

.alert-message {
  font-size: 13px;
  color: var(--text-muted);
  margin-top: 2px;
}

.alert-time {
  font-size: 12px;
  color: var(--text-muted);
  opacity: 0.7;
  white-space: nowrap;
}

.sensor-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.sensor-tag {
  font-size: 12px;
}
</style>
