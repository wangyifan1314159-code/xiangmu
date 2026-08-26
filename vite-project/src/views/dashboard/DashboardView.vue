<script setup lang="ts">
defineOptions({ name: 'Dashboard' })
import { computed, onMounted, onUnmounted, ref, markRaw } from 'vue'
import { useRouter } from 'vue-router'
import { useDeviceStore } from '../../stores/device'
import { realApi } from '../../api/realApi'
import { Monitor, Warning, CircleCheck, CircleClose, ArrowRight, Connection, DataLine } from '@element-plus/icons-vue'

const router = useRouter()
const deviceStore = useDeviceStore()
const statCards = ref([
  { title: '设备总数', value: 0, icon: markRaw(Monitor), tone: 'blue', key: 'total' },
  { title: '在线设备', value: 0, icon: markRaw(CircleCheck), tone: 'green', key: 'online' },
  { title: '离线设备', value: 0, icon: markRaw(CircleClose), tone: 'slate', key: 'offline' },
  { title: '告警设备', value: 0, icon: markRaw(Warning), tone: 'amber', key: 'warning' }
])
const recentAlerts = ref<{ id: number; device: string; type: string; message: string; time: string }[]>([])
const onlineRate = computed(() => deviceStore.totalCount ? Math.round(deviceStore.onlineCount / deviceStore.totalCount * 100) : 0)
const statusRows = computed(() => [
  { label: '在线', value: deviceStore.onlineCount, tone: 'green' },
  { label: '离线', value: deviceStore.offlineCount, tone: 'slate' },
  { label: '告警', value: deviceStore.warningCount, tone: 'amber' }
])
const telemetryRows = computed(() => deviceStore.devices.flatMap(device =>
  (device.sensors || []).slice(0, 2).map(sensor => ({
    device: device.name,
    sensor: sensor.name,
    value: Number(sensor.value),
    unit: sensor.unit || '-',
    status: device.status
  }))
).slice(0, 8))

let updateTimer: ReturnType<typeof setInterval> | undefined
let alertTimer: ReturnType<typeof setInterval> | undefined

function formatValue(value: number) { return Number.isFinite(value) ? value.toFixed(1) : '--' }
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
    recentAlerts.value = (page?.content || []).map((r: any) => ({
      id: r.id, device: r.deviceName || r.deviceId,
      type: r.level === 'CRITICAL' ? 'error' : r.level === 'WARNING' ? 'warning' : 'info',
      message: r.ruleName || r.title?.split('] ')[1] || '告警触发',
      time: formatRelativeTime(r.triggeredAt)
    }))
  } catch { /* alert panel can remain empty */ }
}
function updateStats() {
  statCards.value[0].value = deviceStore.totalCount
  statCards.value[1].value = deviceStore.onlineCount
  statCards.value[2].value = deviceStore.offlineCount
  statCards.value[3].value = deviceStore.warningCount
}
function go(path: string) { router.push(path) }

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
  if (updateTimer) clearInterval(updateTimer)
  if (alertTimer) clearInterval(alertTimer)
})
</script>

