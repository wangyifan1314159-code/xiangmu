<script setup lang="ts">
defineOptions({ name: 'BigDataDashboard' })
import { computed, onMounted, onUnmounted, onActivated, onDeactivated, ref, watch } from 'vue'
import { useDeviceStore } from '../../stores/device'
import { useWebSocket, type WsDeviceData } from '../../stores/websocket'
import { realApi } from '../../api/realApi'
import { ElMessage } from 'element-plus'
import { DataAnalysis, Refresh, WarningFilled, Connection, CircleCheck, Link } from '@element-plus/icons-vue'

const deviceStore = useDeviceStore()
const ws = useWebSocket()
const alertStats = ref<Record<string, number>>({})
const loading = ref(false)

// ── 掘进机 TCP 连接状态（每 5 秒轮询） ──────────────────────────────────────
const binaryConnections = ref<any[]>([])
let tcpPollTimer: ReturnType<typeof setInterval> | undefined

/**
 * 【真实数据链路：入口检测】
 * 检查是否有有效掘进机连接且近 5 分钟内有帧传输
 * true  → 启用流式数据展示（优先 WebSocket 实时推送，降级时采用短轮询真实入库点）
 * false → 显示"连接中断/无数据"，严禁凭空伪造任何流数据
 */
const hasTcpData = computed(() =>
  binaryConnections.value.some(conn =>
    conn.status === 'CONNECTED' &&
    (conn.validFrameCount || 0) > 0 &&
    conn.lastFrameAt && (Date.now() - conn.lastFrameAt) < 5 * 60 * 1000
  )
)

const connectedTcpCount = computed(() =>
  binaryConnections.value.filter(c => c.status === 'CONNECTED').length
)

async function fetchTcpConnections() {
  try {
    const list = await realApi.getBinaryTcpConnections()
    binaryConnections.value = Array.isArray(list) ? list : []
  } catch { /* TCP 查询失败不影响大屏其他区域 */ }
}

// ── 【真实数据链路】WebSocket 实时流数据 ────────────────────────────────────
// 数据来源：掘进机 TCP 帧 → Netty Decoder → DataService → WebSocketPushService → 前端 WebSocket 订阅
interface StreamEntry { time: string; deviceId: string; sensorId: string; value: number; unit: string }
const MAX_STREAM = 20
const streamEntries = ref<StreamEntry[]>([])
let wsUnsubscribe: (() => void) | null = null
let fallbackTimer: ReturnType<typeof setInterval> | undefined
let lastStreamReceivedAt = 0

function appendStream(data: WsDeviceData) {
  if (data.type !== 'data') return
  lastStreamReceivedAt = Date.now()
  streamEntries.value.unshift({
    time: new Date().toLocaleTimeString(),
    deviceId: data.deviceId,
    sensorId: data.sensorId,
    value: typeof data.value === 'number' ? data.value : 0,
    unit: data.unit || ''
  })
  if (streamEntries.value.length > MAX_STREAM) streamEntries.value.length = MAX_STREAM
}

/**
 * 【初始与降级真实数据加载】
 * 当 TCP 处于已连接且有帧状态时，从最新入库数据中填充大屏表格，避免页面初始等待
 */
async function loadRecentTcpData() {
  if (!hasTcpData.value) return
  const activeConn = binaryConnections.value.find(c => c.status === 'CONNECTED' && (c.validFrameCount || 0) > 0)
  if (!activeConn?.deviceId) return

  try {
    const recent = await realApi.getDeviceData(activeConn.deviceId, undefined, 10)
    if (Array.isArray(recent) && recent.length > 0 && streamEntries.value.length === 0) {
      streamEntries.value = recent.map((item: any) => ({
        time: item.timestamp ? new Date(item.timestamp).toLocaleTimeString() : new Date().toLocaleTimeString(),
        deviceId: item.deviceId || activeConn.deviceId,
        sensorId: item.sensorId || 'sensor',
        value: typeof item.value === 'number' ? item.value : 0,
        unit: item.unit || ''
      }))
    }
  } catch { /* 忽略初始拉取异常 */ }
}

/**
 * 【降级链路逻辑】
 * 只有在满足 hasTcpData === true（TCP 已连接且有帧）的前提下：
 * 若 WebSocket 超过 6s 未收到新推送，采用短轮询自动补充最新数据帧，保证大屏流式刷新平滑
 */
