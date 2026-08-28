<template>
  <div ref="dashboardRef" class="dv-wrap">
    <!-- ════════════ 1. 顶部标题栏 (64px) ════════════ -->
    <header class="top-bar">
      <div class="tb-left">
        <span class="clock font-mono"><i class="live-dot"></i>{{ currentTime }}</span>
        <span class="safe-days">安全运行 <b class="font-mono">{{ safeDays }}</b> 天</span>
      </div>
      <div class="tb-center">
        <h1>工业物联网大数据与实时湖仓监控中心</h1>
        <p>INDUSTRIAL IOT BIG DATA &amp; REAL-TIME LAKEHOUSE COMMAND CENTER</p>
      </div>
      <div class="tb-right">
        <span v-for="n in sysNodes" :key="n.name" class="sys-pill">
          <i class="dot" :class="n.ok ? 'ok' : 'bad'"></i>{{ n.name }}
        </span>
        <button class="fs-btn" :class="{ on: isFullscreen }" title="全屏切换" @click="toggleFullScreen">
          <el-icon><FullScreen /></el-icon>
        </button>
      </div>
    </header>

    <!-- ════════════ 2. KPI 指标带 (8 卡) ════════════ -->
    <section class="kpi-band">
      <div v-for="k in kpiList" :key="k.key" class="kpi-card" :class="{ danger: k.danger }">
        <span class="k-label">{{ k.label }}</span>
        <strong class="k-num font-mono">
          <span :key="k.display" class="num-in">{{ k.display }}</span>
          <small>{{ k.unit }}</small>
        </strong>
        <span class="k-trend font-mono" :class="k.dir">
          {{ k.dir === 'up' ? '▲' : '▼' }} {{ k.trend }}
        </span>
      </div>
    </section>

    <!-- ════════════ 3. 告警滚动条 (32px) ════════════ -->
    <div class="alert-marquee">
      <span class="am-tag">实时告警</span>
      <div class="am-mask">
        <div class="am-track">
          <span v-for="(a, i) in marqueeLoop" :key="i" class="am-item">
            <i class="lv-dot" :class="a.level"></i>
            <b class="font-mono">{{ a.time }}</b>
            {{ a.text }}
          </span>
        </div>
      </div>
    </div>

    <!-- ════════════ 4. 主内容区 (22% / 52% / 26%) ════════════ -->
    <section class="main-area">
      <!-- ── 左栏 ── -->
      <div class="col col-l">
        <!-- ① 巷道环境监测 -->
        <div class="panel">
          <i class="cnr tr"></i><i class="cnr bl"></i>
          <div class="p-head">
            <span class="p-live"></span><b>巷道环境监测</b><em>TUNNEL ENVIRONMENT</em>
            <span class="p-extra dim-text font-mono">6 项在线</span>
          </div>
          <div class="p-body env-body">
            <div v-for="e in envList" :key="e.key" class="env-row">
              <span class="e-name">{{ e.name }}</span>
              <div class="e-segs">
                <i v-for="s in 12" :key="s" :class="{ on: s <= e.segs, warn: e.warn }"></i>
              </div>
              <span class="e-val font-mono" :class="{ warn: e.warn }">
                {{ e.value.toFixed(e.decimals) }}<small>{{ e.unit }}</small>
              </span>
            </div>
          </div>
        </div>

        <!-- ② 生产进度 -->
        <div class="panel">
          <i class="cnr tr"></i><i class="cnr bl"></i>
          <div class="p-head">
            <span class="p-live"></span><b>生产进度</b><em>PRODUCTION PROGRESS</em>
            <span class="p-extra dim-text font-mono">完成率 {{ progressPct.toFixed(1) }}%</span>
          </div>
          <div class="p-body prog-body">
            <div ref="gaugeChartRef" class="prog-gauge"></div>
            <div class="prog-nums">
              <div class="pn-item">
                <span>今日进尺</span>
                <strong class="font-mono glow-num">{{ machine.todayFootage.toFixed(1) }}<small>m</small></strong>
              </div>
              <div class="pn-item">
                <span>计划进尺</span>
                <strong class="font-mono">{{ machine.planFootage.toFixed(1) }}<small>m</small></strong>
              </div>
              <div class="pn-item">
                <span>累计进尺</span>
                <strong class="font-mono glow-num">{{ machine.totalFootage.toFixed(1) }}<small>m</small></strong>
              </div>
            </div>
          </div>
        </div>

        <!-- ③ 设备类型分布 -->
        <div class="panel">
          <i class="cnr tr"></i><i class="cnr bl"></i>
          <div class="p-head">
            <span class="p-live"></span><b>设备类型分布</b><em>DEVICE DISTRIBUTION</em>
            <span class="p-extra dim-text font-mono">共 {{ deviceTypeTotal }} 台</span>
          </div>
          <div class="p-body"><div ref="deviceTypeChartRef" class="fill-chart"></div></div>
        </div>
      </div>

      <!-- ── 中栏 ── -->
      <div class="col col-c">
        <!-- ① 巷道态势图（核心） -->
        <div class="panel tunnel-panel">
          <i class="cnr tr"></i><i class="cnr bl"></i>
          <div class="p-head">
            <span class="p-live"></span><b>巷道态势图</b><em>TUNNEL SITUATION</em>
            <span class="p-extra tag-run"><i></i>掘进作业中</span>
          </div>
          <div ref="tunnelWrapRef" class="tunnel-wrap">
            <svg viewBox="0 0 1000 400" preserveAspectRatio="xMidYMid meet">
              <defs>
                <linearGradient id="rockGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0" stop-color="rgba(46,155,255,0.12)" />
                  <stop offset="1" stop-color="rgba(8,26,52,0.6)" />
                </linearGradient>
                <linearGradient id="fadeRight" x1="0" y1="0" x2="1" y2="0">
                  <stop offset="0" stop-color="rgba(10,30,60,0)" />
                  <stop offset="1" stop-color="#0a1e3c" />
                </linearGradient>
              </defs>

              <!-- 巷道主体：直墙半圆拱，右端延伸出画面 -->
              <path d="M 130 336 L 130 250 Q 130 160 215 160 L 915 160 Q 1005 160 1005 250 L 1005 336 Z"
                    fill="url(#rockGrad)" stroke="rgba(0,229,255,0.55)" stroke-width="2" />
              <!-- 顶部锚杆支护示意 -->
              <g stroke="rgba(46,155,255,0.45)" stroke-width="1.5">
                <line x1="300" y1="160" x2="300" y2="148" /><line x1="440" y1="160" x2="440" y2="148" />
                <line x1="580" y1="160" x2="580" y2="148" /><line x1="720" y1="160" x2="720" y2="148" />
                <line x1="860" y1="160" x2="860" y2="148" />
              </g>
              <!-- 地面与轨道 -->
              <line x1="130" y1="336" x2="1005" y2="336" stroke="rgba(46,155,255,0.4)" stroke-width="1.5" />
              <line x1="160" y1="322" x2="1000" y2="322" stroke="rgba(110,141,184,0.35)" stroke-dasharray="10 7" />
              <!-- 压入式风筒（沿顶） -->
              <path class="duct" d="M 1000 176 L 225 176" />
              <!-- 风流方向（新风流向掘进面） -->
              <g class="flow-g">
                <path class="flow" d="M 960 288 L 420 288" />
                <path class="flow" d="M 960 258 L 520 258" />
                <polygon points="420,288 434,283 434,293" fill="rgba(46,155,255,0.6)" />
                <polygon points="520,258 534,253 534,263" fill="rgba(46,155,255,0.6)" />
              </g>

              <!-- 掘进面（左端断面）+ 截割火花 -->
              <path d="M 130 336 L 130 250 Q 130 162 212 161" fill="none" stroke="rgba(0,229,255,0.9)" stroke-width="2.5" />
              <g class="spark-g" stroke="rgba(245,158,11,0.85)" stroke-width="1.5">
                <line x1="128" y1="222" x2="106" y2="212" />
                <line x1="126" y1="235" x2="100" y2="235" />
                <line x1="128" y1="248" x2="106" y2="258" />
              </g>

              <!-- EBZ-260 掘进机剪影 -->
              <g class="machine">
                <rect x="165" y="302" width="175" height="26" rx="9" fill="#16406e" stroke="rgba(0,229,255,0.7)" stroke-width="1.5" />
                <line x1="180" y1="315" x2="325" y2="315" stroke="rgba(0,229,255,0.35)" stroke-dasharray="7 6" />
                <path d="M 185 302 L 185 258 L 295 258 L 318 276 L 318 302 Z" fill="rgba(22,64,110,0.92)" stroke="rgba(0,229,255,0.8)" stroke-width="1.5" />
                <rect x="230" y="242" width="34" height="16" rx="2" fill="rgba(22,64,110,0.85)" stroke="rgba(0,229,255,0.6)" />
                <path d="M 293 268 L 148 226 L 148 244 L 293 286 Z" fill="rgba(0,229,255,0.2)" stroke="rgba(0,229,255,0.8)" stroke-width="1.5" />
                <circle cx="145" cy="235" r="11" fill="rgba(0,229,255,0.25)" stroke="#00E5FF" stroke-width="1.5" />
                <line x1="138" y1="235" x2="152" y2="235" stroke="rgba(0,229,255,0.75)" />
                <line x1="145" y1="228" x2="145" y2="242" stroke="rgba(0,229,255,0.75)" />
              </g>
              <text class="svg-name" x="252" y="230">EBZ-260</text>
              <text class="svg-footage font-mono" x="252" y="248">掘进进尺 {{ machine.totalFootage.toFixed(1) }} m</text>

              <!-- 传感器点位（脉冲圆点） -->
              <g v-for="p in sensorPoints" :key="p.id" class="sp" :class="{ warn: p.warn }"
                 @mouseenter="hoverPoint = p" @mousemove="onTunnelMove" @mouseleave="hoverPoint = null">
                <circle class="sp-ring" :cx="p.x" :cy="p.y" r="7" />
                <circle class="sp-core" :cx="p.x" :cy="p.y" r="4" />
                <circle :cx="p.x" :cy="p.y" r="16" fill="transparent" />
                <text class="sp-label" :x="p.x" :y="p.y + 24">{{ p.short }}</text>
              </g>

              <!-- 右端渐隐遮罩 -->
              <rect x="955" y="140" width="50" height="220" fill="url(#fadeRight)" />
            </svg>

            <!-- 悬停 tooltip -->
            <div v-if="hoverPoint" class="tunnel-tip" :style="{ left: tipX + 'px', top: tipY + 'px' }">
              <b>{{ hoverPoint.name }}</b>
              <span class="font-mono">{{ hoverPoint.value.toFixed(hoverPoint.decimals) }} {{ hoverPoint.unit }}</span>
              <i :class="hoverPoint.warn ? 'w' : 'n'">{{ hoverPoint.warn ? '预警' : '正常' }}</i>
            </div>
          </div>
        </div>

        <!-- ② 核心指标块 ×4 -->
        <div class="core-row">
          <div v-for="m in coreMetricList" :key="m.key" class="core-item" :class="{ over: m.over }">
            <span class="c-name">{{ m.name }}</span>
            <strong class="c-val font-mono" :class="{ glow: !m.over }">
              <span :key="m.display" class="num-in">{{ m.display }}</span>
              <small>{{ m.unit }}</small>
            </strong>
            <span class="c-th font-mono">{{ m.over ? '⚠ 超出阈值 ' + m.threshold + m.unit : '阈值 ' + m.threshold + m.unit }}</span>
          </div>
        </div>
      </div>

      <!-- ── 右栏 ── -->
      <div class="col col-r">
        <!-- ① AI 预测性维护 PHM -->
        <div class="panel">
          <i class="cnr tr"></i><i class="cnr bl"></i>
          <div class="p-head">
            <span class="p-live"></span><b>AI 预测性维护 PHM</b><em>PREDICTIVE MAINTENANCE</em>
            <span class="p-extra tag-ok">健康</span>
          </div>
          <div class="p-body phm-body">
            <div ref="phmGaugeRef" class="phm-gauge"></div>
            <div class="phm-info">
              <div class="pi-row">
                <span>预测剩余寿命</span>
                <strong class="font-mono glow-num">{{ phm.rulDays }}<small>天</small></strong>
              </div>
              <div class="pi-row">
                <span>近 8 日健康趋势</span>
                <div ref="phmTrendRef" class="phm-mini"></div>
              </div>
              <p class="pi-tip">截割部运行平稳，建议 500h 常规巡检</p>
            </div>
          </div>
        </div>

        <!-- ② 十米级遥测数据流 -->
        <div class="panel">
          <i class="cnr tr"></i><i class="cnr bl"></i>
          <div class="p-head">
            <span class="p-live"></span><b>十米级遥测数据流</b><em>TELEMETRY STREAM</em>
            <span class="p-extra dim-text font-mono">{{ telemetryStream.length }} 条</span>
          </div>
          <div class="p-body tl-wrap">
            <div class="tl-head">
              <span>时间</span><span>设备</span><span>传感器</span><span class="ta-r">实时值</span>
            </div>
            <TransitionGroup tag="div" name="tl" class="tl-body">
              <div v-for="t in telemetryStream" :key="t.id" class="tl-row">
                <span class="t font-mono">{{ t.time }}</span>
                <span class="d">{{ t.device }}</span>
                <span class="s">{{ t.sensor }}</span>
                <span class="v font-mono" :class="t.status">
                  <i class="lv-dot" :class="t.status === 'warning' ? 'warning' : 'normal'"></i>{{ t.value.toFixed(t.decimals) }} {{ t.unit }}
                </span>
              </div>
            </TransitionGroup>
          </div>
        </div>

        <!-- ③ 智能联动中心 -->
        <div class="panel">
          <i class="cnr tr"></i><i class="cnr bl"></i>
          <div class="p-head">
            <span class="p-live"></span><b>智能联动中心</b><em>SMART LINKAGE</em>
            <span class="p-extra dim-text font-mono">自动执行</span>
          </div>
          <div class="p-body lk-body">
            <TransitionGroup tag="div" name="tl" class="lk-list">
              <div v-for="l in linkageEvents" :key="l.id" class="lk-row">
                <span class="lk-time font-mono">{{ l.time }}</span>
                <div class="lk-main">
                  <b>{{ l.device }}</b>
                  <p>{{ l.action }}</p>
                </div>
                <i class="lk-dot"></i>
              </div>
            </TransitionGroup>
          </div>
        </div>
      </div>
    </section>

    <!-- ════════════ 5. 底部趋势带 (~220px) ════════════ -->
    <section class="panel trend-band">
      <i class="cnr tr"></i><i class="cnr bl"></i>
      <div class="p-head">
        <span class="p-live"></span><b>关键参数趋势</b><em>KEY PARAMETERS TREND · 3s 采样</em>
        <span class="p-extra dim-text font-mono">窗口 3 分钟</span>
      </div>
      <div ref="trendChartRef" class="trend-chart"></div>
    </section>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'Dashboard' })