<template>
  <div class="dashboard">
    <section class="hero-row">
      <div>
        <div class="eyebrow"><span class="live-mark" /> OPERATIONS OVERVIEW</div>
        <h1>平台运行概览</h1>
        <p>工业设备接入、状态与实时遥测的统一视图。</p>
      </div>
      <div class="hero-actions">
        <el-button class="secondary-action" @click="go('/connections')"><Connection /> 设备接入</el-button>
        <el-button type="primary" @click="go('/monitor')"><DataLine /> 实时监控 <ArrowRight /></el-button>
      </div>
    </section>

    <section class="stat-grid">
      <article v-for="card in statCards" :key="card.key" class="metric-panel" :class="`tone-${card.tone}`">
        <div class="metric-icon"><el-icon :size="18"><component :is="card.icon" /></el-icon></div>
        <div class="metric-copy"><span>{{ card.title }}</span><strong>{{ card.value }}</strong></div>
        <div class="metric-code">{{ card.key.toUpperCase() }}</div>
      </article>
    </section>

    <section class="primary-grid">
      <article class="panel status-panel">
        <div class="panel-heading"><div><span class="panel-kicker">DEVICE HEALTH</span><h2>设备在线状态</h2></div><span class="panel-meta">{{ onlineRate }}% 在线率</span></div>
        <div class="status-layout">
          <div class="status-ring" :style="{ '--rate': onlineRate + '%' }"><div><strong>{{ onlineRate }}%</strong><span>在线率</span></div></div>
          <div class="status-list">
            <div v-for="row in statusRows" :key="row.label" class="status-row"><span><i :class="['status-dot', row.tone]" />{{ row.label }}</span><strong>{{ row.value }}</strong></div>
          </div>
        </div>
        <button class="text-action" @click="go('/devices')">查看设备中心 <ArrowRight /></button>
      </article>

      <article class="panel alert-panel">
        <div class="panel-heading"><div><span class="panel-kicker">LIVE ALERTS</span><h2>实时告警</h2></div><span class="panel-meta danger-text">{{ recentAlerts.length }} 待处理</span></div>
        <div v-if="recentAlerts.length" class="alert-list">
          <div v-for="alert in recentAlerts" :key="alert.id" class="alert-row"><span :class="['alert-level', alert.type]" /> <div><strong>{{ alert.device }}</strong><p>{{ alert.message }}</p></div><time>{{ alert.time }}</time></div>
        </div>
        <div v-else class="empty-state"><el-icon><CircleCheck /></el-icon><span>当前没有待处理告警</span></div>
      </article>
    </section>

    <section class="panel telemetry-panel">
      <div class="panel-heading"><div><span class="panel-kicker">LATEST TELEMETRY</span><h2>实时遥测</h2></div><span class="panel-meta"><span class="live-mark" /> WebSocket / HTTP</span></div>
      <div v-if="telemetryRows.length" class="telemetry-grid">
        <div v-for="item in telemetryRows" :key="`${item.device}-${item.sensor}`" class="telemetry-row"><div class="telemetry-name"><strong>{{ item.sensor }}</strong><span>{{ item.device }}</span></div><strong class="telemetry-value">{{ formatValue(item.value) }} <small>{{ item.unit }}</small></strong><span :class="['state-label', item.status]">{{ item.status === 'online' ? 'ONLINE' : item.status === 'warning' ? 'WARNING' : 'OFFLINE' }}</span></div>
      </div>
      <div v-else class="empty-state"><el-icon><Monitor /></el-icon><span>暂无实时遥测数据</span></div>
    </section>
  </div>
</template>