async function checkFallbackStream() {
  if (!hasTcpData.value) {
    if (streamEntries.value.length > 0 && !binaryConnections.value.some(c => c.status === 'CONNECTED')) {
      streamEntries.value = [] // TCP 断开后清除数据，严格呈现无数据状态
    }
    return
  }

  // 若 WS 超过 6 秒无新数据推入，拉取最新真实数据
  if (Date.now() - lastStreamReceivedAt > 6000) {
    const activeConn = binaryConnections.value.find(c => c.status === 'CONNECTED' && (c.validFrameCount || 0) > 0)
    if (!activeConn?.deviceId) return
    try {
      const latest = await realApi.getDeviceData(activeConn.deviceId, undefined, 5)
      if (Array.isArray(latest) && latest.length > 0) {
        for (const item of latest) {
          const exists = streamEntries.value.some(
            e => e.sensorId === item.sensorId && Math.abs(e.value - (item.value || 0)) < 0.0001
          )
          if (!exists) {
            appendStream({
              type: 'data',
              deviceId: item.deviceId || activeConn.deviceId,
              sensorId: item.sensorId || 'sensor',
              value: item.value || 0,
              unit: item.unit || '',
              timestamp: item.timestamp || new Date().toISOString()
            })
          }
        }
      }
    } catch { /* 降级轮询失败不影响页面 */ }
  }
}

// ── 设备摘要 & 告警（HTTP 轮询 15s） ─────────────────────────────────────────
let refreshTimer: ReturnType<typeof setInterval> | undefined
let hasShownError = false

const onlineRate = computed(() =>
  deviceStore.totalCount ? Math.round(deviceStore.onlineCount / deviceStore.totalCount * 100) : 0
)
const healthRows = computed(() =>
  deviceStore.devices.slice(0, 6).map(d => ({ id: d.id, name: d.name, status: d.status }))
)
const triggeredAlerts = computed(() => alertStats.value.triggered || alertStats.value.TRIGGERED || 0)

async function refresh() {
  loading.value = true
  try {
    const [, stats] = await Promise.all([deviceStore.fetchDevices(), realApi.getAlertStats()])
    alertStats.value = stats || {}
    hasShownError = false
    await fetchTcpConnections()
    if (hasTcpData.value && streamEntries.value.length === 0) {
      await loadRecentTcpData()
    }
  } catch (e: any) {
    if (!hasShownError) { ElMessage.error('数据刷新失败: ' + (e?.message || '未知错误')); hasShownError = true }
  } finally { loading.value = false }
}

function statusLabel(s: string) { return s === 'online' ? '在线' : s === 'warning' ? '告警' : '离线' }
function statusType(s: string) { return s === 'online' ? 'success' : s === 'warning' ? 'warning' : 'info' }
function formatFrameTime(ts: number | null) { return ts ? new Date(ts).toLocaleString() : '暂无' }

function startPolling() {
  stopPolling()
  refreshTimer = setInterval(refresh, 15000)
  fetchTcpConnections()
  tcpPollTimer = setInterval(fetchTcpConnections, 5000)
  fallbackTimer = setInterval(checkFallbackStream, 3000)
}
function stopPolling() {
  if (refreshTimer) { clearInterval(refreshTimer); refreshTimer = undefined }
  if (tcpPollTimer) { clearInterval(tcpPollTimer); tcpPollTimer = undefined }
  if (fallbackTimer) { clearInterval(fallbackTimer); fallbackTimer = undefined }
}
function startWs() {
  ws.connect()
  wsUnsubscribe = ws.onAllDeviceData(appendStream)
}
function stopWs() {
  if (wsUnsubscribe) { wsUnsubscribe(); wsUnsubscribe = null }
}

watch(hasTcpData, (hasData) => {
  if (hasData && streamEntries.value.length === 0) {
    loadRecentTcpData()
  } else if (!hasData) {
    streamEntries.value = []
  }
})

onMounted(() => { refresh(); startPolling(); startWs() })
onActivated(() => { startPolling(); startWs() })
onDeactivated(() => { stopPolling(); stopWs() })
onUnmounted(() => { stopPolling(); stopWs() })
</script>