import { computed, nextTick, onActivated, onDeactivated, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useDeviceStore } from '../../stores/device'
import { useWebSocket, type WsDeviceData } from '../../stores/websocket'
import { realApi } from '../../api/realApi'
import { FullScreen } from '@element-plus/icons-vue'
import * as echarts from 'echarts'

// ─────────────────────────────────────────────────────────────
// 类型定义（mock 数据结构）
// ─────────────────────────────────────────────────────────────
interface KpiDef {
  key: string; label: string; unit: string; decimals: number
  useGroup?: boolean; danger?: boolean; trend: string; dir: 'up' | 'down'
}
interface EnvDef {
  key: string; name: string; unit: string; decimals: number
  displayMax: number; threshold: number | null; base: number; amp: number
}
interface EnvItem extends EnvDef { value: number; segs: number; warn: boolean }
interface SensorPoint {
  id: string; name: string; short: string; x: number; y: number
  unit: string; decimals: number; base: number; amp: number
  value: number; warn: boolean; warnTicks: number; warnable: boolean
}
interface TelemetryItem {
  id: string; time: string; device: string; sensor: string
  value: number; unit: string; decimals: number; status: 'normal' | 'warning'
}
interface LinkageEvent { id: string; time: string; device: string; action: string }
interface MarqueeAlert { time: string; level: 'normal' | 'warning' | 'critical'; text: string }

// ─────────────────────────────────────────────────────────────
// 全屏控制
// ─────────────────────────────────────────────────────────────
// 大屏根元素：全屏目标（而非 document.documentElement，避免 MainLayout 侧边栏/顶栏残留）
const dashboardRef = ref<HTMLDivElement | null>(null)
const isFullscreen = ref(false)
function toggleFullScreen() {
  if (!document.fullscreenElement) {
    dashboardRef.value?.requestFullscreen().catch(() => {})
  } else if (document.exitFullscreen) {
    document.exitFullscreen().catch(() => {})
  }
}
function onFullscreenChange() {
  isFullscreen.value = !!document.fullscreenElement
  // 进入/退出全屏后容器尺寸变化，重算图表尺寸
  nextTick(handleResize)
}

// ─────────────────────────────────────────────────────────────
// 时钟与安全运行天数
// ─────────────────────────────────────────────────────────────
const currentTime = ref('')
// [接入点] 安全运行天数：可替换为后端系统启动时间接口
const START_TIME = Date.now() - (142 * 24 * 3600 + 8 * 3600 + 32 * 60) * 1000
const safeDays = computed(() => Math.floor((Date.now() - START_TIME) / 86400000))
let clockTimer: ReturnType<typeof setInterval> | undefined

function updateClock() {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  const week = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  currentTime.value =
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
    `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())} ${week[d.getDay()]}`
}

// ─────────────────────────────────────────────────────────────
// 真实接入：设备 store 与 WebSocket
// ─────────────────────────────────────────────────────────────
const deviceStore = useDeviceStore()
const ws = useWebSocket()
let wsUnsub: (() => void) | null = null

// ─────────────────────────────────────────────────────────────
// 顶部右侧系统节点状态
// [接入点] 可替换为 realApi.getFlinkJob / Kafka / Iceberg 健康接口
// ─────────────────────────────────────────────────────────────
const sysNodes = ref([
  { name: 'Flink 1.18', ok: true },
  { name: 'Kafka 32P', ok: true },
  { name: 'Iceberg', ok: true }
])

// ─────────────────────────────────────────────────────────────
// KPI 指标带（8 卡）
// ─────────────────────────────────────────────────────────────
const kpiDefs: KpiDef[] = [
  { key: 'devices', label: '设备接入总数', unit: '台', decimals: 0, trend: '1.2%', dir: 'up' },
  { key: 'online', label: '在线设备数', unit: '台', decimals: 0, trend: '0.8%', dir: 'up' },
  { key: 'qps', label: '实时流入 QPS', unit: '', decimals: 0, useGroup: true, trend: '2.4%', dir: 'up' },
  { key: 'kafkaRate', label: 'Kafka 消费速率', unit: 'M/s', decimals: 2, trend: '1.6%', dir: 'up' },
  { key: 'storage', label: '湖仓累计存储', unit: 'TB', decimals: 2, trend: '0.3%', dir: 'up' },
  { key: 'passRate', label: '清洗合格率', unit: '%', decimals: 2, trend: '0.02%', dir: 'up' },
  { key: 'pending', label: '待响应报警', unit: '条', decimals: 0, danger: true, trend: '1', dir: 'up' },
  { key: 'todayAlerts', label: '今日告警数', unit: '条', decimals: 0, trend: '3', dir: 'up' }
]
const kpiValues = reactive<Record<string, number>>({
  devices: 48, online: 45, qps: 85420, kafkaRate: 1.45,
  storage: 4.82, passRate: 99.92, pending: 3, todayAlerts: 17
})
const kpiList = computed(() =>
  kpiDefs.map(d => {
    const v = kpiValues[d.key]
    const display = d.useGroup ? Math.round(v).toLocaleString('en-US') : v.toFixed(d.decimals)
    return { ...d, display }
  })
)
function tickKpis() {
  kpiValues.qps = Math.max(82000, Math.min(89000, kpiValues.qps + (Math.random() - 0.5) * 2600))
  kpiValues.kafkaRate = Math.max(1.3, Math.min(1.6, kpiValues.kafkaRate + (Math.random() - 0.5) * 0.08))
  kpiValues.storage = +(kpiValues.storage + Math.random() * 0.004).toFixed(3)
  kpiValues.passRate = Math.min(100, Math.max(99.8, kpiValues.passRate + (Math.random() - 0.5) * 0.04))
  if (Math.random() < 0.25) kpiValues.pending = Math.max(1, Math.min(6, kpiValues.pending + (Math.random() < 0.5 ? -1 : 1)))
  if (Math.random() < 0.12) kpiValues.todayAlerts += 1
}