<style scoped>
.dashboard { max-width: 1440px; margin: 0 auto; padding-bottom: 24px; }
.hero-row, .panel-heading, .status-row, .alert-row, .telemetry-row { display: flex; align-items: center; justify-content: space-between; }
.hero-row { margin-bottom: 24px; gap: 20px; }
.eyebrow, .panel-kicker, .metric-code { color: var(--text-muted); font: 10px/1.2 'Roboto Mono', monospace; letter-spacing: .12em; }
.eyebrow { display: flex; align-items: center; gap: 8px; margin-bottom: 9px; color: var(--color-cyan); }
.live-mark { width: 6px; height: 6px; display: inline-block; border-radius: 50%; background: var(--color-success); box-shadow: 0 0 0 3px rgba(34,197,94,.12); }
h1 { margin: 0; color: var(--text-primary); font-size: clamp(24px, 2.2vw, 32px); letter-spacing: -.02em; }
.hero-row p { margin: 8px 0 0; color: var(--text-secondary); font-size: 13px; }
.hero-actions { display: flex; gap: 10px; }
.hero-actions :deep(.el-button) { height: 38px; border-radius: 6px; }
.secondary-action { color: var(--text-secondary); background: var(--bg-card); border-color: var(--border-color); }
.secondary-action:hover { color: var(--text-primary); border-color: var(--color-primary); background: var(--bg-hover); }
.stat-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 12px; }
.metric-panel, .panel { background: var(--bg-card); border: 1px solid var(--border-color); border-radius: 8px; }
.metric-panel { position: relative; min-height: 112px; padding: 18px; overflow: hidden; }
.metric-panel::after { content: ''; position: absolute; right: -22px; bottom: -40px; width: 110px; height: 110px; border: 1px solid currentColor; border-radius: 50%; opacity: .07; }
.metric-icon { display: grid; place-items: center; width: 32px; height: 32px; margin-bottom: 16px; border-radius: 6px; background: currentColor; color: var(--text-primary); opacity: .9; }
.tone-blue { color: var(--color-primary); }.tone-green { color: var(--color-success); }.tone-slate { color: var(--text-muted); }.tone-amber { color: var(--color-warning); }
.metric-copy { display: flex; align-items: baseline; gap: 10px; color: var(--text-secondary); }.metric-copy strong { color: var(--text-primary); font: 700 28px/1 'Roboto Mono', monospace; }.metric-copy span { font-size: 13px; }.metric-code { position: absolute; top: 18px; right: 18px; opacity: .7; }
.primary-grid { display: grid; grid-template-columns: minmax(0, 1.3fr) minmax(320px, .7fr); gap: 12px; margin-bottom: 12px; }
.panel { padding: 20px; }.panel-heading { align-items: flex-start; margin-bottom: 20px; }.panel-heading h2 { margin: 5px 0 0; color: var(--text-primary); font-size: 16px; }.panel-meta { color: var(--text-muted); font: 11px 'Roboto Mono', monospace; }.danger-text { color: var(--color-warning); }
.status-layout { display: grid; grid-template-columns: 190px 1fr; align-items: center; gap: 30px; min-height: 150px; }.status-ring { --rate: 0%; display: grid; place-items: center; width: 150px; height: 150px; border-radius: 50%; background: conic-gradient(var(--color-primary) var(--rate), var(--border-color) var(--rate)); }.status-ring > div { display: grid; place-items: center; width: 116px; height: 116px; border-radius: 50%; background: var(--bg-card); }.status-ring strong { color: var(--text-primary); font: 700 28px 'Roboto Mono', monospace; }.status-ring span { color: var(--text-muted); font-size: 11px; }.status-list { display: grid; gap: 18px; }.status-row { padding-bottom: 10px; border-bottom: 1px solid var(--border-light); color: var(--text-secondary); font-size: 13px; }.status-row span { display: flex; align-items: center; gap: 8px; }.status-row strong { color: var(--text-primary); font: 600 16px 'Roboto Mono', monospace; }.status-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--text-muted); }.status-dot.green { background: var(--color-success); }.status-dot.amber { background: var(--color-warning); }.text-action { display: inline-flex; align-items: center; gap: 7px; margin-top: 18px; padding: 0; border: 0; color: var(--color-primary); background: transparent; font-size: 12px; cursor: pointer; }.text-action:hover { color: var(--color-cyan); }
.alert-list { display: grid; gap: 4px; }.alert-row { justify-content: flex-start; gap: 10px; padding: 12px 0; border-bottom: 1px solid var(--border-light); }.alert-row:last-child { border-bottom: 0; }.alert-level { width: 3px; align-self: stretch; border-radius: 3px; background: var(--color-primary); }.alert-level.warning { background: var(--color-warning); }.alert-level.error { background: var(--color-danger); }.alert-row div { flex: 1; min-width: 0; }.alert-row strong { display: block; overflow: hidden; color: var(--text-primary); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }.alert-row p { margin: 3px 0 0; overflow: hidden; color: var(--text-muted); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }.alert-row time { color: var(--text-muted); font: 10px 'Roboto Mono', monospace; white-space: nowrap; }.empty-state { display: flex; min-height: 150px; flex-direction: column; align-items: center; justify-content: center; gap: 8px; color: var(--text-muted); font-size: 12px; }.empty-state .el-icon { color: var(--color-success); font-size: 26px; }
.telemetry-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 32px; }.telemetry-row { min-height: 62px; gap: 12px; border-bottom: 1px solid var(--border-light); }.telemetry-name { display: grid; flex: 1; min-width: 0; gap: 4px; }.telemetry-name strong { overflow: hidden; color: var(--text-primary); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }.telemetry-name span { overflow: hidden; color: var(--text-muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }.telemetry-value { color: var(--text-primary); font: 600 16px 'Roboto Mono', monospace; white-space: nowrap; }.telemetry-value small { color: var(--text-muted); font-size: 10px; font-weight: 400; }.state-label { color: var(--text-muted); font: 10px 'Roboto Mono', monospace; }.state-label.online { color: var(--color-success); }.state-label.warning { color: var(--color-warning); }
@media (max-width: 900px) { .stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }.primary-grid { grid-template-columns: 1fr; }.telemetry-grid { grid-template-columns: 1fr; } }
@media (max-width: 600px) { .hero-row { display: block; }.hero-actions { margin-top: 16px; }.hero-actions :deep(.el-button) { flex: 1; }.panel { padding: 16px; }.status-layout { grid-template-columns: 1fr; justify-items: center; }.status-list { width: 100%; }.metric-panel { min-height: 100px; padding: 14px; }.metric-copy { display: block; }.metric-copy strong { display: block; margin-bottom: 4px; }.metric-code { display: none; } }
</style>
