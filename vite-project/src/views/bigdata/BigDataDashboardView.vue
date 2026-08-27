<script setup lang="ts">
import { computed, onMounted, onUnmounted, onActivated, onDeactivated, ref } from 'vue'
import { useDeviceStore } from '../../stores/device'
import { realApi } from '../../api/realApi'
import { ElMessage } from 'element-plus'
import { DataAnalysis, Refresh, WarningFilled, Connection, CircleCheck } from '@element-plus/icons-vue'

const deviceStore = useDeviceStore()
const alertStats = ref<Record<string, number>>({})
const loading = ref(false)
let refreshTimer: ReturnType<typeof setInterval> | undefined

const onlineRate = computed(() => deviceStore.totalCount ? Math.round(deviceStore.onlineCount / deviceStore.totalCount * 100) : 0)
const healthRows = computed(() => deviceStore.devices.slice(0, 6).map(device => ({ id: device.id, name: device.name, status: device.status })))
const triggeredAlerts = computed(() => alertStats.value.triggered || alertStats.value.TRIGGERED || 0)

let hasShownError = false

async function refresh() {
  loading.value = true
  try {
    const [, stats] = await Promise.all([deviceStore.fetchDevices(), realApi.getAlertStats()])
    alertStats.value = stats || {}
    hasShownError = false
  } catch (e: any) {
    // 轮询每 15 秒触发一次，仅在错误状态切换时提示一次，避免刷屏
    if (!hasShownError) {
      ElMessage.error('数据刷新失败: ' + (e?.message || '未知错误'))
      hasShownError = true
    }
  } finally { loading.value = false }
}
function statusLabel(status: string) { return status === 'online' ? '在线' : status === 'warning' ? '告警' : '离线' }
function statusType(status: string) { return status === 'online' ? 'success' : status === 'warning' ? 'warning' : 'info' }

function startPolling() {
  stopPolling()
  refreshTimer = setInterval(refresh, 15000)
}
function stopPolling() {
  if (refreshTimer) { clearInterval(refreshTimer); refreshTimer = undefined }
}

onMounted(() => { refresh(); startPolling() })
onActivated(() => { startPolling() })
onDeactivated(() => { stopPolling() })
onUnmounted(() => { stopPolling() })
</script>

<template>
  <div class="bigdata-dashboard">
    <section class="page-header"><div><span class="page-kicker">DATA PLATFORM / LIVE STATUS</span><h1>数据大屏</h1><p>设备接入、告警与湖仓链路状态的统一视图。</p></div><el-button :icon="Refresh" :loading="loading" @click="refresh">刷新数据</el-button></section>
    <section class="metric-grid">
      <article class="metric-card blue"><Connection /><span>接入设备</span><strong>{{ deviceStore.totalCount }}</strong><small>来自设备管理接口</small></article>
      <article class="metric-card green"><CircleCheck /><span>在线设备</span><strong>{{ deviceStore.onlineCount }}</strong><small>在线率 {{ onlineRate }}%</small></article>
      <article class="metric-card amber"><WarningFilled /><span>待处理告警</span><strong>{{ triggeredAlerts }}</strong><small>来自告警记录接口</small></article>
      <article class="metric-card muted"><DataAnalysis /><span>湖仓数据</span><strong>--</strong><small>尚未接入指标 API</small></article>
    </section>
    <section class="content-grid">
      <article class="panel telemetry-panel"><div class="panel-heading"><div><span>STREAM PROCESSING</span><h2>流式数据概览</h2></div><em>真实数据源</em></div><div class="no-data"><el-icon :size="32"><DataAnalysis /></el-icon><strong>暂无湖仓实时指标</strong><p>当前后端未提供 Flink、Kafka 或 MinIO 的指标接口，页面不会以模拟数据替代。</p></div></article>
      <article class="panel"><div class="panel-heading"><div><span>DEVICE STATUS</span><h2>设备运行状态</h2></div><em>{{ healthRows.length }} 台</em></div><el-table :data="healthRows" size="small" empty-text="暂无设备数据"><el-table-column prop="name" label="设备名称" min-width="130" /><el-table-column prop="id" label="设备 ID" min-width="130" /><el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag></template></el-table-column></el-table></article>
    </section>
    <section class="panel pipeline"><div class="panel-heading"><div><span>PIPELINE</span><h2>数据链路</h2></div><em>按部署状态展示</em></div><el-steps :active="2" align-center finish-status="success"><el-step title="设备接入" description="设备 API、TCP 通道与 WebSocket" /><el-step title="平台服务" description="告警、遥测数据与业务接口" /><el-step title="流处理" description="等待指标接口接入" /><el-step title="湖仓服务" description="等待指标接口接入" /></el-steps></section>
  </div>
</template>

<style scoped>
.bigdata-dashboard { max-width: 1440px; margin: 0 auto; padding-bottom: 24px; }.page-header, .panel-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }.page-header { margin-bottom: 24px; }.page-kicker, .panel-heading span { color: var(--color-cyan); font: 10px/1.2 'Roboto Mono', monospace; letter-spacing: .12em; }.page-header h1 { margin: 7px 0 0; color: var(--text-primary); font-size: 30px; }.page-header p { margin: 8px 0 0; color: var(--text-secondary); font-size: 13px; }.page-header :deep(.el-button) { border-radius: 6px; }.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 12px; }.metric-card, .panel { background: var(--bg-card); border: 1px solid var(--border-color); border-radius: 8px; }.metric-card { position: relative; display: grid; grid-template-columns: 32px 1fr; gap: 0 12px; min-height: 122px; padding: 18px; color: var(--text-secondary); }.metric-card > svg { grid-row: span 2; width: 32px; height: 32px; padding: 8px; border-radius: 6px; background: currentColor; color: var(--bg-card); }.metric-card strong { color: var(--text-primary); font: 700 27px/1.1 'Roboto Mono', monospace; }.metric-card small { grid-column: 1 / -1; align-self: end; color: var(--text-muted); font-size: 11px; }.metric-card.blue { color: var(--color-primary); }.metric-card.green { color: var(--color-success); }.metric-card.amber { color: var(--color-warning); }.metric-card.muted { color: var(--text-muted); }.content-grid { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(340px, .9fr); gap: 12px; margin-bottom: 12px; }.panel { padding: 20px; }.panel-heading { margin-bottom: 18px; }.panel-heading h2 { margin: 6px 0 0; color: var(--text-primary); font-size: 16px; }.panel-heading em { color: var(--text-muted); font: 11px 'Roboto Mono', monospace; font-style: normal; }.no-data { display: grid; min-height: 235px; place-content: center; justify-items: center; text-align: center; color: var(--text-muted); }.no-data :deep(.el-icon) { margin-bottom: 12px; color: var(--color-primary); }.no-data strong { color: var(--text-primary); font-size: 14px; }.no-data p { max-width: 330px; margin: 8px 0 0; font-size: 12px; line-height: 1.7; }.pipeline { padding-bottom: 34px; }.pipeline :deep(.el-step__title) { color: var(--text-primary); font-size: 13px; }.pipeline :deep(.el-step__description) { color: var(--text-muted); font-size: 11px; }@media (max-width: 900px) { .metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }.content-grid { grid-template-columns: 1fr; } }@media (max-width: 600px) { .page-header { align-items: stretch; flex-direction: column; }.metric-grid { grid-template-columns: 1fr; }.pipeline { overflow-x: auto; }.pipeline :deep(.el-steps) { min-width: 660px; } }
</style>