// ─────────────────────────────────────────────────────────────
// 告警滚动条（真实接入 + mock 兜底）
// ─────────────────────────────────────────────────────────────
const marqueeAlerts = ref<MarqueeAlert[]>([
  { time: '16:08:12', level: 'critical', text: 'EBZ-260·截割电机温度 74.3℃ 接近阈值' },
  { time: '16:09:40', level: 'warning', text: 'KJ90-环境站#03·工作面甲烷浓度上扬至 0.58%' },
  { time: '16:10:05', level: 'normal', text: 'MD-280-排水泵#02·自动排空自愈保护联动完成' },
  { time: '16:12:33', level: 'warning', text: 'GCG100-粉尘仪·掘进面粉尘浓度 21.4 mg/m³ 超限' },
  { time: '16:15:47', level: 'normal', text: 'FBD-No7.5·局扇频率自适应调节已生效' }
])
const marqueeLoop = computed(() => [...marqueeAlerts.value, ...marqueeAlerts.value])

async function fetchAlertMarquee() {
  // [已接入] realApi.getAlertRecords：真实告警记录（失败时回落 mock）
  try {
    const page = await realApi.getAlertRecords({ status: 'TRIGGERED', page: 0, size: 8 })
    const content = page?.content || []
    if (content.length > 0) {
      kpiValues.pending = page.totalElements ?? content.length
      marqueeAlerts.value = content.slice(0, 6).map((r: Record<string, unknown>) => {
        const lv = String(r.level || 'WARNING').toUpperCase()
        return {
          time: r.triggeredAt ? new Date(String(r.triggeredAt)).toLocaleTimeString('zh-CN', { hour12: false }) : '刚刚',
          level: (lv === 'CRITICAL' ? 'critical' : lv === 'WARNING' ? 'warning' : 'normal') as MarqueeAlert['level'],
          text: `${String(r.deviceName || r.deviceId || '设备')}·${String(r.ruleName || r.title || '遥测阈值超限触发')}`
        }
      })
    }
  } catch { /* mock 兜底，忽略 */ }
}

// ─────────────────────────────────────────────────────────────
// 左栏 ① 巷道环境监测（6 项分段进度条）
// ─────────────────────────────────────────────────────────────
const envDefs: EnvDef[] = [
  { key: 'ch4', name: '瓦斯 CH4', unit: '%', decimals: 2, displayMax: 1, threshold: 0.6, base: 0.32, amp: 0.12 },
  { key: 'co', name: 'CO 浓度', unit: 'ppm', decimals: 1, displayMax: 40, threshold: 24, base: 5.4, amp: 2.2 },
  { key: 'temp', name: '环境温度', unit: '℃', decimals: 1, displayMax: 40, threshold: 30, base: 23.8, amp: 1.6 },
  { key: 'humidity', name: '相对湿度', unit: '%RH', decimals: 1, displayMax: 100, threshold: null, base: 62.5, amp: 3 },
  { key: 'wind', name: '巷道风速', unit: 'm/s', decimals: 1, displayMax: 6, threshold: null, base: 3.2, amp: 0.5 },
  { key: 'dust', name: '粉尘浓度', unit: 'mg/m³', decimals: 1, displayMax: 30, threshold: 20, base: 14.2, amp: 4 }
]
// [已接入] 传感器类型 → 大屏目标映射（后端为通用 IoT 传感器，按最接近类型代理）
// temperature/humidity 数值天然匹配；pm25/co2 为视觉代理映射，见 scale 注释
const SENSOR_TYPE_MAP: Record<string, { env?: string; point?: string; scale?: number }> = {
  temperature: { env: 'temp', point: 'temp' },
  humidity: { env: 'humidity', point: 'hum' },
  pm25: { env: 'dust', point: 'dust', scale: 0.5 },   // μg/m³ → mg/m³ 量级代理
  co2: { env: 'co', point: 'co', scale: 0.025 }        // ppm CO2 → CO 显示量级代理
}
// [已接入] 真实值豁免窗口：目标键 → 时间戳；期内 tickEnv/tickPoints 跳过该键
// （mock 不干扰真实值，超时后恢复游走兜底）
const realUntil: Record<string, number> = {}
// [已接入] sensorId → 传感器类型索引（devices 加载后构建，用于 WS 数据分发）
const sensorIndex = new Map<string, { type: string; name: string }>()
const envState = reactive<Record<string, number>>(
  Object.fromEntries(envDefs.map(d => [d.key, d.base]))
)
const envList = computed<EnvItem[]>(() =>
  envDefs.map(d => {
    const value = envState[d.key]
    return {
      ...d, value,
      segs: Math.min(12, Math.max(1, Math.round((value / d.displayMax) * 12))),
      warn: d.threshold !== null && value >= d.threshold
    }
  })
)
function tickEnv() {
  for (const d of envDefs) {
    // 真实值豁免期内不游走（超时后恢复 mock 兜底）
    if (Date.now() < (realUntil[d.key] ?? 0)) continue
    const next = envState[d.key] + (Math.random() - 0.5) * d.amp * 0.35
    envState[d.key] = +Math.max(d.base - d.amp, Math.min(d.base + d.amp, next)).toFixed(d.decimals)
  }
}

// ─────────────────────────────────────────────────────────────
// 左栏 ② 生产进度 + 中栏核心指标 + 底部趋势 共享的机器状态
// [已接入] 底部趋势温度序列由真实历史数据预热；machine 状态仍为 mock 随机游走兜底
// ─────────────────────────────────────────────────────────────
const machine = reactive({
  todayFootage: 18.6,
  planFootage: 24.0,
  totalFootage: 1258.6,
  cutRate: 42.0,        // 截割效率 cut/min
  motorCurrent: 160.4,  // 主电机电流 A
  advanceSpeed: 1.28,   // 推进速度 m/min
  coolingFlow: 65.0,    // 冷却水流量 L/min
  cutterTemp: 56.4,     // 截割电机温度 ℃（趋势 + 遥测）
  hydraulicPress: 28.5  // 液压压力 MPa（趋势）
})
const progressPct = computed(() => (machine.todayFootage / machine.planFootage) * 100)

const clamp = (v: number, min: number, max: number) => Math.max(min, Math.min(max, v))
function tickMachine() {
  machine.cutRate = +clamp(machine.cutRate + (Math.random() - 0.5) * 4, 36, 48).toFixed(1)
  // 偶发冲高演示超阈值变橙（阈值 185A）
  machine.motorCurrent = Math.random() < 0.06
    ? +clamp(186 + Math.random() * 8, 186, 196).toFixed(1)
    : +clamp(machine.motorCurrent + (Math.random() - 0.5) * 8, 150, 184).toFixed(1)
  machine.advanceSpeed = +clamp(machine.advanceSpeed + (Math.random() - 0.5) * 0.2, 1.0, 1.5).toFixed(2)
  // 偶发低于下限演示（下限 58L/min）
  machine.coolingFlow = Math.random() < 0.05
    ? +clamp(55 + Math.random() * 2.5, 55, 57.5).toFixed(1)
    : +clamp(machine.coolingFlow + (Math.random() - 0.5) * 5, 58.5, 74).toFixed(1)
  // 偶发温升演示（阈值 70℃）
  machine.cutterTemp = Math.random() < 0.06
    ? +clamp(70.5 + Math.random() * 5, 70.5, 76).toFixed(1)
    : +clamp(machine.cutterTemp + (Math.random() - 0.5) * 3, 52, 69).toFixed(1)
  machine.hydraulicPress = +clamp(machine.hydraulicPress + (Math.random() - 0.5) * 1.4, 26, 31).toFixed(1)
  // 推进速度 1.28 m/min → 每 3s 进尺约 0.064m
  const delta = (machine.advanceSpeed * 3 / 60) * (0.85 + Math.random() * 0.3)
  machine.todayFootage = +(machine.todayFootage + delta).toFixed(2)
  machine.totalFootage = +(machine.totalFootage + delta).toFixed(2)
}

// 中栏底部 4 个核心指标块
const coreMetricDefs = [
  { key: 'cutRate', name: '截割效率', unit: 'cut/min', decimals: 1, threshold: 48, direction: 'max' as const },
  { key: 'motorCurrent', name: '主电机电流', unit: 'A', decimals: 1, threshold: 185, direction: 'max' as const },
  { key: 'advanceSpeed', name: '推进速度', unit: 'm/min', decimals: 2, threshold: 1.6, direction: 'max' as const },
  { key: 'coolingFlow', name: '冷却水流量', unit: 'L/min', decimals: 1, threshold: 58, direction: 'min' as const }
]
const coreMetricList = computed(() =>
  coreMetricDefs.map(d => {
    const value = machine[d.key as keyof typeof machine] as number
    const over = d.direction === 'max' ? value >= d.threshold : value <= d.threshold
    return { ...d, value, over, display: value.toFixed(d.decimals) }
  })
)