<template>
  <div class="bigdata-dashboard">
    <section class="page-header">
      <div>
        <span class="page-kicker">DATA PLATFORM / LIVE STATUS</span>
        <h1>数据大屏</h1>
        <p>矿洞掘进机实时流数据、设备接入、告警状态统一视图。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="refresh">刷新数据</el-button>
    </section>

    <section class="metric-grid">
      <article class="metric-card blue"><Connection /><span>接入设备</span><strong>{{ deviceStore.totalCount }}</strong><small>来自设备管理接口</small></article>
      <article class="metric-card green"><CircleCheck /><span>在线设备</span><strong>{{ deviceStore.onlineCount }}</strong><small>在线率 {{ onlineRate }}%</small></article>
      <article class="metric-card amber"><WarningFilled /><span>待处理告警</span><strong>{{ triggeredAlerts }}</strong><small>来自告警记录接口</small></article>
      <article :class="['metric-card', connectedTcpCount > 0 ? 'green' : 'muted']">
        <Link /><span>掘进机连接</span><strong>{{ connectedTcpCount }}</strong>
        <small>{{ binaryConnections.length }} 路已配置</small>
      </article>
    </section>

    <section class="content-grid">
      <!-- 实时流数据面板 -->
      <article class="panel telemetry-panel">
        <div class="panel-heading">
          <div><span>STREAM PROCESSING</span><h2>实时流数据</h2></div>
          <!--
            【数据链路切换】
            hasTcpData=true  → 真实数据链路（WebSocket 推送，来自掘进机 TCP 帧）
            hasTcpData=false → 降级状态（连接断开/无帧，不展示模拟数据）
          -->
          <el-tag v-if="hasTcpData" type="success" size="small" effect="dark">
            <span class="live-dot" /> 数据传输中
          </el-tag>
          <el-tag v-else type="info" size="small">连接中断/无数据</el-tag>
        </div>

        <!-- 【真实数据链路】有数据时展示最近 10 条 -->
        <template v-if="hasTcpData && streamEntries.length > 0">
          <el-table :data="streamEntries.slice(0, 10)" size="small" empty-text="等待数据帧...">
            <el-table-column prop="time" label="时间" width="90" />
            <el-table-column prop="deviceId" label="设备ID" min-width="110" show-overflow-tooltip />
            <el-table-column prop="sensorId" label="传感器" min-width="120" show-overflow-tooltip />
            <el-table-column label="数值" width="100">
              <template #default="{ row }">
                <span class="val">{{ typeof row.value === 'number' ? row.value.toFixed(2) : row.value }}</span>
                <span class="unit"> {{ row.unit }}</span>
              </template>
            </el-table-column>
          </el-table>
        </template>

        <!-- 【真实数据链路】已连接但尚未收到 WS 推送 -->
        <template v-else-if="hasTcpData && streamEntries.length === 0">
          <div class="no-data">
            <el-icon :size="32"><DataAnalysis /></el-icon>
            <strong>已连接，等待数据帧...</strong>
            <p>掘进机 TCP 连接正常，等待传感器数据推送至大屏。</p>
          </div>
        </template>

        <!-- 【降级状态】无有效连接或无帧传输，严禁展示模拟数据 -->
        <template v-else>
          <div class="no-data no-data--warning">
            <el-icon :size="32"><WarningFilled /></el-icon>
            <strong>连接中断 / 无数据</strong>
            <p>当前无有效掘进机 TCP 连接，或近 5 分钟内无数据帧传输。</p>
            <p class="hint">请在「TCP 设备连接」页面建立掘进机连接后，此处将自动展示真实数据。</p>
          </div>
        </template>
      </article>

      <!-- 设备运行状态 -->
      <article class="panel">
        <div class="panel-heading">
          <div><span>DEVICE STATUS</span><h2>设备运行状态</h2></div>
          <em>{{ healthRows.length }} 台</em>
        </div>
        <el-table :data="healthRows" size="small" empty-text="暂无设备数据">
          <el-table-column prop="name" label="设备名称" min-width="130" />
          <el-table-column prop="id" label="设备 ID" min-width="130" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </article>
    </section>

    <!-- 掘进机 TCP 连接详情表 -->
    <section class="panel tcp-panel">
      <div class="panel-heading">
        <div><span>TUNNELING MACHINE</span><h2>掘进机 TCP 连接</h2></div>
        <em>{{ binaryConnections.length }} 路</em>
      </div>
      <el-table v-if="binaryConnections.length > 0" :data="binaryConnections" size="small">
        <el-table-column prop="deviceName" label="设备名称" min-width="120" />
        <el-table-column prop="deviceId" label="设备ID" min-width="130" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'CONNECTED' ? 'success' : 'warning'" size="small">
              {{ row.status === 'CONNECTED' ? '已连接' : '重连中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remoteAddress" label="远端地址" min-width="130" />
        <el-table-column label="有效帧" width="80">
          <template #default="{ row }">{{ row.validFrameCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="最近帧时间" min-width="160">
          <template #default="{ row }">{{ formatFrameTime(row.lastFrameAt) }}</template>
        </el-table-column>
      </el-table>
      <div v-else class="no-data" style="min-height:100px">
        <el-icon :size="28"><Link /></el-icon>
        <strong>暂无掘进机连接</strong>
        <p>请前往「TCP 设备连接」页面配置连接。</p>
      </div>
    </section>

    <!-- 数据链路状态步骤条 -->
    <section class="panel pipeline">
      <div class="panel-heading">
        <div><span>PIPELINE</span><h2>数据链路</h2></div>
        <em>按部署状态展示</em>
      </div>
      <el-steps :active="hasTcpData ? 3 : 1" align-center finish-status="success">
        <el-step title="掘进机接入" description="Netty TCP 客户端 + AA/CRC16 帧解析" />
        <el-step title="平台服务" description="DataService → AlertService → WebSocket 推送" />
        <el-step title="流式展示" :description="hasTcpData ? '实时数据已接入大屏' : '等待 TCP 连接和数据帧'" />
        <el-step title="告警闭环" description="甲烷超限 / 规则告警 → 记录 → 推送" />
      </el-steps>
    </section>
  </div>
</template>

<style scoped>
.bigdata-dashboard { max-width: 1440px; margin: 0 auto; padding-bottom: 24px; }
.page-header, .panel-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.page-header { margin-bottom: 24px; }
.page-kicker, .panel-heading span { color: var(--color-cyan); font: 10px/1.2 'Roboto Mono', monospace; letter-spacing: .12em; }
.page-header h1 { margin: 7px 0 0; color: var(--text-primary); font-size: 30px; }
.page-header p { margin: 8px 0 0; color: var(--text-secondary); font-size: 13px; }
.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 12px; }
.metric-card, .panel { background: var(--bg-card); border: 1px solid var(--border-color); border-radius: 8px; }
.metric-card { position: relative; display: grid; grid-template-columns: 32px 1fr; gap: 0 12px; min-height: 122px; padding: 18px; color: var(--text-secondary); }
.metric-card > svg { grid-row: span 2; width: 32px; height: 32px; padding: 8px; border-radius: 6px; background: currentColor; color: var(--bg-card); }
.metric-card strong { color: var(--text-primary); font: 700 27px/1.1 'Roboto Mono', monospace; }
.metric-card small { grid-column: 1 / -1; align-self: end; color: var(--text-muted); font-size: 11px; }
.metric-card.blue { color: var(--color-primary); } .metric-card.green { color: var(--color-success); }
.metric-card.amber { color: var(--color-warning); } .metric-card.muted { color: var(--text-muted); }
.content-grid { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(340px, .9fr); gap: 12px; margin-bottom: 12px; }
.panel { padding: 20px; margin-bottom: 12px; }
.panel-heading { margin-bottom: 18px; }
.panel-heading h2 { margin: 6px 0 0; color: var(--text-primary); font-size: 16px; }
.panel-heading em { color: var(--text-muted); font: 11px 'Roboto Mono', monospace; font-style: normal; }
.no-data { display: grid; min-height: 200px; place-content: center; justify-items: center; text-align: center; color: var(--text-muted); gap: 8px; }
.no-data strong { color: var(--text-primary); font-size: 14px; }
.no-data p { max-width: 340px; margin: 0; font-size: 12px; line-height: 1.7; }
.no-data .hint { color: var(--color-cyan); }
.no-data--warning :deep(.el-icon) { color: var(--color-warning); }
.no-data--warning strong { color: var(--color-warning); }
.live-dot { display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: var(--color-success, #22c55e); margin-right: 4px; vertical-align: middle; animation: pulse 2s infinite; }
@keyframes pulse { 0%,100%{opacity:1}50%{opacity:.4} }
.val { font-weight: 600; color: var(--text-primary); }
.unit { font-size: 11px; color: var(--text-muted); }
.tcp-panel { margin-bottom: 12px; }
.pipeline { padding-bottom: 34px; }
.pipeline :deep(.el-step__title) { color: var(--text-primary); font-size: 13px; }
.pipeline :deep(.el-step__description) { color: var(--text-muted); font-size: 11px; }
@media (max-width: 900px) { .metric-grid { grid-template-columns: repeat(2, 1fr); } .content-grid { grid-template-columns: 1fr; } }
@media (max-width: 600px) { .page-header { flex-direction: column; } .metric-grid { grid-template-columns: 1fr; } }
</style>