// ─────────────────────────────────────────────────────────────
// 中栏 ① 巷道态势图：SVG 传感器点位（真实值优先 + mock 兜底）
// [已接入] 点位实时值由 WebSocket 推送驱动（SENSOR_TYPE_MAP：sensorId 类型 → 点位映射）
// ─────────────────────────────────────────────────────────────
const sensorPoints = reactive<SensorPoint[]>([
  { id: 't0', name: 'T0 瓦斯（掘进面）', short: 'T0瓦斯', x: 155, y: 205, unit: '%', decimals: 2, base: 0.34, amp: 0.16, value: 0.34, warn: false, warnTicks: 0, warnable: true },
  { id: 'dust', name: '掘进面粉尘', short: '粉尘', x: 368, y: 232, unit: 'mg/m³', decimals: 1, base: 14.5, amp: 6, value: 14.5, warn: false, warnTicks: 0, warnable: true },
  { id: 't1', name: 'T1 瓦斯（回风侧）', short: 'T1瓦斯', x: 462, y: 200, unit: '%', decimals: 2, base: 0.28, amp: 0.1, value: 0.28, warn: false, warnTicks: 0, warnable: false },
  { id: 'temp', name: '巷道温度', short: '温度', x: 582, y: 192, unit: '℃', decimals: 1, base: 24.2, amp: 1.8, value: 24.2, warn: false, warnTicks: 0, warnable: false },
  { id: 'wind', name: '风速传感器', short: '风速', x: 686, y: 240, unit: 'm/s', decimals: 1, base: 3.3, amp: 0.5, value: 3.3, warn: false, warnTicks: 0, warnable: false },
  { id: 'co', name: 'CO 传感器', short: 'CO', x: 768, y: 196, unit: 'ppm', decimals: 1, base: 5.8, amp: 1.8, value: 5.8, warn: false, warnTicks: 0, warnable: false },
  { id: 'press', name: '压力监测点', short: '压力', x: 848, y: 228, unit: 'kPa', decimals: 1, base: 88.5, amp: 3, value: 88.5, warn: false, warnTicks: 0, warnable: false },
  { id: 'hum', name: '湿度监测点', short: '湿度', x: 916, y: 196, unit: '%RH', decimals: 1, base: 60.5, amp: 3, value: 60.5, warn: false, warnTicks: 0, warnable: false }
])
function tickPoints() {
  for (const p of sensorPoints) {
    // 真实值豁免期内不游走、不触发预警（超时后恢复 mock 兜底）
    if (Date.now() < (realUntil[p.id] ?? 0)) continue
    if (p.warnTicks > 0) {
      // 预警态：数值冲向高位
      p.warnTicks--
      p.value = +(p.base + p.amp * (0.85 + Math.random() * 0.15)).toFixed(p.decimals)
      if (p.warnTicks === 0) p.value = +(p.base + (Math.random() - 0.3) * p.amp * 0.4).toFixed(p.decimals)
    } else {
      p.value = +(clamp(p.value + (Math.random() - 0.5) * p.amp * 0.3, p.base - p.amp * 0.5, p.base + p.amp * 0.5)).toFixed(p.decimals)
      // 可预警点位间歇性进入预警（持续约 5 个 tick ≈ 15s）
      if (p.warnable && Math.random() < 0.05) p.warnTicks = 5
    }
    p.warn = p.warnTicks > 0
  }
}

// 点位悬停 tooltip
const tunnelWrapRef = ref<HTMLDivElement | null>(null)
const hoverPoint = ref<SensorPoint | null>(null)
const tipX = ref(0)
const tipY = ref(0)
function onTunnelMove(e: MouseEvent) {
  const el = tunnelWrapRef.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  tipX.value = Math.min(e.clientX - rect.left + 14, rect.width - 190)
  tipY.value = Math.min(e.clientY - rect.top + 12, rect.height - 70)
}

// ─────────────────────────────────────────────────────────────
// 右栏 ① AI 预测性维护 PHM
// [接入点] 健康指数/剩余寿命未来由 PHM 推理服务接口返回
// ─────────────────────────────────────────────────────────────
const phm = reactive({ health: 92, rulDays: 410 })
const phmHistory = [88, 90, 89, 91, 93, 92, 94, 92]
function tickPhm() {
  phm.health = Math.round(clamp(phm.health + (Math.random() - 0.5) * 2, 88, 96))
  phm.rulDays = Math.round(clamp(phm.rulDays + (Math.random() - 0.5) * 4, 400, 420))
}

// ─────────────────────────────────────────────────────────────
// 右栏 ② 十米级遥测数据流（mock 2s 插入 + WebSocket 真实数据混合）
// ─────────────────────────────────────────────────────────────
const telemetryPool = [
  { device: 'EBZ-260掘进机#01', sensor: '截割电机温度', unit: '℃', decimals: 1, min: 52, max: 75 },
  { device: 'EBZ-260掘进机#01', sensor: '主电机电流', unit: 'A', decimals: 1, min: 155, max: 195 },
  { device: 'FBD-No7.5通风机', sensor: '轴承温度', unit: '℃', decimals: 1, min: 45, max: 60 },
  { device: 'MD-280排水泵#02', sensor: '出口压力', unit: 'MPa', decimals: 2, min: 3.2, max: 4.5 },
  { device: 'KJ90环境站#03', sensor: 'CH4 浓度', unit: '%', decimals: 2, min: 0.2, max: 0.7 },
  { device: 'DSJ-100皮带机', sensor: '带速', unit: 'm/s', decimals: 2, min: 2.0, max: 2.8 },
  { device: 'GCG100粉尘仪', sensor: '粉尘浓度', unit: 'mg/m³', decimals: 1, min: 8, max: 28 }
]
const telemetryStream = ref<TelemetryItem[]>([])
let telemetrySeq = 0

function fmtHMS(d: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
function pushTelemetry(item: TelemetryItem) {
  telemetryStream.value.unshift(item)
  if (telemetryStream.value.length > 12) telemetryStream.value.pop()
}
function mockTelemetryTick() {
  const s = telemetryPool[Math.floor(Math.random() * telemetryPool.length)]
  const value = +(s.min + Math.random() * (s.max - s.min)).toFixed(s.decimals)
  pushTelemetry({
    id: `mock_${++telemetrySeq}_${Date.now()}`,
    time: fmtHMS(new Date()),
    device: s.device, sensor: s.sensor,
    value, unit: s.unit, decimals: s.decimals,
    status: value > s.max * 0.9 ? 'warning' : 'normal'
  })
}
// [已接入] 真实传感器值 → 大屏目标（env 环境项 + 态势图点位），并刷新 15s 豁免窗口
function applySensorValue(type: string, value: number) {
  const map = SENSOR_TYPE_MAP[(type || '').toLowerCase()]
  if (!map) return
  const scaled = value * (map.scale ?? 1)
  if (map.env && envState[map.env] !== undefined) {
    const def = envDefs.find(d => d.key === map.env)
    envState[map.env] = +scaled.toFixed(def?.decimals ?? 1)
    realUntil[map.env] = Date.now() + 15000
  }
  if (map.point) {
    const p = sensorPoints.find(sp => sp.id === map.point)
    if (p) {
      p.value = +scaled.toFixed(p.decimals)  // 只更新值，不触发 warn 逻辑
      realUntil[p.id] = Date.now() + 15000
    }
  }
}

// [已接入] devices 加载后：构建 sensorId → 类型索引，命中映射的传感器当前值直接写入（首屏即真实）
function seedRealInitialValues() {
  sensorIndex.clear()
  for (const d of deviceStore.devices) {
    for (const s of d.sensors) {
      if (!s?.id) continue
      sensorIndex.set(String(s.id), { type: String(s.type || ''), name: s.name || '' })
      if (SENSOR_TYPE_MAP[String(s.type || '').toLowerCase()]) {
        applySensorValue(String(s.type || ''), typeof s.value === 'number' ? s.value : 0)
      }
    }
  }
}

// [已接入] WebSocket 真实遥测数据到达时插入流，并按 SENSOR_TYPE_MAP 驱动大屏目标（真实优先，mock 兜底）
function handleWsData(data: WsDeviceData) {
  if (data.type !== 'data') return
  pushTelemetry({
    id: `ws_${++telemetrySeq}_${Date.now()}`,
    time: fmtHMS(new Date()),
    device: data.deviceId, sensor: data.sensorId,
    value: typeof data.value === 'number' ? data.value : 0,
    unit: data.unit || '', decimals: 2, status: 'normal'
  })
  // 真实值覆盖：查 sensorIndex 得到传感器类型，命中映射则驱动 env/point 目标
  const meta = sensorIndex.get(data.sensorId)
  if (meta) applySensorValue(meta.type, typeof data.value === 'number' ? data.value : 0)
}

// ─────────────────────────────────────────────────────────────
// 右栏 ③ 智能联动中心（mock 事件池轮换）
// [接入点] 未来由规则引擎/联动执行记录接口提供
// ─────────────────────────────────────────────────────────────
const linkagePool = [
  { device: 'EBZ-260 掘进机', action: '自动增强外喷雾降尘已启动' },
  { device: 'FBD-No7.5 局扇', action: '变频器频率上调至 45Hz 指令已下发' },
  { device: 'MD-280 排水泵', action: '备用泵轮换启动，双回路切换完成' },
  { device: 'KJ90 断电仪', action: '掘进面瓦斯电闭锁自检通过' },
  { device: 'DSJ-100 皮带机', action: '沿线洒水降尘联动已生效' }
]
const linkageEvents = ref<LinkageEvent[]>([])
let linkageSeq = 0
function seedLinkage() {
  linkageEvents.value = linkagePool.slice(0, 4).map((l, i) => ({
    id: `lk_${i}`, time: fmtHMS(new Date(Date.now() - (i + 1) * 40000)),
    device: l.device, action: l.action
  }))
}
function linkageTick() {
  const l = linkagePool[linkageSeq % linkagePool.length]
  linkageSeq++
  linkageEvents.value.unshift({
    id: `lk_${linkageSeq}_${Date.now()}`,
    time: fmtHMS(new Date()), device: l.device, action: l.action
  })
  if (linkageEvents.value.length > 5) linkageEvents.value.pop()
}

// ─────────────────────────────────────────────────────────────
// 左栏 ③ 设备类型分布（真实聚合 + mock 兜底，横向条形图）
// [已接入] devices 加载后按 device.type 聚合（type 为空归「其他」，降序取前 6；空列表保持 mock 兜底）
// ─────────────────────────────────────────────────────────────
const deviceTypeData = ref<{ name: string; value: number }[]>([
  { name: '瓦斯/环境站', value: 18 },
  { name: '主皮带运线', value: 14 },
  { name: '掘进机本体', value: 12 },
  { name: '主通风机', value: 10 },
  { name: '主排水泵', value: 8 },
  { name: '供配电系统', value: 6 }
])
const deviceTypeTotal = computed(() => deviceTypeData.value.reduce((s, d) => s + d.value, 0))

// [已接入] 设备类型真实聚合：仅当设备数 > 0 时替换 mock 数据
function aggregateDeviceTypes() {
  if (deviceStore.devices.length === 0) return  // 空列表保持 mock 兜底
  const counts = new Map<string, number>()
  for (const d of deviceStore.devices) {
    const key = (d.type || '').trim() || '其他'
    counts.set(key, (counts.get(key) || 0) + 1)
  }
  deviceTypeData.value = [...counts.entries()]
    .map(([name, value]) => ({ name, value }))
    .sort((a, b) => b.value - a.value)
    .slice(0, 6)
  // 若图表已初始化（如后续 devices 刷新），补一次刷新；init 前更新则由 initAllCharts 读取
  deviceTypeChart?.setOption(buildDeviceTypeOption())
}

// ─────────────────────────────────────────────────────────────
// ECharts 实例与初始化
// ─────────────────────────────────────────────────────────────
const gaugeChartRef = ref<HTMLDivElement | null>(null)
const deviceTypeChartRef = ref<HTMLDivElement | null>(null)
const phmGaugeRef = ref<HTMLDivElement | null>(null)
const phmTrendRef = ref<HTMLDivElement | null>(null)
const trendChartRef = ref<HTMLDivElement | null>(null)

let gaugeChart: echarts.ECharts | null = null
let deviceTypeChart: echarts.ECharts | null = null
let phmGaugeChart: echarts.ECharts | null = null
let phmTrendChart: echarts.ECharts | null = null
let trendChart: echarts.ECharts | null = null

const MONO = 'Roboto Mono, JetBrains Mono, Consolas, monospace'
const CYAN = '#00E5FF'
const BLUE = '#2E9BFF'
const TXT = '#D6E9FF'
const DIM = '#6E8DB8'

function makeGaugeOption(value: number, label: string): echarts.EChartsCoreOption {
  return {
    series: [{
      type: 'gauge', startAngle: 210, endAngle: -30, min: 0, max: 100,
      radius: '96%', center: ['50%', '58%'],
      progress: {
        show: true, width: 9, roundCap: true,
        itemStyle: { color: CYAN, shadowColor: 'rgba(0,229,255,0.5)', shadowBlur: 8 }
      },
      axisLine: { roundCap: true, lineStyle: { width: 9, color: [[1, 'rgba(46,155,255,0.15)']] } },
      pointer: { show: false }, axisTick: { show: false },
      splitLine: { show: false }, axisLabel: { show: false },
      title: { show: false },
      detail: {
        offsetCenter: [0, 0], formatter: `{value}%\n{sub|${label}}`,
        rich: {
          sub: { fontSize: 10, color: DIM, padding: [6, 0, 0, 0] }
        },
        color: TXT, fontSize: 22, fontFamily: MONO, fontWeight: 600,
        lineHeight: 24
      },
      data: [{ value: +value.toFixed(1) }]
    }]
  }
}

// 设备类型分布 option 构造（数据来自 deviceTypeData.value，聚合更新后可重复调用）
function buildDeviceTypeOption(): echarts.EChartsCoreOption {
  return {
    tooltip: {
      trigger: 'axis', axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(6,22,48,0.92)', borderColor: 'rgba(46,155,255,0.45)',
      textStyle: { color: TXT, fontSize: 12 }
    },
    grid: { top: 6, right: 34, bottom: 2, left: 6, containLabel: true },
    xAxis: { type: 'value', axisLabel: { show: false }, splitLine: { show: false }, axisLine: { show: false } },
    yAxis: {
      type: 'category', inverse: true,
      data: deviceTypeData.value.map(d => d.name),
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: DIM, fontSize: 11 }
    },
    series: [{
      type: 'bar', barWidth: 9,
      data: deviceTypeData.value.map(d => d.value),
      itemStyle: {
        borderRadius: [0, 5, 5, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: 'rgba(0,229,255,0.20)' },
          { offset: 1, color: CYAN }
        ])
      },
      showBackground: true,
      backgroundStyle: { color: 'rgba(46,155,255,0.08)', borderRadius: [0, 5, 5, 0] },
      label: { show: true, position: 'right', color: TXT, fontSize: 11, fontFamily: MONO }
    }]
  }
}

function initAllCharts() {
  // 左栏② 生产进度 gauge
  if (gaugeChartRef.value) {
    gaugeChart = echarts.init(gaugeChartRef.value)
    gaugeChart.setOption(makeGaugeOption(progressPct.value, '今日计划完成率'))
  }

  // 左栏③ 设备类型分布：单色渐变横向条形图
  if (deviceTypeChartRef.value) {
    deviceTypeChart = echarts.init(deviceTypeChartRef.value)
    deviceTypeChart.setOption(buildDeviceTypeOption())
  }

  // 右栏① PHM 健康 gauge + 近 8 日迷你趋势
  if (phmGaugeRef.value) {
    phmGaugeChart = echarts.init(phmGaugeRef.value)
    phmGaugeChart.setOption(makeGaugeOption(phm.health, '健康指数'))
  }
  if (phmTrendRef.value) {
    phmTrendChart = echarts.init(phmTrendRef.value)
    phmTrendChart.setOption({
      grid: { top: 6, right: 4, bottom: 2, left: 4 },
      xAxis: { type: 'category', show: false, data: ['D-7', 'D-6', 'D-5', 'D-4', 'D-3', 'D-2', 'D-1', '今'] },
      yAxis: { type: 'value', show: false, min: 80, max: 100 },
      series: [{
        type: 'line', smooth: true, showSymbol: false, data: phmHistory,
        lineStyle: { width: 1.5, color: BLUE },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(46,155,255,0.35)' },
            { offset: 1, color: 'rgba(46,155,255,0)' }
          ])
        }
      }]
    })
  }

  // 底部趋势带
  if (trendChartRef.value) {
    trendChart = echarts.init(trendChartRef.value)
    trendChart.setOption(buildTrendOption())
  }
}

// ─────────────────────────────────────────────────────────────
// 底部趋势带：60 点滚动窗口
// 【关键】X 轴为类目轴（HH:mm:ss），通过 interval 函数只在「整分钟
// 首次出现」的点位渲染 HH:mm 标签，杜绝同一分钟标签重复堆积
// ─────────────────────────────────────────────────────────────
const trendLabels: string[] = []
const trendLabelFlags: boolean[] = []  // 该点位是否为其所在分钟的第一个出现点
const trendTemp: number[] = []
const trendPress: number[] = []
const trendSpeed: number[] = []
const TREND_WINDOW = 60

function pushTrendPoint(temp: number, press: number, speed: number) {
  const label = fmtHMS(new Date())
  const prev = trendLabels.length > 0 ? trendLabels[trendLabels.length - 1] : ''
  trendLabels.push(label)
  trendLabelFlags.push(label.slice(0, 5) !== prev.slice(0, 5))
  trendTemp.push(+temp.toFixed(1))
  trendPress.push(+press.toFixed(1))
  trendSpeed.push(+speed.toFixed(2))
  if (trendLabels.length > TREND_WINDOW) {
    trendLabels.shift(); trendLabelFlags.shift()
    trendTemp.shift(); trendPress.shift(); trendSpeed.shift()
  }
}
function seedTrend() {
  const now = Date.now()
  for (let i = TREND_WINDOW - 1; i >= 0; i--) {
    const t = new Date(now - i * 3000)
    const label = fmtHMS(t)
    const prev = trendLabels.length > 0 ? trendLabels[trendLabels.length - 1] : ''
    trendLabels.push(label)
    trendLabelFlags.push(label.slice(0, 5) !== prev.slice(0, 5))
    const phase = i / 6
    trendTemp.push(+(56.4 + Math.sin(phase) * 4 + (Math.random() - 0.5) * 1.5).toFixed(1))
    trendPress.push(+(28.5 + Math.cos(phase) * 1.2 + (Math.random() - 0.5) * 0.5).toFixed(1))
    trendSpeed.push(+(1.28 + Math.sin(phase / 2) * 0.08 + (Math.random() - 0.5) * 0.05).toFixed(2))
  }
}

// [已接入] 底部趋势真实预热：取第一台有温度传感器且在线的设备的历史数据重建窗口
// （X 轴用真实时间戳、温度序列用真实值；压力/推进速度保持 mock 种子并按长度对齐；失败保持 mock 种子）
async function warmTrendWithRealData() {
  try {
    const dev = deviceStore.devices.find(d =>
      d.status === 'online' && d.sensors.some(s => (s.type || '').toLowerCase() === 'temperature'))
    if (!dev) return
    const sensor = dev.sensors.find(s => (s.type || '').toLowerCase() === 'temperature')
    if (!sensor) return
    const points = await realApi.getDeviceData(dev.id, sensor.id, 60) as Array<{ timestamp?: string; value?: number }>
    if (!Array.isArray(points) || points.length < 10) return
    // 后端可能按时间倒序：统一按时间正序排列
    const sorted = points
      .filter(p => p && typeof p.value === 'number')
      .sort((a, b) => new Date(String(a.timestamp)).getTime() - new Date(String(b.timestamp)).getTime())
    if (sorted.length < 10) return
    // 重建窗口：X 轴类目与温度序列均来自真实数据
    trendLabels.length = 0; trendLabelFlags.length = 0; trendTemp.length = 0
    for (const p of sorted.slice(-TREND_WINDOW)) {
      const ts = new Date(String(p.timestamp))
      const label = fmtHMS(isNaN(ts.getTime()) ? new Date() : ts)
      const prev = trendLabels.length > 0 ? trendLabels[trendLabels.length - 1] : ''
      trendLabels.push(label)
      trendLabelFlags.push(label.slice(0, 5) !== prev.slice(0, 5))
      trendTemp.push(+Number(p.value).toFixed(1))
    }
    // 压力/推进速度：保持 mock 种子并对齐长度（不足用 mock 值填充，多余截断）
    const n = trendLabels.length
    while (trendPress.length < n) trendPress.push(+(28.5 + (Math.random() - 0.5) * 1.2).toFixed(1))
    if (trendPress.length > n) trendPress.splice(0, trendPress.length - n)
    while (trendSpeed.length < n) trendSpeed.push(+(1.28 + (Math.random() - 0.5) * 0.1).toFixed(2))
    if (trendSpeed.length > n) trendSpeed.splice(0, trendSpeed.length - n)
    // 图表已初始化时立即刷新（未初始化则由 initAllCharts 读取）
    updateCharts()
  } catch { /* 失败保持 mock 种子，不报错 */ }
}

function buildTrendOption(): echarts.EChartsCoreOption {
  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(6,22,48,0.94)', borderColor: 'rgba(0,229,255,0.4)',
      textStyle: { color: TXT, fontSize: 12 }
    },
    legend: {
      top: 4, right: 12, itemWidth: 14, itemHeight: 8, itemGap: 16,
      textStyle: { color: DIM, fontSize: 11 },
      data: ['截割电机温度 ℃', '液压压力 MPa', '推进速度 m/min']
    },
    grid: { top: 30, right: 58, bottom: 22, left: 46 },
    xAxis: {
      type: 'category', boundaryGap: false, data: trendLabels,
      axisLine: { lineStyle: { color: 'rgba(46,155,255,0.25)' } },
      axisTick: { show: false },
      axisLabel: {
        color: DIM, fontSize: 10, fontFamily: MONO,
        // 只在整分钟首个点位显示 HH:mm，避免同分钟标签重复
        interval: (i: number) => !!trendLabelFlags[i],
        formatter: (v: string) => v.slice(0, 5)
      }
    },
    yAxis: [
      {
        type: 'value', name: '℃', min: 40, max: 90,
        nameTextStyle: { color: DIM, fontSize: 10, align: 'right' },
        axisLabel: { color: DIM, fontSize: 10, fontFamily: MONO },
        splitLine: { lineStyle: { color: 'rgba(46,155,255,0.10)', type: 'dashed' } }
      },
      {
        type: 'value', name: 'MPa', min: 0, max: 40,
        nameTextStyle: { color: DIM, fontSize: 10, align: 'right' },
        axisLabel: { color: DIM, fontSize: 10, fontFamily: MONO },
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '截割电机温度 ℃', type: 'line', smooth: true, showSymbol: false,
        data: trendTemp, lineStyle: { width: 2, color: CYAN }, itemStyle: { color: CYAN },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(0,229,255,0.26)' },
            { offset: 1, color: 'rgba(0,229,255,0)' }
          ])
        }
      },
      {
        name: '液压压力 MPa', type: 'line', smooth: true, showSymbol: false,
        yAxisIndex: 1, data: trendPress,
        lineStyle: { width: 2, color: BLUE }, itemStyle: { color: BLUE },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(46,155,255,0.18)' },
            { offset: 1, color: 'rgba(46,155,255,0)' }
          ])
        }
      },
      {
        name: '推进速度 m/min', type: 'line', smooth: true, showSymbol: false,
        yAxisIndex: 1, data: trendSpeed,
        lineStyle: { width: 1.5, color: DIM, type: 'dashed' }, itemStyle: { color: DIM }
      }
    ]
  }
}

function updateCharts() {
  gaugeChart?.setOption({
    series: [{ data: [{ value: +progressPct.value.toFixed(1) }] }]
  })
  phmGaugeChart?.setOption({
    series: [{ data: [{ value: phm.health }] }]
  })
  trendChart?.setOption({
    xAxis: { data: trendLabels },
    series: [{ data: trendTemp }, { data: trendPress }, { data: trendSpeed }]
  })
}

function handleResize() {
  gaugeChart?.resize()
  deviceTypeChart?.resize()
  phmGaugeChart?.resize()
  phmTrendChart?.resize()
  trendChart?.resize()
}

// ─────────────────────────────────────────────────────────────
// 定时器编排（onActivated / onDeactivated 安全启停）
// ─────────────────────────────────────────────────────────────
let dataTimer: ReturnType<typeof setInterval> | undefined
let telemetryTimer: ReturnType<typeof setInterval> | undefined
let linkageTimer: ReturnType<typeof setInterval> | undefined

function startLoops() {
  stopLoops()
  ws.connect()
  // [已接入] WebSocket 全量设备数据 → 遥测流 + 大屏目标分发（真实数据优先，mock 兜底）
  wsUnsub = ws.onAllDeviceData(handleWsData)
  dataTimer = setInterval(() => {
    tickKpis(); tickEnv(); tickMachine(); tickPoints(); tickPhm()
    pushTrendPoint(machine.cutterTemp, machine.hydraulicPress, machine.advanceSpeed)
    updateCharts()
  }, 3000)
  telemetryTimer = setInterval(mockTelemetryTick, 2000)
  linkageTimer = setInterval(linkageTick, 12000)
}
function stopLoops() {
  if (dataTimer) { clearInterval(dataTimer); dataTimer = undefined }
  if (telemetryTimer) { clearInterval(telemetryTimer); telemetryTimer = undefined }
  if (linkageTimer) { clearInterval(linkageTimer); linkageTimer = undefined }
  if (wsUnsub) { wsUnsub(); wsUnsub = null }
}

onMounted(async () => {
  updateClock()
  clockTimer = setInterval(updateClock, 1000)
  document.addEventListener('fullscreenchange', onFullscreenChange)

  seedTrend()
  seedLinkage()
  for (let i = 0; i < 9; i++) mockTelemetryTick()

  // [已接入] 真实设备数/在线数（deviceStore）+ 首屏真实值/类型聚合
  try {
    await deviceStore.fetchDevices()
    if (deviceStore.totalCount > 0) {
      kpiValues.devices = deviceStore.totalCount
      kpiValues.online = deviceStore.onlineCount
    }
    seedRealInitialValues()   // [已接入] sensorId 索引 + 命中映射的传感器当前值（首屏即真实）
    aggregateDeviceTypes()    // [已接入] 设备类型分布真实聚合（空列表保持 mock）
  } catch { /* 保持 mock 值 */ }
  warmTrendWithRealData()     // [已接入] 底部趋势真实预热（异步，失败保持 mock 种子）
  fetchAlertMarquee()

  await nextTick()
  initAllCharts()
  window.addEventListener('resize', handleResize)
  startLoops()
})

onActivated(() => {
  startLoops()
  handleResize()
})

onDeactivated(() => {
  stopLoops()
})

onUnmounted(() => {
  stopLoops()
  if (clockTimer) clearInterval(clockTimer)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  window.removeEventListener('resize', handleResize)
  gaugeChart?.dispose()
  deviceTypeChart?.dispose()
  phmGaugeChart?.dispose()
  phmTrendChart?.dispose()
  trendChart?.dispose()
  gaugeChart = null
  deviceTypeChart = null
  phmGaugeChart = null
  phmTrendChart = null
  trendChart = null
})
</script>

<style scoped>
/* ═══════════ 设计令牌（DataV 政企指挥风，组件作用域内覆盖） ═══════════ */
.dv-wrap {
  --bg: #0a1e3c;
  --panel: linear-gradient(180deg, rgba(12, 42, 84, 0.72), rgba(8, 26, 52, 0.55));
  --line: rgba(0, 229, 255, 0.28);
  --cyan: #00e5ff;
  --blue: #2e9bff;
  --txt: #d6e9ff;
  --dim: #6e8db8;
  --ok: #22c55e;
  --warn: #f59e0b;
  --bad: #ef4444;
  --mono: 'Roboto Mono', 'JetBrains Mono', Consolas, monospace;

  width: 100%;
  height: 100vh;
  box-sizing: border-box;
  overflow: hidden;
  display: grid;
  grid-template-rows: 64px auto 32px minmax(0, 1fr) 220px;
  gap: 10px;
  padding: 12px 16px 14px;
  color: var(--txt);
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 12px;
  background:
    repeating-linear-gradient(0deg, rgba(0, 229, 255, 0.03) 0 1px, transparent 1px 56px),
    repeating-linear-gradient(90deg, rgba(0, 229, 255, 0.03) 0 1px, transparent 1px 56px),
    radial-gradient(1100px 520px at 50% 24%, rgba(20, 60, 120, 0.25), transparent 68%),
    radial-gradient(760px 380px at 92% 108%, rgba(0, 229, 255, 0.05), transparent 60%),
    var(--bg);
}

/* 全屏兜底：根元素进入 top layer 后铺满视口，防止白边/布局残留 */
.dv-wrap:fullscreen {
  width: 100vw;
  height: 100vh;
  max-width: none;
  max-height: none;
  margin: 0;
  background-color: #0a1e3c;
}
.dv-wrap::backdrop {
  background-color: #0a1e3c;
}
.font-mono { font-family: var(--mono); }
.dim-text { color: var(--dim); font-size: 11px; }
.lv-dot {
  display: inline-block; width: 6px; height: 6px; border-radius: 50%;
  margin-right: 6px; vertical-align: middle; flex-shrink: 0;
}
.lv-dot.normal { background: var(--ok); box-shadow: 0 0 5px rgba(34, 197, 94, 0.7); }
.lv-dot.warning { background: var(--warn); box-shadow: 0 0 5px rgba(245, 158, 11, 0.7); }
.lv-dot.critical { background: var(--bad); box-shadow: 0 0 5px rgba(239, 68, 68, 0.7); }

/* ═══════════ 1. 顶部标题栏 ═══════════ */
.top-bar {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 0 6px;
  border-bottom: 1px solid var(--line);
  position: relative;
}
.top-bar::after {
  content: '';
  position: absolute; left: 0; bottom: -1px;
  width: 240px; height: 2px;
  background: linear-gradient(90deg, var(--cyan), transparent);
}
.tb-left { display: flex; align-items: center; gap: 18px; }
.clock {
  display: inline-flex; align-items: center;
  font-size: 14px; color: var(--txt); letter-spacing: 0.5px;
}
.live-dot {
  width: 7px; height: 7px; border-radius: 50%;
  background: var(--cyan); margin-right: 8px;
  box-shadow: 0 0 6px var(--cyan), 0 0 14px rgba(0, 229, 255, 0.5);
  animation: pulse 2s ease-in-out infinite;
}
.safe-days { color: var(--dim); font-size: 12px; }
.safe-days b { color: var(--cyan); font-size: 16px; margin: 0 2px; text-shadow: 0 0 10px rgba(0, 229, 255, 0.45); }
.tb-center { text-align: center; }
.tb-center h1 {
  position: relative;
  margin: 0; font-size: 24px; font-weight: 700; letter-spacing: 4px; color: #fff;
  padding: 0 78px;
  background: linear-gradient(180deg, #ffffff 25%, #9be8ff 95%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  text-shadow: 0 0 22px rgba(0, 229, 255, 0.5);
}
/* 主标题两侧对称科技装饰：渐变斜线 + 端点小方块 */
.tb-center h1::before,
.tb-center h1::after {
  content: '';
  position: absolute; top: 50%; transform: translateY(-50%);
  width: 64px; height: 8px;
  background-repeat: no-repeat;
  filter: drop-shadow(0 0 4px rgba(0, 229, 255, 0.5));
}
.tb-center h1::before {
  left: 0;
  background-image:
    linear-gradient(90deg, transparent, rgba(0, 229, 255, 0.75)),
    linear-gradient(180deg, rgba(0, 229, 255, 0.95), rgba(46, 155, 255, 0.55));
  background-size: 50px 2px, 6px 8px;
  background-position: right center, 0 center;
}
.tb-center h1::after {
  right: 0;
  background-image:
    linear-gradient(270deg, transparent, rgba(0, 229, 255, 0.75)),
    linear-gradient(180deg, rgba(0, 229, 255, 0.95), rgba(46, 155, 255, 0.55));
  background-size: 50px 2px, 6px 8px;
  background-position: left center, 100% center;
}
.tb-center p { margin: 2px 0 0; font-size: 9px; letter-spacing: 3px; color: var(--dim); }
.tb-right { display: flex; align-items: center; justify-content: flex-end; gap: 10px; }
.sys-pill {
  display: inline-flex; align-items: center;
  padding: 3px 10px; font-size: 11px; color: var(--txt);
  border: 1px solid var(--line); border-radius: 3px;
  background: rgba(12, 42, 84, 0.5);
}
.sys-pill .dot { width: 7px; height: 7px; border-radius: 50%; margin-right: 6px; }
.sys-pill .dot.ok {
  background: var(--ok);
  box-shadow: 0 0 5px rgba(34, 197, 94, 0.9), 0 0 12px rgba(34, 197, 94, 0.45);
  animation: pulse 2.4s infinite;
}
.sys-pill .dot.bad {
  background: var(--bad);
  box-shadow: 0 0 5px rgba(239, 68, 68, 0.9), 0 0 12px rgba(239, 68, 68, 0.45);
}
.fs-btn {
  display: inline-flex; align-items: center; justify-content: center;
  width: 30px; height: 30px; cursor: pointer;
  background: rgba(46, 155, 255, 0.1); color: var(--cyan);
  border: 1px solid var(--line); border-radius: 4px;
  transition: all 0.2s;
}
.fs-btn:hover { background: rgba(0, 229, 255, 0.16); border-color: rgba(0, 229, 255, 0.55); }
.fs-btn.on {
  background: rgba(0, 229, 255, 0.18);
  border-color: rgba(0, 229, 255, 0.6);
  box-shadow: 0 0 8px rgba(0, 229, 255, 0.35);
}

/* ═══════════ 2. KPI 指标带 ═══════════ */
.kpi-band { display: grid; grid-template-columns: repeat(8, 1fr); gap: 10px; }
.kpi-card {
  position: relative;
  display: flex; flex-direction: column; justify-content: center; gap: 2px;
  padding: 8px 12px; min-height: 86px; box-sizing: border-box;
  background: var(--panel);
  border: 1px solid var(--line);
}
.kpi-card::before {
  content: ''; position: absolute; top: 0; left: 0;
  width: 34px; height: 2px;
  background: linear-gradient(90deg, var(--cyan), transparent);
}
.kpi-card.danger {
  border-color: rgba(239, 68, 68, 0.55);
  background: rgba(60, 17, 22, 0.45);
  animation: dangerBreathe 2.2s ease-in-out infinite;
}
.kpi-card.danger::before { background: linear-gradient(90deg, var(--bad), transparent); }
.kpi-card.danger::after {
  content: '';
  position: absolute; left: 0; right: 0; top: 0;
  height: 2px; pointer-events: none;
  background: linear-gradient(90deg, transparent, rgba(239, 68, 68, 0.75), transparent);
  animation: scanDown 6s linear infinite;
}
.k-label { font-size: 11px; color: var(--dim); letter-spacing: 1px; white-space: nowrap; }
.k-num {
  display: flex; align-items: baseline; gap: 4px;
  font-size: 25px; font-weight: 600; color: var(--txt); line-height: 1.1;
}
.kpi-card.danger .k-num { color: var(--bad); text-shadow: 0 0 14px rgba(239, 68, 68, 0.5); }
.k-num small { font-size: 11px; font-weight: 400; color: var(--dim); }
.k-trend { font-size: 10px; color: var(--dim); }
.k-trend.up { color: rgba(0, 229, 255, 0.75); }
.kpi-card.danger .k-trend { color: rgba(239, 68, 68, 0.85); }
.num-in { display: inline-block; animation: numIn 0.45s ease; }

/* ═══════════ 3. 告警滚动条 ═══════════ */
.alert-marquee {
  display: flex; align-items: center; overflow: hidden;
  background: rgba(12, 42, 84, 0.45);
  border: 1px solid rgba(239, 68, 68, 0.18);
}
.am-tag {
  flex-shrink: 0; display: inline-flex; align-items: center; height: 100%;
  padding: 0 14px; font-size: 11px; letter-spacing: 2px; color: var(--bad);
  border-right: 1px solid rgba(239, 68, 68, 0.25);
  background: rgba(239, 68, 68, 0.08);
}
.am-mask { flex: 1; overflow: hidden; white-space: nowrap; }
.am-track {
  display: inline-flex; align-items: center;
  animation: marquee 32s linear infinite;
}
.am-item {
  display: inline-flex; align-items: center;
  padding-right: 56px; font-size: 12px; color: var(--txt);
}
.am-item b { color: var(--dim); font-weight: 400; margin-right: 8px; font-size: 11px; }

/* ═══════════ 4. 主内容区 ═══════════ */
.main-area {
  display: grid;
  grid-template-columns: 22fr 52fr 26fr;
  gap: 10px;
  min-height: 0;
}
.col { display: grid; gap: 10px; min-height: 0; min-width: 0; }
.col-l, .col-r { grid-template-rows: 1fr 1fr 1fr; }
.col-c { grid-template-rows: 1fr auto; }

/* 面板骨架：四角 sci-fi 角标 */
.panel {
  position: relative;
  display: flex; flex-direction: column;
  background: var(--panel);
  border: 1px solid var(--line);
  box-shadow: 0 0 18px rgba(0, 190, 255, 0.1), inset 0 0 30px rgba(0, 120, 255, 0.06);
  min-height: 0; min-width: 0;
}
.panel::before, .panel::after,
.cnr {
  position: absolute; width: 10px; height: 10px; pointer-events: none; opacity: 0.9;
  filter: drop-shadow(0 0 4px rgba(0, 229, 255, 0.55));
}
.panel::before {
  content: ''; top: -1px; left: -1px;
  border-top: 1px solid var(--cyan); border-left: 1px solid var(--cyan);
}
.panel::after {
  content: ''; bottom: -1px; right: -1px;
  border-bottom: 1px solid var(--cyan); border-right: 1px solid var(--cyan);
}
.cnr.tr { top: -1px; right: -1px; border-top: 1px solid var(--cyan); border-right: 1px solid var(--cyan); }
.cnr.bl { bottom: -1px; left: -1px; border-bottom: 1px solid var(--cyan); border-left: 1px solid var(--cyan); }

.p-head {
  position: relative;
  display: flex; align-items: center; gap: 8px;
  height: 32px; padding: 0 10px 0 18px; flex-shrink: 0;
}
/* DataV 斜切标题标签 */
.p-head::before {
  content: '';
  position: absolute; left: 4px; top: 50%;
  width: 7px; height: 15px;
  transform: translateY(-50%) skewX(-18deg);
  background: linear-gradient(180deg, #00e5ff, #2e9bff);
  box-shadow: 0 0 8px rgba(0, 229, 255, 0.55);
}
/* 标题栏底部青→透明渐变线 */
.p-head::after {
  content: '';
  position: absolute; left: 0; right: 0; bottom: 0;
  height: 1px;
  background: linear-gradient(90deg, rgba(0, 229, 255, 0.75), transparent 88%);
}
.p-head b {
  font-size: 13px; font-weight: 600; letter-spacing: 1px; color: var(--txt);
  text-shadow: 0 0 8px rgba(0, 229, 255, 0.45);
}
.p-head em {
  font-style: normal; font-size: 9px; letter-spacing: 2px;
  color: var(--dim); text-transform: uppercase; opacity: 0.8;
}
.p-extra { margin-left: auto; display: inline-flex; align-items: center; gap: 6px; }
.p-live {
  width: 6px; height: 6px; border-radius: 50%;
  background: var(--cyan); box-shadow: 0 0 6px var(--cyan);
  animation: pulse 2s ease-in-out infinite;
}
.tag-run {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 2px 10px; font-size: 11px; color: var(--ok);
  border: 1px solid rgba(34, 197, 94, 0.4); border-radius: 3px;
  background: rgba(34, 197, 94, 0.08);
}
.tag-run i {
  width: 6px; height: 6px; border-radius: 50%; background: var(--ok);
  box-shadow: 0 0 6px var(--ok); animation: pulse 1.6s infinite;
}
.tag-ok {
  padding: 2px 10px; font-size: 11px; color: var(--ok);
  border: 1px solid rgba(34, 197, 94, 0.4); border-radius: 3px;
  background: rgba(34, 197, 94, 0.08);
}
.p-body { flex: 1; min-height: 0; padding: 8px 10px; box-sizing: border-box; position: relative; }

/* ── 左栏① 环境监测分段条 ── */
.env-body { display: flex; flex-direction: column; justify-content: space-around; }
.env-row { display: flex; align-items: center; gap: 10px; }
.e-name { width: 62px; flex-shrink: 0; font-size: 12px; color: var(--dim); }
.e-segs { flex: 1; display: flex; gap: 3px; }
.e-segs i {
  flex: 1; height: 9px;
  background: rgba(46, 155, 255, 0.1);
  border: 1px solid rgba(46, 155, 255, 0.08);
  transition: background 0.4s;
}
.e-segs i.on {
  background: rgba(0, 229, 255, 0.55);
  border-color: rgba(0, 229, 255, 0.3);
  box-shadow: inset 0 0 3px rgba(0, 229, 255, 0.4);
}
.e-segs i.on.warn {
  background: rgba(245, 158, 11, 0.65);
  border-color: rgba(245, 158, 11, 0.4);
  animation: blinkWarn 1s ease-in-out infinite;
}
.e-val { width: 82px; flex-shrink: 0; text-align: right; font-size: 13px; color: var(--txt); }
.e-val.warn { color: var(--warn); animation: blinkWarn 1s ease-in-out infinite; }
.e-val small { font-size: 10px; color: var(--dim); margin-left: 2px; }

/* ── 左栏② 生产进度 ── */
.prog-body { display: grid; grid-template-columns: 46% 1fr; align-items: center; gap: 6px; }
.prog-gauge { width: 100%; height: 100%; min-height: 110px; }
.prog-nums { display: flex; flex-direction: column; gap: 10px; }
.pn-item span { display: block; font-size: 11px; color: var(--dim); margin-bottom: 2px; }
.pn-item strong { font-size: 19px; color: var(--txt); font-weight: 600; }
.pn-item strong small { font-size: 10px; color: var(--dim); margin-left: 3px; font-weight: 400; }
.glow-num { text-shadow: 0 0 12px rgba(0, 229, 255, 0.5); }

/* ── 图表容器 ── */
.fill-chart { width: 100%; height: 100%; }

/* ── 中栏① 巷道态势图 ── */
.tunnel-panel { min-height: 0; }
.tunnel-panel .scan {
  position: absolute; left: 2px; right: 2px; top: 0; z-index: 2;
  height: 2px; pointer-events: none;
  background: linear-gradient(90deg, transparent, rgba(0, 229, 255, 0.5), transparent);
  animation: scanDown 8s linear infinite;
}
.tunnel-wrap { position: relative; flex: 1; min-height: 0; padding: 4px 8px 0; }
.tunnel-wrap svg { width: 100%; height: 100%; display: block; }
.machine { filter: drop-shadow(0 0 6px rgba(0, 229, 255, 0.3)); }
.svg-name {
  fill: var(--cyan); font-size: 12px; letter-spacing: 3px; font-weight: 600;
  font-family: var(--mono);
}
.svg-footage { fill: var(--txt); font-size: 11px; }
.duct {
  fill: none; stroke: rgba(46, 155, 255, 0.45); stroke-width: 2.5;
  stroke-dasharray: 14 7; animation: flow 1.6s linear infinite;
}
.flow {
  fill: none; stroke: rgba(46, 155, 255, 0.4); stroke-width: 1.5;
  stroke-dasharray: 10 8; animation: flow 1.2s linear infinite;
}
.spark-g line { animation: spark 0.8s ease-in-out infinite alternate; }
.spark-g line:nth-child(2) { animation-delay: 0.25s; }
.spark-g line:nth-child(3) { animation-delay: 0.5s; }
.sp { cursor: pointer; }
.sp-ring {
  fill: none; stroke: var(--cyan); stroke-width: 1.5;
  transform-box: fill-box; transform-origin: center;
  animation: ping 2.2s cubic-bezier(0, 0, 0.2, 1) infinite;
}
.sp-core { fill: var(--cyan); filter: drop-shadow(0 0 4px rgba(0, 229, 255, 0.8)); }
.sp-label { fill: var(--dim); font-size: 10px; text-anchor: middle; }
.sp.warn .sp-ring { stroke: var(--warn); animation-duration: 1.1s; }
.sp.warn .sp-core {
  fill: var(--warn);
  filter: drop-shadow(0 0 5px rgba(245, 158, 11, 0.9));
  animation: blinkWarn 0.9s ease-in-out infinite;
}
.sp.warn .sp-label { fill: var(--warn); }
.tunnel-tip {
  position: absolute; z-index: 10; pointer-events: none;
  display: flex; align-items: center; gap: 8px;
  padding: 6px 10px;
  background: rgba(6, 22, 48, 0.94);
  border: 1px solid rgba(0, 229, 255, 0.4);
  font-size: 12px; white-space: nowrap;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.5);
}
.tunnel-tip b { color: var(--txt); font-weight: 600; }
.tunnel-tip span { color: var(--cyan); }
.tunnel-tip i { font-style: normal; font-size: 10px; padding: 1px 6px; border-radius: 2px; }
.tunnel-tip i.n { color: var(--ok); background: rgba(34, 197, 94, 0.12); }
.tunnel-tip i.w { color: var(--warn); background: rgba(245, 158, 11, 0.12); }

/* ── 中栏② 核心指标块 ── */
.core-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
.core-item {
  display: flex; flex-direction: column; gap: 2px;
  padding: 10px 12px; min-height: 84px; box-sizing: border-box;
  background: var(--panel); border: 1px solid var(--line);
}
.core-item .c-name { font-size: 11px; color: var(--dim); letter-spacing: 1px; }
.core-item .c-val {
  font-size: 24px; font-weight: 600; color: var(--txt); line-height: 1.15;
}
.core-item .c-val small { font-size: 10px; color: var(--dim); margin-left: 3px; font-weight: 400; }
.core-item .c-val.glow { text-shadow: 0 0 12px rgba(0, 229, 255, 0.45); }
.core-item .c-th { font-size: 10px; color: var(--dim); }
.core-item.over { border-color: rgba(245, 158, 11, 0.55); background: rgba(66, 44, 8, 0.35); }
.core-item.over .c-val { color: var(--warn); text-shadow: 0 0 12px rgba(245, 158, 11, 0.5); }
.core-item.over .c-th { color: var(--warn); animation: blinkWarn 1.1s ease-in-out infinite; }

/* ── 右栏① PHM ── */
.phm-body { display: grid; grid-template-columns: 42% 1fr; gap: 8px; }
.phm-gauge { width: 100%; height: 100%; min-height: 108px; }
.phm-info { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.pi-row span { display: block; font-size: 11px; color: var(--dim); margin-bottom: 1px; }
.pi-row strong { font-size: 20px; color: var(--txt); font-weight: 600; }
.pi-row strong small { font-size: 10px; color: var(--dim); margin-left: 3px; font-weight: 400; }
.phm-mini { width: 100%; height: 46px; margin-top: 2px; }
.pi-tip {
  margin: auto 0 0; padding: 5px 8px; font-size: 11px; line-height: 1.5; color: var(--dim);
  border-left: 2px solid rgba(0, 229, 255, 0.5);
  background: rgba(0, 229, 255, 0.06);
}

/* ── 右栏② 遥测流 ── */
.tl-wrap { display: flex; flex-direction: column; padding: 6px 8px 4px; overflow: hidden; }
.tl-head {
  display: grid; grid-template-columns: 58px 1.1fr 1fr 1fr;
  gap: 6px; padding: 0 4px 4px; flex-shrink: 0;
  font-size: 10px; color: var(--dim); letter-spacing: 1px;
  border-bottom: 1px solid rgba(46, 155, 255, 0.16);
}
.tl-body { position: relative; flex: 1; overflow: hidden; }
.tl-row {
  display: grid; grid-template-columns: 58px 1.1fr 1fr 1fr;
  gap: 6px; align-items: center;
  height: 21px; padding: 0 4px;
  font-size: 11px; color: var(--txt);
  border-bottom: 1px dashed rgba(46, 155, 255, 0.08);
  white-space: nowrap; overflow: hidden;
}
.tl-row .t { color: var(--dim); font-size: 10px; }
.tl-row .d, .tl-row .s { overflow: hidden; text-overflow: ellipsis; color: var(--txt); }
.tl-row .d { color: var(--dim); }
.tl-row .v { display: inline-flex; align-items: center; justify-content: flex-end; overflow: hidden; }
.tl-row .v.warning { color: var(--warn); }
.tl-enter-active { transition: all 0.35s ease; }
.tl-enter-from { opacity: 0; transform: translateY(-8px); }
.tl-leave-active { position: absolute; opacity: 0; width: 100%; }
.tl-move { transition: transform 0.3s ease; }

/* ── 右栏③ 联动中心 ── */
.lk-body { padding: 6px 10px; overflow: hidden; }
.lk-list { display: flex; flex-direction: column; gap: 2px; height: 100%; }
.lk-row {
  display: flex; align-items: flex-start; gap: 10px;
  padding: 6px 0; border-bottom: 1px dashed rgba(46, 155, 255, 0.1);
}
.lk-time { flex-shrink: 0; font-size: 10px; color: var(--dim); padding-top: 2px; }
.lk-main { flex: 1; min-width: 0; }
.lk-main b { display: block; font-size: 12px; color: var(--txt); font-weight: 600; }
.lk-main p { margin: 2px 0 0; font-size: 11px; color: var(--dim); line-height: 1.4; }
.lk-dot {
  flex-shrink: 0; width: 6px; height: 6px; border-radius: 50%;
  margin-top: 5px; background: var(--ok);
  box-shadow: 0 0 6px rgba(34, 197, 94, 0.7);
}

/* ── 5. 底部趋势带 ── */
.trend-band { min-height: 0; }
.trend-chart { flex: 1; min-height: 0; padding: 2px 6px 4px; }

/* ═══════════ 动画 ═══════════ */
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.35; }
}
@keyframes marquee {
  from { transform: translateX(0); }
  to { transform: translateX(-50%); }
}
@keyframes ping {
  0% { transform: scale(0.6); opacity: 0.9; }
  75%, 100% { transform: scale(2.4); opacity: 0; }
}
@keyframes blinkWarn {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}
@keyframes dangerBreathe {
  0%, 100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0), inset 0 0 16px rgba(239, 68, 68, 0.07); }
  50% { box-shadow: 0 0 30px 4px rgba(239, 68, 68, 0.38), inset 0 0 26px rgba(239, 68, 68, 0.16); }
}
@keyframes numIn {
  from { opacity: 0.2; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}
/* 顶部扫描线：仅用于巷道态势图面板与告警 KPI 卡 */
@keyframes scanDown {
  0% { top: 0; opacity: 0; }
  8% { opacity: 1; }
  88% { opacity: 1; }
  100% { top: calc(100% - 2px); opacity: 0; }
}
@keyframes flow {
  from { stroke-dashoffset: 0; }
  to { stroke-dashoffset: -36; }
}
@keyframes spark {
  from { opacity: 0.25; }
  to { opacity: 1; }
}
</style>
