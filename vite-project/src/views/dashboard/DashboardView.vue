<script setup lang="ts">
defineOptions({ name: 'Dashboard' })
import { computed, onMounted, onUnmounted, onActivated, onDeactivated, ref, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useDeviceStore } from '../../stores/device'
import { useWebSocket, type WsDeviceData } from '../../stores/websocket'
import { realApi } from '../../api/realApi'
import { 
  TrendCharts, 
  FullScreen, 
  Refresh, 
  Bell, 
  Cpu, 
  Connection, 
  DataAnalysis,
  CircleCheck,
  WarningFilled,
  Odometer,
  Histogram,
  Setting,
  VideoPlay,
  Opportunity,
  Monitor
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'

const router = useRouter()
const deviceStore = useDeviceStore()
const ws = useWebSocket()

// ── 全屏控制 ─────────────────────────────────────────────────────────────
const isFullscreen = ref(false)
function toggleFullScreen() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen().catch(() => {})
    isFullscreen.value = true
  } else {
    if (document.exitFullscreen) {
      document.exitFullscreen().catch(() => {})
      isFullscreen.value = false
    }
  }
}

// ── 时钟与运行时间 ────────────────────────────────────────────────────────
const currentTime = ref('')
const runningDuration = ref('')
let clockTimer: ReturnType<typeof setInterval> | undefined
const startTime = Date.now() - (142 * 24 * 3600 + 8 * 3600 + 32 * 60) * 1000

function updateClock() {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  const weekDays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  currentTime.value = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())} ${weekDays[d.getDay()]}`
  
  const diff = Math.floor((Date.now() - startTime) / 1000)
  const days = Math.floor(diff / 86400)
  const hours = Math.floor((diff % 86400) / 3600)
  const mins = Math.floor((diff % 3600) / 60)
  const secs = diff % 60
  runningDuration.value = `${days}天 ${pad(hours)}:${pad(mins)}:${pad(secs)}`
}

// ── 顶部核心 KPI 指标 (支持高频微波动) ──────────────────────────────────────
const qps = ref(85420)
const flinkLatency = ref(32)
const lakeStorage = ref('4.82 TB')
const kafkaThroughput = ref('1.45 M/s')
const cleanPassRate = ref('99.92%')
const deadValueBlocked = ref(1420)
const onlineRate = computed(() => deviceStore.totalCount ? Math.round(deviceStore.onlineCount / deviceStore.totalCount * 100) : 0)

// ── 中间区域：环境传感器群 (左子区) ──────────────────────────────────────
const envData = ref({
  ch4: 0.32,       // 甲烷 %
  co: 5.4,         // 一氧化碳 ppm
  co2: 0.06,       // 二氧化碳 %
  temp: 23.8,      // 环境温度 ℃
  humidity: 62.5,  // 湿度 %RH
  dust: 14.2,      // 粉尘 mg/m³
  windSpeed: 12.5, // 风速 m/s
  pressure: 101.3  // 气压 kPa
})

// ── 中间区域：掘进机设备传感器群 (右子区) ──────────────────────────────────
const devSensors = ref({
  cutterTemp: 56.4,   // 截割电机温度 ℃
  oilPumpTemp: 48.2,  // 油泵电机温度 ℃
  hydraulicPress: 28.5,// 液压系统压力 MPa
  coolingPress: 1.8,  // 冷却水压力 MPa
  coolingFlow: 65.0,  // 冷却水流量 L/min
  motorCurrent: 168.4,// 主电机运行电流 A
  vibration: 2.8,     // 截割三轴振动 mm/s
  gearOilTemp: 52.1   // 齿轮箱油温 ℃
})

// 掘进机姿态角与运行状态 (中子区)
const machineState = ref({
  name: 'EBZ-260 智能悬臂式掘进机',
  status: 'CUTTING', // CUTTING, IDLE, HIGH_LOAD, WARNING
  statusText: '截割作业中',
  rpm: 42,
  advance: 1.28,
  pitch: -1.2,
  roll: 0.4,
  yaw: 88.5
})

// ── 右侧：AI 预测性维护 (PHM) 动态状态机 ──────────────────────────────────
// 预设 4 种典型联动工况，支持 TCP 数据联动或自动情境演练
const currentScenario = ref<'NORMAL' | 'HIGH_LOAD' | 'WEAR_WARN' | 'COOLING_ADAPT'>('NORMAL')
const scenarioList = [
  { key: 'NORMAL', name: '正常稳态截割', desc: '各电机与水压温度平稳，健康指数 96+' },
  { key: 'HIGH_LOAD', name: '坚硬岩层/大载荷', desc: '截割电流与温升增加，AI动态下调RUL预测' },
  { key: 'WEAR_WARN', name: '截割齿轻度磨损', desc: '三轴振动超标(4.2mm/s)，触发智能换齿预警' },
  { key: 'COOLING_ADAPT', name: '自适应水冷调节', desc: '喷雾降尘水压自愈联动，温升受控' }
]

const aiPhmState = ref({
  healthScore: 96,
  rulDays: 450,
  wearRate: 14,
  anomalyProb: 3.2,
  recommendation: '截割头传动机构与润滑正常，建议按既定规程每 500 小时巡检',
  statusLevel: 'HEALTHY'
})

const phmFleetList = ref([
  { id: 'EBZ-260-掘进机#1', score: 96, rul: 450, status: 'HEALTHY', type: '掘进机' },
  { id: 'FBD-No7.5-通风机', score: 88, rul: 180, status: 'HEALTHY', type: '通风系统' },
  { id: 'MD-280-主排水泵', score: 76, rul: 64, status: 'ATTENTION', type: '排水泵组' },
  { id: 'DSJ-100-皮带机', score: 58, rul: 12, status: 'CRITICAL', type: '主运输线' }
])

function switchScenario(scKey: 'NORMAL' | 'HIGH_LOAD' | 'WEAR_WARN' | 'COOLING_ADAPT') {
  currentScenario.value = scKey
  if (scKey === 'NORMAL') {
    machineState.value.status = 'CUTTING'
    machineState.value.statusText = '连续截割作业中'
    devSensors.value.cutterTemp = 56.4
    devSensors.value.motorCurrent = 168.4
    devSensors.value.vibration = 2.8
    devSensors.value.hydraulicPress = 28.5
    envData.value.ch4 = 0.32
    aiPhmState.value.healthScore = 96
    aiPhmState.value.rulDays = 450
    aiPhmState.value.anomalyProb = 3.2
    aiPhmState.value.statusLevel = 'HEALTHY'
    aiPhmState.value.recommendation = '主驱动与液压系统健康度极佳，无异常磨损'
    phmFleetList.value[0].score = 96
    phmFleetList.value[0].rul = 450
    phmFleetList.value[0].status = 'HEALTHY'
  } else if (scKey === 'HIGH_LOAD') {
    machineState.value.status = 'HIGH_LOAD'
    machineState.value.statusText = '遇坚硬岩层 (高载荷)'
    devSensors.value.cutterTemp = 74.8
    devSensors.value.motorCurrent = 194.2
    devSensors.value.vibration = 3.6
    devSensors.value.hydraulicPress = 30.2
    envData.value.dust = 28.4
    aiPhmState.value.healthScore = 82
    aiPhmState.value.rulDays = 340
    aiPhmState.value.anomalyProb = 18.5
    aiPhmState.value.statusLevel = 'ATTENTION'
    aiPhmState.value.recommendation = '检测到截割阻力突增，已自动调低推进步进速度0.15m/min'
    phmFleetList.value[0].score = 82
    phmFleetList.value[0].rul = 340
    phmFleetList.value[0].status = 'ATTENTION'
  } else if (scKey === 'WEAR_WARN') {
    machineState.value.status = 'WARNING'
    machineState.value.statusText = '截割齿磨损预警'
    devSensors.value.cutterTemp = 82.5
    devSensors.value.motorCurrent = 188.0
    devSensors.value.vibration = 4.8
    envData.value.ch4 = 0.65
    aiPhmState.value.healthScore = 64
    aiPhmState.value.rulDays = 85
    aiPhmState.value.anomalyProb = 46.8
    aiPhmState.value.statusLevel = 'CRITICAL'
    aiPhmState.value.recommendation = '【AI预警】3#截割齿高频冲击振动超限，建议12小时内停机更换'
    phmFleetList.value[0].score = 64
    phmFleetList.value[0].rul = 85
    phmFleetList.value[0].status = 'CRITICAL'
  } else if (scKey === 'COOLING_ADAPT') {
    machineState.value.status = 'CUTTING'
    machineState.value.statusText = '风水联动自适应喷雾'
    devSensors.value.cutterTemp = 58.2
    devSensors.value.coolingPress = 2.4
    devSensors.value.coolingFlow = 88.0
    envData.value.dust = 11.2
    aiPhmState.value.healthScore = 92
    aiPhmState.value.rulDays = 410
    aiPhmState.value.anomalyProb = 6.4
    aiPhmState.value.statusLevel = 'HEALTHY'
    aiPhmState.value.recommendation = '高压喷雾除尘降温闭环生效中，环境粉尘显著下降'
    phmFleetList.value[0].score = 92
    phmFleetList.value[0].rul = 410
    phmFleetList.value[0].status = 'HEALTHY'
  }
  updateAiPhmChart()
}

// ── 实时遥测流 (WebSocket 推送 + 真实接口填充) ───────────────────────────
interface TelemetryItem {
  id: string
  time: string
  device: string
  sensor: string
  value: number
  unit: string
  status: string
  highlight?: boolean
}
const telemetryStream = ref<TelemetryItem[]>([])
let wsUnsubscribe: (() => void) | null = null

function appendTelemetryData(data: WsDeviceData) {
  if (data.type !== 'data') return
  const item: TelemetryItem = {
    id: `${Date.now()}_${Math.random()}`,
    time: new Date().toLocaleTimeString(),
    device: data.deviceId,
    sensor: data.sensorId,
    value: typeof data.value === 'number' ? data.value : 0,
    unit: data.unit || '',
    status: 'online',
    highlight: true
  }
  telemetryStream.value.unshift(item)
  if (telemetryStream.value.length > 20) {
    telemetryStream.value.pop()
  }
  setTimeout(() => { item.highlight = false }, 1200)

  // 联动更新全套环境与设备实体指标
  const sId = (data.sensorId || '').toLowerCase()
  const v = typeof data.value === 'number' ? data.value : 0

  // 1. 环境指标识别
  if (sId.includes('ch4') || sId.includes('甲烷') || sId.includes('瓦斯')) {
    envData.value.ch4 = +(v).toFixed(2)
  } else if (sId.includes('co2') || sId.includes('二氧化碳')) {
    envData.value.co2 = +(v).toFixed(2)
  } else if (sId.includes('co') || sId.includes('一氧化碳')) {
    envData.value.co = +(v).toFixed(1)
  } else if (sId.includes('humidity') || sId.includes('湿度')) {
    envData.value.humidity = +(v).toFixed(1)
  } else if (sId.includes('dust') || sId.includes('粉尘')) {
    envData.value.dust = +(v).toFixed(1)
  } else if (sId.includes('wind') || sId.includes('风速')) {
    envData.value.windSpeed = +(v).toFixed(1)
  } else if (sId.includes('pressure') && (sId.includes('env') || sId.includes('气压') || sId.includes('负压'))) {
    envData.value.pressure = +(v).toFixed(1)
  }
  // 2. 掘进机本体设备工控指标识别
  else if (sId.includes('cutter') || sId.includes('截割') || (sId.includes('temp') && !sId.includes('env'))) {
    devSensors.value.cutterTemp = +(v).toFixed(1)
  } else if (sId.includes('pump_temp') || sId.includes('油泵温度')) {
    devSensors.value.oilPumpTemp = +(v).toFixed(1)
  } else if (sId.includes('press') || sId.includes('液压') || sId.includes('油压')) {
    devSensors.value.hydraulicPress = +(v).toFixed(1)
  } else if (sId.includes('cooling') || sId.includes('水压') || sId.includes('水温')) {
    devSensors.value.coolingPress = +(v).toFixed(1)
  } else if (sId.includes('flow') || sId.includes('流量')) {
    devSensors.value.coolingFlow = +(v).toFixed(1)
  } else if (sId.includes('current') || sId.includes('电流') || sId.includes('安培')) {
    devSensors.value.motorCurrent = +(v).toFixed(1)
  } else if (sId.includes('vibr') || sId.includes('振动')) {
    devSensors.value.vibration = +(v).toFixed(1)
  } else if (sId.includes('gear') || sId.includes('齿轮')) {
    devSensors.value.gearOilTemp = +(v).toFixed(1)
  }

  // 3. 基于真实数据帧实时驱动 AI 动态推断
  let healthDeduction = 0
  if (devSensors.value.cutterTemp > 70) healthDeduction += (devSensors.value.cutterTemp - 70) * 2
  if (devSensors.value.vibration > 3.5) healthDeduction += (devSensors.value.vibration - 3.5) * 8
  if (devSensors.value.hydraulicPress > 30) healthDeduction += (devSensors.value.hydraulicPress - 30) * 4
  if (devSensors.value.motorCurrent > 185) healthDeduction += (devSensors.value.motorCurrent - 185) * 1.5

  const score = Math.max(45, Math.min(99, Math.round(98 - healthDeduction)))
  aiPhmState.value.healthScore = score
  aiPhmState.value.rulDays = Math.round(score * 4.6)
  aiPhmState.value.anomalyProb = +(Math.max(1.5, (100 - score) * 0.8)).toFixed(1)

  if (score < 70) {
    aiPhmState.value.statusLevel = 'CRITICAL'
    aiPhmState.value.recommendation = '【AI实时警告】截割负载与振动复合超限，建议立即排查截割齿与供油状态'
    machineState.value.status = 'WARNING'
    machineState.value.statusText = '工况超限预警'
  } else if (score < 85) {
    aiPhmState.value.statusLevel = 'ATTENTION'
    aiPhmState.value.recommendation = '工况负荷处于高位，建议适当降低掘进截割进尺速率'
    machineState.value.status = 'HIGH_LOAD'
    machineState.value.statusText = '大负荷截割'
  } else {
    aiPhmState.value.statusLevel = 'HEALTHY'
    aiPhmState.value.recommendation = '设备运行健康稳定，各传感器温度与压力在安全包络线内'
    machineState.value.status = 'CUTTING'
    machineState.value.statusText = '稳定截割中'
  }
  phmFleetList.value[0].score = score
  phmFleetList.value[0].rul = aiPhmState.value.rulDays
  phmFleetList.value[0].status = aiPhmState.value.statusLevel
  updateAiPhmChart()
}

function seedInitialTelemetry() {
  const sensors = [
    { dev: 'EBZ-260-掘进机#01', sensor: '截割电机温度', unit: '℃', min: 52, max: 70 },
    { dev: 'EBZ-260-掘进机#01', sensor: '瓦斯甲烷CH4', unit: '%', min: 0.2, max: 0.6 },
    { dev: 'EBZ-260-掘进机#01', sensor: '主液压泵压力', unit: 'MPa', min: 26, max: 30 },
    { dev: 'FBD-No7.5-通风机', sensor: '工作面风速', unit: 'm/s', min: 10, max: 15 },
    { dev: 'MD-280-排水泵#02', sensor: '管网瞬时流量', unit: 'm³/h', min: 260, max: 290 },
    { dev: 'DEV-环境站#03', sensor: '粉尘综合浓度', unit: 'mg/m³', min: 12, max: 25 }
  ]
  
  if (telemetryStream.value.length === 0) {
    for (let i = 0; i < 10; i++) {
      const s = sensors[i % sensors.length]
      const val = +(s.min + Math.random() * (s.max - s.min)).toFixed(2)
      telemetryStream.value.push({
        id: `init_${i}`,
        time: new Date(Date.now() - (10 - i) * 3000).toLocaleTimeString(),
        device: s.dev,
        sensor: s.sensor,
        value: val,
        unit: s.unit,
        status: val > s.max * 0.9 ? 'warning' : 'online'
      })
    }
  }
}

// ── ECharts 实例 ─────────────────────────────────────────────────────────
const deviceTypeChartRef = ref<HTMLDivElement | null>(null)
const qualityRadarRef = ref<HTMLDivElement | null>(null)
const throughputBarRef = ref<HTMLDivElement | null>(null)
const trendChartRef = ref<HTMLDivElement | null>(null)
const phmTrendChartRef = ref<HTMLDivElement | null>(null)

let deviceTypeChart: echarts.ECharts | null = null
let qualityRadarChart: echarts.ECharts | null = null
let throughputBarChart: echarts.ECharts | null = null
let trendChart: echarts.ECharts | null = null
let phmTrendChart: echarts.ECharts | null = null

// 趋势波形
const trendTimes = ref<string[]>([])
const trendAvg = ref<number[]>([])
const trendMax = ref<number[]>([])
const trendPressure = ref<number[]>([])

// AI 寿命衰退预测波形
const phmPoints = ref<number[]>([98, 97, 96, 95, 96, 94, 96, 95, 96])

function initTrendData() {
  const now = Date.now()
  trendTimes.value = []
  trendAvg.value = []
  trendMax.value = []
  trendPressure.value = []
  for (let i = 24; i >= 0; i--) {
    const t = new Date(now - i * 60 * 1000)
    trendTimes.value.push(`${String(t.getHours()).padStart(2, '0')}:${String(t.getMinutes()).padStart(2, '0')}`)
    const base = 52 + Math.sin(i / 3) * 8 + Math.random() * 3
    trendAvg.value.push(+base.toFixed(1))
    trendMax.value.push(+(base + 8 + Math.random() * 4).toFixed(1))
    trendPressure.value.push(+(28.0 + Math.cos(i / 4) * 1.5 + Math.random() * 0.4).toFixed(1))
  }
}

function initAllCharts() {
  // 1. 设备类型分布
  if (deviceTypeChartRef.value) {
    deviceTypeChart = echarts.init(deviceTypeChartRef.value)
    deviceTypeChart.setOption({
      tooltip: {
        trigger: 'item',
        backgroundColor: 'rgba(3, 16, 38, 0.92)',
        borderColor: '#00f0ff',
        textStyle: { color: '#fff', fontSize: 12 }
      },
      legend: {
        orient: 'vertical',
        right: '4%',
        top: 'center',
        itemWidth: 10,
        itemHeight: 10,
        textStyle: { color: '#8fa4bf', fontSize: 11 }
      },
      color: ['#00f0ff', '#00ff9d', '#2a72ff', '#ffb700', '#ff3d68', '#a855f7'],
      series: [
        {
          name: '设备类型分布',
          type: 'pie',
          radius: ['25%', '75%'],
          center: ['38%', '50%'],
          roseType: 'radius',
          itemStyle: { borderRadius: 4, borderColor: '#030c1e', borderWidth: 2 },
          label: { show: false },
          data: [
            { value: 12, name: '掘进机本体' },
            { value: 18, name: '瓦斯/环境站' },
            { value: 10, name: '主通风机' },
            { value: 8, name: '主排水泵' },
            { value: 14, name: '主皮带运线' },
            { value: 6, name: '供配电系统' }
          ]
        }
      ]
    })
  }

  // 2. Flink 数据质量雷达
  if (qualityRadarRef.value) {
    qualityRadarChart = echarts.init(qualityRadarRef.value)
    qualityRadarChart.setOption({
      radar: {
        indicator: [
          { name: '死值卡死过滤', max: 100 },
          { name: '物理极值拦截', max: 100 },
          { name: '网络迟到重排', max: 100 },
          { name: '断网重传对齐', max: 100 },
          { name: '方差波动识别', max: 100 }
        ],
        center: ['50%', '52%'],
        radius: '68%',
        splitNumber: 4,
        axisName: { color: '#8fa4bf', fontSize: 11 },
        splitLine: { lineStyle: { color: 'rgba(0, 240, 255, 0.2)' } },
        splitArea: { areaStyle: { color: ['rgba(0, 240, 255, 0.02)', 'rgba(0, 255, 157, 0.05)'] } },
        axisLine: { lineStyle: { color: 'rgba(0, 240, 255, 0.3)' } }
      },
      series: [
        {
          type: 'radar',
          data: [
            {
              value: [99.2, 98.6, 95.4, 97.8, 99.5],
              name: '质量得分',
              itemStyle: { color: '#00ff9d' },
              lineStyle: { width: 2, color: '#00ff9d' },
              areaStyle: { color: 'rgba(0, 255, 157, 0.35)' }
            }
          ]
        }
      ]
    })
  }

  // 3. Kafka 分区速率柱状图
  if (throughputBarRef.value) {
    throughputBarChart = echarts.init(throughputBarRef.value)
    throughputBarChart.setOption({
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(3, 16, 38, 0.92)',
        borderColor: '#2a72ff',
        textStyle: { color: '#fff' }
      },
      grid: { top: '15%', right: '3%', bottom: '15%', left: '8%', containLabel: true },
      xAxis: {
        type: 'category',
        data: ['P0-P7', 'P8-P15', 'P16-P23', 'P24-P31'],
        axisLabel: { color: '#8fa4bf', fontSize: 11 },
        axisLine: { lineStyle: { color: 'rgba(0, 240, 255, 0.2)' } }
      },
      yAxis: {
        type: 'value',
        name: 'k msg/s',
        nameTextStyle: { color: '#8fa4bf', fontSize: 10 },
        axisLabel: { color: '#8fa4bf', fontSize: 10 },
        splitLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.05)', type: 'dashed' } }
      },
      series: [
        {
          name: '写入吞吐',
          type: 'bar',
          barWidth: 14,
          itemStyle: {
            borderRadius: [4, 4, 0, 0],
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#00f0ff' },
              { offset: 1, color: 'rgba(0, 240, 255, 0.1)' }
            ])
          },
          data: [42.5, 38.2, 45.1, 39.8]
        },
        {
          name: 'Flink消费',
          type: 'bar',
          barWidth: 14,
          itemStyle: {
            borderRadius: [4, 4, 0, 0],
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#00ff9d' },
              { offset: 1, color: 'rgba(0, 255, 157, 0.1)' }
            ])
          },
          data: [42.1, 38.0, 44.9, 39.6]
        }
      ]
    })
  }

  // 4. 双轴多维趋势波形图
  if (trendChartRef.value) {
    trendChart = echarts.init(trendChartRef.value)
    trendChart.setOption({
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(3, 16, 38, 0.95)',
        borderColor: '#00f0ff',
        textStyle: { color: '#fff', fontSize: 12 }
      },
      legend: {
        data: ['截割电机温度 (℃)', '瞬时峰值 (℃)', '液压主压力 (MPa)'],
        textStyle: { color: '#8fa4bf', fontSize: 11 },
        top: 2,
        right: '3%'
      },
      grid: { top: 38, right: '5%', bottom: 20, left: '4%', containLabel: true },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: trendTimes.value,
        axisLabel: { color: '#8fa4bf', fontSize: 10 },
        axisLine: { lineStyle: { color: 'rgba(0, 240, 255, 0.2)' } }
      },
      yAxis: [
        {
          type: 'value',
          name: '温度 (℃)',
          nameTextStyle: { color: '#8fa4bf', fontSize: 10 },
          axisLabel: { color: '#8fa4bf', fontSize: 10 },
          splitLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.05)', type: 'dashed' } }
        },
        {
          type: 'value',
          name: '压力 (MPa)',
          nameTextStyle: { color: '#8fa4bf', fontSize: 10 },
          axisLabel: { color: '#8fa4bf', fontSize: 10 },
          splitLine: { show: false }
        }
      ],
      series: [
        {
          name: '截割电机温度 (℃)',
          type: 'line',
          smooth: true,
          showSymbol: false,
          itemStyle: { color: '#00f0ff' },
          lineStyle: { width: 2 },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(0, 240, 255, 0.35)' },
              { offset: 1, color: 'rgba(0, 240, 255, 0.01)' }
            ])
          },
          data: trendAvg.value
        },
        {
          name: '瞬时峰值 (℃)',
          type: 'line',
          smooth: true,
          showSymbol: false,
          itemStyle: { color: '#ff3d68' },
          lineStyle: { width: 1.5, type: 'dashed' },
          data: trendMax.value
        },
        {
          name: '液压主压力 (MPa)',
          type: 'line',
          yAxisIndex: 1,
          smooth: true,
          showSymbol: false,
          itemStyle: { color: '#00ff9d' },
          lineStyle: { width: 2 },
          data: trendPressure.value
        }
      ]
    })
  }

  // 5. 右侧 AI 健康度与剩余寿命波形
  if (phmTrendChartRef.value) {
    phmTrendChart = echarts.init(phmTrendChartRef.value)
    updateAiPhmChart()
  }
}

function updateAiPhmChart() {
  if (!phmTrendChart) return
  phmTrendChart.setOption({
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(3, 16, 38, 0.95)',
      borderColor: '#ffb700',
      textStyle: { color: '#fff', fontSize: 11 }
    },
    grid: { top: 15, right: 10, bottom: 18, left: 25, containLabel: true },
    xAxis: {
      type: 'category',
      data: ['T-8', 'T-7', 'T-6', 'T-5', 'T-4', 'T-3', 'T-2', 'T-1', 'Now'],
      axisLabel: { color: '#8fa4bf', fontSize: 9 },
      axisLine: { lineStyle: { color: 'rgba(0, 240, 255, 0.2)' } }
    },
    yAxis: {
      type: 'value',
      min: 50,
      max: 100,
      axisLabel: { color: '#8fa4bf', fontSize: 9 },
      splitLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.05)', type: 'dashed' } }
    },
    series: [
      {
        name: 'AI实时健康指数',
        type: 'line',
        smooth: true,
        showSymbol: true,
        symbolSize: 4,
        itemStyle: { color: aiPhmState.value.statusLevel === 'CRITICAL' ? '#ff3d68' : aiPhmState.value.statusLevel === 'ATTENTION' ? '#ffb700' : '#00ff9d' },
        lineStyle: { width: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: aiPhmState.value.statusLevel === 'CRITICAL' ? 'rgba(255, 61, 104, 0.35)' : 'rgba(0, 255, 157, 0.3)' },
            { offset: 1, color: 'transparent' }
          ])
        },
        data: [...phmPoints.value.slice(0, 8), aiPhmState.value.healthScore]
      }
    ]
  })
}

// ── 动态更新与微波动 ──────────────────────────────────────────────────────
function updateChartsDynamic() {
  qps.value = Math.round(85000 + Math.random() * 1400 - 700)
  flinkLatency.value = Math.round(30 + Math.random() * 6 - 3)
  
  // 自然微波动环境与设备值
  envData.value.temp = +(23.8 + Math.random() * 0.4 - 0.2).toFixed(1)
  envData.value.humidity = +(62.5 + Math.random() * 0.8 - 0.4).toFixed(1)
  if (currentScenario.value === 'NORMAL') {
    devSensors.value.cutterTemp = +(56.4 + Math.random() * 1.2 - 0.6).toFixed(1)
    devSensors.value.motorCurrent = +(168.4 + Math.random() * 3.0 - 1.5).toFixed(1)
  }

  // 更新趋势图
  const now = new Date()
  const timeStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
  trendTimes.value.shift()
  trendTimes.value.push(timeStr)
  
  const lastAvg = trendAvg.value[trendAvg.value.length - 1] || 54
  const nextAvg = +(lastAvg + (Math.random() * 2 - 1)).toFixed(1)
  trendAvg.value.shift()
  trendAvg.value.push(nextAvg)
  
  trendMax.value.shift()
  trendMax.value.push(+(nextAvg + 8 + Math.random() * 2).toFixed(1))
  
  const nextPres = +(devSensors.value.hydraulicPress + (Math.random() * 0.4 - 0.2)).toFixed(1)
  trendPressure.value.shift()
  trendPressure.value.push(nextPres)

  if (trendChart) {
    trendChart.setOption({
      xAxis: { data: trendTimes.value },
      series: [
        { data: trendAvg.value },
        { data: trendMax.value },
        { data: trendPressure.value }
      ]
    })
  }
}

function handleResize() {
  deviceTypeChart?.resize()
  qualityRadarChart?.resize()
  throughputBarChart?.resize()
  trendChart?.resize()
  phmTrendChart?.resize()
}

// ── 告警数据 ─────────────────────────────────────────────────────────────
const recentAlerts = ref<any[]>([])
async function fetchAlerts() {
  try {
    const page = await realApi.getAlertRecords({ status: 'TRIGGERED', size: 5, page: 0 })
    if (page?.content && page.content.length > 0) {
      recentAlerts.value = page.content.map((r: any) => ({
        id: r.id,
        device: r.deviceName || r.deviceId,
        level: r.level || 'WARNING',
        message: r.ruleName || r.title?.split('] ')[1] || '遥测阈值超限触发',
        time: r.triggeredAt ? new Date(r.triggeredAt).toLocaleTimeString() : '刚刚'
      }))
    } else {
      recentAlerts.value = [
        { id: 101, device: 'EBZ-260-掘进机#01', level: 'CRITICAL', message: '截割电机温度升至 74.8℃ (预警阈值 70℃)', time: '16:08:12' },
        { id: 102, device: 'DEV-环境站#03', level: 'WARNING', message: '工作面甲烷浓度轻微上扬至 0.65%', time: '16:09:40' },
        { id: 103, device: 'MD-280-排水泵#02', level: 'INFO', message: '自动排空自愈保护触发，备用回路已联动', time: '16:10:05' }
      ]
    }
  } catch {
    recentAlerts.value = [
      { id: 101, device: 'EBZ-260-掘进机#01', level: 'CRITICAL', message: '截割电机温度升至 74.8℃ (预警阈值 70℃)', time: '16:08:12' }
    ]
  }
}

// ── 定时器与自动场景轮询 (让大屏彻底动起来) ────────────────────────────────
let mainTimer: ReturnType<typeof setInterval> | undefined
let scenarioCycleTimer: ReturnType<typeof setInterval> | undefined

function startScenarioCycle() {
  let idx = 0
  const seq: ('NORMAL' | 'HIGH_LOAD' | 'WEAR_WARN' | 'COOLING_ADAPT')[] = ['NORMAL', 'HIGH_LOAD', 'COOLING_ADAPT', 'NORMAL']
  scenarioCycleTimer = setInterval(() => {
    idx = (idx + 1) % seq.length
    switchScenario(seq[idx])
  }, 12000)
}

function startDataLoop() {
  stopDataLoop()
  ws.connect()
  wsUnsubscribe = ws.onAllDeviceData(appendTelemetryData)
  deviceStore.startRealtimeUpdates(4000)
  mainTimer = setInterval(() => {
    updateChartsDynamic()
  }, 3000)
  startScenarioCycle()
}

function stopDataLoop() {
  deviceStore.stopRealtimeUpdates()
  if (wsUnsubscribe) { wsUnsubscribe(); wsUnsubscribe = null }
  if (mainTimer) { clearInterval(mainTimer); mainTimer = undefined }
  if (scenarioCycleTimer) { clearInterval(scenarioCycleTimer); scenarioCycleTimer = undefined }
}

onMounted(async () => {
  updateClock()
  clockTimer = setInterval(updateClock, 1000)
  initTrendData()
  seedInitialTelemetry()
  
  await Promise.allSettled([
    deviceStore.fetchDevices(),
    fetchAlerts()
  ])
  
  nextTick(() => {
    initAllCharts()
    window.addEventListener('resize', handleResize)
  })
  
  startDataLoop()
})

onActivated(() => { startDataLoop() })
onDeactivated(() => { stopDataLoop() })

onUnmounted(() => {
  stopDataLoop()
  if (clockTimer) clearInterval(clockTimer)
  window.removeEventListener('resize', handleResize)
  deviceTypeChart?.dispose()
  qualityRadarChart?.dispose()
  throughputBarChart?.dispose()
  trendChart?.dispose()
  phmTrendChart?.dispose()
})

function go(path: string) { router.push(path) }
</script>

<template>
  <div class="dv-wrapper" :class="{ 'is-fullscreen': isFullscreen }">
    <div class="dv-screen">
      
      <!-- ════════════ 1. 顶部 Header ════════════ -->
      <header class="dv-header">
        <div class="header-side header-left">
          <div class="time-box">
            <span class="live-blink-dot"></span>
            <span class="time-val font-mono">{{ currentTime }}</span>
          </div>
          <div class="running-box">
            <span class="kicker-tag">系统安全运行</span>
            <strong class="run-val font-mono">{{ runningDuration }}</strong>
          </div>
        </div>

        <div class="header-center">
          <div class="header-title-glow">工业物联网大数据与实时湖仓监控中心</div>
          <div class="header-subtitle">INDUSTRIAL IOT BIG DATA & LAKEHOUSE OPERATIONS CENTER</div>
        </div>

        <div class="header-side header-right">
          <div class="lake-nodes">
            <span class="node-pill online"><i class="dot"></i> Flink 1.18</span>
            <span class="node-pill online"><i class="dot"></i> Kafka 32P</span>
            <span class="node-pill online"><i class="dot"></i> Iceberg</span>
          </div>
          <div class="action-buttons">
            <el-tooltip content="全屏切换" placement="bottom">
              <button class="icon-btn" @click="toggleFullScreen">
                <el-icon><FullScreen /></el-icon>
              </button>
            </el-tooltip>
            <button class="nav-btn primary" @click="go('/monitor')">
              <el-icon><TrendCharts /></el-icon> 实时监控
            </button>
            <button class="nav-btn" @click="go('/devices')">
              <el-icon><Cpu /></el-icon> 设备中心
            </button>
          </div>
        </div>
      </header>

      <!-- ════════════ 2. 核心大数据与指标卡片阵列 (7大KPI) ════════════ -->
      <section class="kpi-array">
        <div class="kpi-card">
          <div class="kpi-icon blue"><el-icon><Cpu /></el-icon></div>
          <div class="kpi-body">
            <span class="kpi-label">设备接入总量</span>
            <strong class="kpi-num txt-blue font-mono">{{ deviceStore.totalCount || 50 }} <small>台</small></strong>
            <span class="kpi-sub">多协议统一网关</span>
          </div>
        </div>

        <div class="kpi-card">
          <div class="kpi-icon green"><el-icon><CircleCheck /></el-icon></div>
          <div class="kpi-body">
            <span class="kpi-label">在线设备数</span>
            <strong class="kpi-num txt-green font-mono">{{ deviceStore.onlineCount || 47 }} <small>台</small></strong>
            <span class="kpi-sub">在线率 {{ onlineRate || 94 }}%</span>
          </div>
        </div>

        <div class="kpi-card">
          <div class="kpi-icon cyan"><el-icon><DataAnalysis /></el-icon></div>
          <div class="kpi-body">
            <span class="kpi-label">实时流接入 (QPS)</span>
            <strong class="kpi-num txt-cyan font-mono">{{ qps.toLocaleString() }} <small>pts/s</small></strong>
            <span class="kpi-sub">Flink 延迟: <em class="txt-green font-mono">{{ flinkLatency }}ms</em></span>
          </div>
        </div>

        <div class="kpi-card">
          <div class="kpi-icon purple"><el-icon><Histogram /></el-icon></div>
          <div class="kpi-body">
            <span class="kpi-label">Kafka 吞吐速率</span>
            <strong class="kpi-num txt-purple font-mono">{{ kafkaThroughput }}</strong>
            <span class="kpi-sub">32 并发 Partitions</span>
          </div>
        </div>

        <div class="kpi-card">
          <div class="kpi-icon amber"><el-icon><Odometer /></el-icon></div>
          <div class="kpi-body">
            <span class="kpi-label">湖仓累计存储</span>
            <strong class="kpi-num txt-amber font-mono">{{ lakeStorage }}</strong>
            <span class="kpi-sub">Iceberg + TDengine</span>
          </div>
        </div>

        <div class="kpi-card">
          <div class="kpi-icon teal"><el-icon><Connection /></el-icon></div>
          <div class="kpi-body">
            <span class="kpi-label">流清洗合格率</span>
            <strong class="kpi-num txt-teal font-mono">{{ cleanPassRate }}</strong>
            <span class="kpi-sub">已阻断死值 {{ deadValueBlocked }}</span>
          </div>
        </div>

        <div class="kpi-card">
          <div class="kpi-icon red"><el-icon><WarningFilled /></el-icon></div>
          <div class="kpi-body">
            <span class="kpi-label">待响应告警</span>
            <strong class="kpi-num txt-red font-mono">{{ recentAlerts.length }} <small>条</small></strong>
            <span class="kpi-sub">自动溯源闭环</span>
          </div>
        </div>
      </section>

      <!-- ════════════ 3. 主体内容布局 (左 - 中 - 右) ════════════ -->
      <main class="dv-main-grid">
        
        <!-- ── 左侧：设备分布与流计算质量 ── -->
        <aside class="grid-col col-left">
          <!-- 模块 1: 设备类型分布 -->
          <div class="dv-panel">
            <div class="panel-header">
              <span class="panel-title-text">设备类型与拓扑分布</span>
              <span class="panel-tag">ROSE CHART</span>
            </div>
            <div class="panel-body">
              <div ref="deviceTypeChartRef" class="chart-box"></div>
            </div>
            <div class="corner-mark top-l"></div><div class="corner-mark top-r"></div>
            <div class="corner-mark btm-l"></div><div class="corner-mark btm-r"></div>
          </div>

          <!-- 模块 2: Flink 数据质量清洗 -->
          <div class="dv-panel">
            <div class="panel-header">
              <span class="panel-title-text">Flink 数据质量清洗雷达</span>
              <span class="panel-tag tag-green">AI 实时方差</span>
            </div>
            <div class="panel-body">
              <div ref="qualityRadarRef" class="chart-box"></div>
            </div>
            <div class="corner-mark top-l"></div><div class="corner-mark top-r"></div>
            <div class="corner-mark btm-l"></div><div class="corner-mark btm-r"></div>
          </div>

          <!-- 模块 3: Kafka 分区速率 -->
          <div class="dv-panel">
            <div class="panel-header">
              <span class="panel-title-text">Kafka 消息分区吞吐</span>
              <span class="panel-tag">32 PARTITIONS</span>
            </div>
            <div class="panel-body">
              <div ref="throughputBarRef" class="chart-box"></div>
            </div>
            <div class="corner-mark top-l"></div><div class="corner-mark top-r"></div>
            <div class="corner-mark btm-l"></div><div class="corner-mark btm-r"></div>
          </div>
        </aside>

        <!-- ── 中间核心区：三大子区域 (环境传感器 + 掘进机模型 + 本体设备传感器) ── -->
        <section class="grid-col col-center">
          
          <!-- ★ 重构核心：中间三区域主视窗 ★ -->
          <div class="dv-panel roadheader-trio-panel">
            <div class="panel-header trio-header">
              <div class="trio-title-left">
                <span class="panel-title-text">掘进工作面与掘进机本体智能监控系统</span>
                <span class="machine-status-badge" :class="machineState.status.toLowerCase()">
                  <span class="live-dot"></span> {{ machineState.statusText }}
                </span>
              </div>
              
              <!-- 演示工况快捷切换 (让大屏彻底动起来) -->
              <div class="scenario-toggles">
                <span class="sc-label">工况情境:</span>
                <button 
                  v-for="sc in scenarioList" 
                  :key="sc.key" 
                  class="sc-btn"
                  :class="{ active: currentScenario === sc.key }"
                  @click="switchScenario(sc.key as any)"
                >
                  {{ sc.name }}
                </button>
              </div>
            </div>

            <!-- 三区域主体 -->
            <div class="trio-body">
              
              <!-- 【子区域 1：环境传感器数据 (左边)】 -->
              <div class="trio-sub-col sub-env">
                <div class="sub-col-title">
                  <span>🍃 巷道环境监测群</span>
                  <small>实时通风安全</small>
                </div>
                <div class="env-cards-grid">
                  
                  <div class="env-item" :class="{ warn: envData.ch4 > 0.5 }">
                    <div class="env-item-header">
                      <span class="env-name">甲烷浓度 (CH4)</span>
                      <span class="env-status" :class="envData.ch4 > 0.5 ? 'txt-orange' : 'txt-green'">
                        {{ envData.ch4 > 0.5 ? '轻微上扬' : '安全正常' }}
                      </span>
                    </div>
                    <div class="env-val-row">
                      <strong class="env-val font-mono" :class="envData.ch4 > 0.5 ? 'txt-orange' : 'txt-cyan'">
                        {{ envData.ch4 }}
                      </strong>
                      <span class="env-unit">%</span>
                    </div>
                    <div class="env-bar"><div class="env-bar-fill" :style="{ width: (envData.ch4 * 100) + '%' }"></div></div>
                  </div>

                  <div class="env-item">
                    <div class="env-item-header">
                      <span class="env-name">一氧化碳 (CO)</span>
                      <span class="env-status txt-green">正常</span>
                    </div>
                    <div class="env-val-row">
                      <strong class="env-val font-mono txt-cyan">{{ envData.co }}</strong>
                      <span class="env-unit">ppm</span>
                    </div>
                    <div class="env-bar"><div class="env-bar-fill" :style="{ width: (envData.co * 8) + '%' }"></div></div>
                  </div>

                  <div class="env-item">
                    <div class="env-item-header">
                      <span class="env-name">环境温度 / 湿度</span>
                    </div>
                    <div class="env-val-row">
                      <strong class="env-val font-mono txt-green">{{ envData.temp }} <small>℃</small></strong>
                      <span class="env-sep">/</span>
                      <strong class="env-val font-mono txt-cyan">{{ envData.humidity }} <small>%</small></strong>
                    </div>
                  </div>

                  <div class="env-item" :class="{ warn: envData.dust > 20 }">
                    <div class="env-item-header">
                      <span class="env-name">工作面粉尘浓度</span>
                      <span class="env-status" :class="envData.dust > 20 ? 'txt-orange' : 'txt-green'">
                        {{ envData.dust > 20 ? '开启喷雾' : '优良' }}
                      </span>
                    </div>
                    <div class="env-val-row">
                      <strong class="env-val font-mono" :class="envData.dust > 20 ? 'txt-orange' : 'txt-teal'">
                        {{ envData.dust }}
                      </strong>
                      <span class="env-unit">mg/m³</span>
                    </div>
                  </div>

                  <div class="env-item">
                    <div class="env-item-header">
                      <span class="env-name">局部通风风速</span>
                    </div>
                    <div class="env-val-row">
                      <strong class="env-val font-mono txt-cyan">{{ envData.windSpeed }}</strong>
                      <span class="env-unit">m/s</span>
                    </div>
                  </div>

                  <div class="env-item">
                    <div class="env-item-header">
                      <span class="env-name">巷道大气压 / 负压</span>
                    </div>
                    <div class="env-val-row">
                      <strong class="env-val font-mono txt-purple">{{ envData.pressure }}</strong>
                      <span class="env-unit">kPa</span>
                    </div>
                  </div>

                </div>
              </div>

              <!-- 【子区域 2：掘进机图片与数字模型中心 (中间)】 -->
              <div class="trio-sub-col sub-machine">
                <div class="machine-display-frame">
                  <!-- 顶部机型与工况标识 -->
                  <div class="machine-head-info">
                    <div class="machine-type-tag">EBZ-260 智能重型悬臂式掘进机</div>
                    <div class="machine-attitude-box font-mono">
                      <span>俯仰: <em>{{ machineState.pitch }}°</em></span>
                      <span>横滚: <em>{{ machineState.roll }}°</em></span>
                      <span>航向: <em>{{ machineState.yaw }}°</em></span>
                    </div>
                  </div>

                  <!-- 掘进机立体图与科技光圈 -->
                  <div class="machine-img-wrapper">
                    <div class="cyber-glow-bg"></div>
                    <img src="/roadheader.png" alt="掘进机模型" class="machine-img" />
                    
                    <!-- 动态高亮锚点 (Hotspots 精准对齐机身结构) -->
                    <div class="hotspot pt-cutter">
                      <span class="hotspot-dot"></span>
                      <div class="hotspot-tip font-mono">截割头: {{ devSensors.cutterTemp }}℃</div>
                    </div>

                    <div class="hotspot pt-pump">
                      <span class="hotspot-dot"></span>
                      <div class="hotspot-tip font-mono">主泵压: {{ devSensors.hydraulicPress }}MPa</div>
                    </div>

                    <div class="hotspot pt-track">
                      <span class="hotspot-dot"></span>
                      <div class="hotspot-tip font-mono">履带进尺: {{ machineState.advance }}m</div>
                    </div>
                  </div>

                  <!-- 底部作业参数指示带 -->
                  <div class="machine-foot-bar">
                    <div class="foot-param">
                      <span>截割转速</span>
                      <strong class="font-mono txt-cyan">{{ machineState.rpm }} <small>rpm</small></strong>
                    </div>
                    <div class="foot-param">
                      <span>主电机电流</span>
                      <strong class="font-mono" :class="devSensors.motorCurrent > 190 ? 'txt-orange' : 'txt-green'">
                        {{ devSensors.motorCurrent }} <small>A</small>
                      </strong>
                    </div>
                    <div class="foot-param">
                      <span>推进位移</span>
                      <strong class="font-mono txt-purple">{{ machineState.advance }} <small>m</small></strong>
                    </div>
                    <div class="foot-param">
                      <span>冷却水流量</span>
                      <strong class="font-mono txt-teal">{{ devSensors.coolingFlow }} <small>L/min</small></strong>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 【子区域 3：掘进机本体设备传感器数据 (右边)】 -->
              <div class="trio-sub-col sub-device">
                <div class="sub-col-title">
                  <span>⚙️ 掘进机本体传感器</span>
                  <small>工控与动力指标</small>
                </div>
                <div class="device-cards-grid">
                  
                  <div class="dev-item" :class="{ alert: devSensors.cutterTemp > 75 }">
                    <div class="dev-item-header">
                      <span class="dev-name">截割头电机温度</span>
                      <span class="dev-tag" :class="devSensors.cutterTemp > 75 ? 'tag-red' : 'tag-green'">
                        {{ devSensors.cutterTemp > 75 ? '温升告警' : '正常' }}
                      </span>
                    </div>
                    <div class="dev-val-row">
                      <strong class="dev-val font-mono" :class="devSensors.cutterTemp > 75 ? 'txt-red' : 'txt-cyan'">
                        {{ devSensors.cutterTemp }}
                      </strong>
                      <span class="dev-unit">℃</span>
                      <small class="dev-limit font-mono">阈值: 80℃</small>
                    </div>
                    <div class="dev-bar"><div class="dev-bar-fill" :style="{ width: (devSensors.cutterTemp / 85 * 100) + '%' }"></div></div>
                  </div>

                  <div class="dev-item">
                    <div class="dev-item-header">
                      <span class="dev-name">油泵主电机温度</span>
                      <span class="dev-tag tag-green">正常</span>
                    </div>
                    <div class="dev-val-row">
                      <strong class="dev-val font-mono txt-green">{{ devSensors.oilPumpTemp }}</strong>
                      <span class="dev-unit">℃</span>
                    </div>
                  </div>

                  <div class="dev-item" :class="{ warn: devSensors.hydraulicPress > 30 }">
                    <div class="dev-item-header">
                      <span class="dev-name">液压系统主压力</span>
                      <span class="dev-tag" :class="devSensors.hydraulicPress > 30 ? 'tag-amber' : 'tag-green'">
                        {{ devSensors.hydraulicPress > 30 ? '高压运行' : '标准' }}
                      </span>
                    </div>
                    <div class="dev-val-row">
                      <strong class="dev-val font-mono" :class="devSensors.hydraulicPress > 30 ? 'txt-amber' : 'txt-cyan'">
                        {{ devSensors.hydraulicPress }}
                      </strong>
                      <span class="dev-unit">MPa</span>
                      <small class="dev-limit font-mono">额定 31.5</small>
                    </div>
                  </div>

                  <div class="dev-item">
                    <div class="dev-item-header">
                      <span class="dev-name">冷却水回路水压</span>
                    </div>
                    <div class="dev-val-row">
                      <strong class="dev-val font-mono txt-teal">{{ devSensors.coolingPress }}</strong>
                      <span class="dev-unit">MPa</span>
                    </div>
                  </div>

                  <div class="dev-item" :class="{ alert: devSensors.vibration > 4.0 }">
                    <div class="dev-item-header">
                      <span class="dev-name">截割三轴振动烈度</span>
                      <span class="dev-tag" :class="devSensors.vibration > 4.0 ? 'tag-red' : 'tag-green'">
                        {{ devSensors.vibration > 4.0 ? '异常加剧' : '稳态' }}
                      </span>
                    </div>
                    <div class="dev-val-row">
                      <strong class="dev-val font-mono" :class="devSensors.vibration > 4.0 ? 'txt-red' : 'txt-cyan'">
                        {{ devSensors.vibration }}
                      </strong>
                      <span class="dev-unit">mm/s</span>
                      <small class="dev-limit font-mono">RMS</small>
                    </div>
                  </div>

                  <div class="dev-item">
                    <div class="dev-item-header">
                      <span class="dev-name">齿轮箱润滑油温</span>
                    </div>
                    <div class="dev-val-row">
                      <strong class="dev-val font-mono txt-purple">{{ devSensors.gearOilTemp }}</strong>
                      <span class="dev-unit">℃</span>
                    </div>
                  </div>

                </div>
              </div>

            </div>
            
            <div class="corner-mark top-l"></div><div class="corner-mark top-r"></div>
            <div class="corner-mark btm-l"></div><div class="corner-mark btm-r"></div>
          </div>

          <!-- 模块 2: 实时多维流式聚合趋势波形 -->
          <div class="dv-panel trend-panel">
            <div class="panel-header">
              <span class="panel-title-text">实时 1分钟滑动窗口流式多维聚合波形 (Doris & Flink)</span>
              <span class="panel-tag tag-cyan">双 Y 轴连续采样</span>
            </div>
            <div class="panel-body">
              <div ref="trendChartRef" class="chart-box"></div>
            </div>
            <div class="corner-mark top-l"></div><div class="corner-mark top-r"></div>
            <div class="corner-mark btm-l"></div><div class="corner-mark btm-r"></div>
          </div>
        </section>

        <!-- ── 右侧：AI 预测性维护 (根据TCP数据/工况动态跳变) + 遥测流 + 告警 ── -->
        <aside class="grid-col col-right">
          
          <!-- 模块 1: ★ 动态 AI 预测性维护与寿命 (PHM) ★ -->
          <div class="dv-panel phm-panel">
            <div class="panel-header">
              <span class="panel-title-text">AI 预测性维护与寿命 (PHM)</span>
              <span class="panel-tag tag-amber">动态推断中</span>
            </div>
            <div class="panel-body phm-body">
              
              <!-- 掘进机专属 AI 实时健康卡片 (动态响应) -->
              <div class="ai-hero-card" :class="aiPhmState.statusLevel.toLowerCase()">
                <div class="ai-head-row">
                  <div class="ai-title-block">
                    <strong>掘进机主传动健康指数</strong>
                    <small class="font-mono">实时故障概率: {{ aiPhmState.anomalyProb }}%</small>
                  </div>
                  <div class="ai-badge-box" :class="aiPhmState.statusLevel.toLowerCase()">
                    {{ aiPhmState.statusLevel }}
                  </div>
                </div>

                <div class="ai-score-metric">
                  <div class="metric-block">
                    <span class="m-lbl">综合健康评分</span>
                    <div class="m-val-row">
                      <strong class="font-mono score-huge" :class="aiPhmState.statusLevel === 'CRITICAL' ? 'txt-red' : aiPhmState.statusLevel === 'ATTENTION' ? 'txt-amber' : 'txt-green'">
                        {{ aiPhmState.healthScore }}
                      </strong>
                      <span class="score-unit">/ 100</span>
                    </div>
                  </div>
                  
                  <div class="metric-block">
                    <span class="m-lbl">预测剩余寿命 (RUL)</span>
                    <div class="m-val-row">
                      <strong class="font-mono rul-huge txt-cyan">
                        {{ aiPhmState.rulDays }}
                      </strong>
                      <span class="score-unit">天</span>
                    </div>
                  </div>
                </div>

                <!-- 动态微图表 -->
                <div class="ai-spark-chart">
                  <div ref="phmTrendChartRef" style="width: 100%; height: 75px;"></div>
                </div>

                <!-- AI 诊断建议提示框 -->
                <div class="ai-advice-box">
                  <el-icon><Opportunity /></el-icon>
                  <span>{{ aiPhmState.recommendation }}</span>
                </div>
              </div>

              <!-- 全矿机组健康度对比列表 -->
              <div class="phm-fleet-list">
                <div v-for="item in phmFleetList" :key="item.id" class="phm-mini-item" :class="item.status.toLowerCase()">
                  <div class="p-mini-head">
                    <span class="p-name text-ellipsis">{{ item.id }}</span>
                    <span class="p-rul font-mono">{{ item.rul }}天</span>
                  </div>
                  <div class="p-mini-bar">
                    <div class="p-bar-track"><div class="p-bar-val" :style="{ width: item.score + '%' }"></div></div>
                    <span class="p-score font-mono">{{ item.score }}分</span>
                  </div>
                </div>
              </div>

            </div>
            <div class="corner-mark top-l"></div><div class="corner-mark top-r"></div>
            <div class="corner-mark btm-l"></div><div class="corner-mark btm-r"></div>
          </div>

          <!-- 模块 2: 毫秒级实时遥测流 -->
          <div class="dv-panel telemetry-stream-panel">
            <div class="panel-header">
              <span class="panel-title-text">毫秒级遥测数据流 (Live Telemetry)</span>
              <span class="panel-tag tag-green">TCP / WS PUSH</span>
            </div>
            <div class="panel-body telemetry-body">
              <div class="telemetry-table-header">
                <span class="col-time">时间</span>
                <span class="col-dev">设备 / 节点</span>
                <span class="col-sensor">传感器</span>
                <span class="col-val">实时读数</span>
              </div>
              <div class="telemetry-scroll-wrapper">
                <div 
                  v-for="item in telemetryStream" 
                  :key="item.id" 
                  class="telemetry-row-item"
                  :class="{ highlight: item.highlight }"
                >
                  <span class="col-time font-mono">{{ item.time }}</span>
                  <span class="col-dev text-ellipsis">{{ item.device }}</span>
                  <span class="col-sensor text-ellipsis">{{ item.sensor }}</span>
                  <span class="col-val font-mono" :class="item.status === 'warning' ? 'txt-orange' : 'txt-cyan'">
                    {{ Number(item.value).toFixed(2) }} <small>{{ item.unit }}</small>
                  </span>
                </div>
              </div>
            </div>
            <div class="corner-mark top-l"></div><div class="corner-mark top-r"></div>
            <div class="corner-mark btm-l"></div><div class="corner-mark btm-r"></div>
          </div>

          <!-- 模块 3: 实时告警智能溯源中心 -->
          <div class="dv-panel">
            <div class="panel-header">
              <span class="panel-title-text">智能告警联动中心</span>
              <span class="panel-tag tag-red">{{ recentAlerts.length }} 待响应</span>
            </div>
            <div class="panel-body alerts-body">
              <div v-for="alert in recentAlerts" :key="alert.id" class="alert-strip" :class="alert.level.toLowerCase()">
                <div class="alert-indicator"></div>
                <div class="alert-detail">
                  <div class="alert-dev-row">
                    <strong class="dev-name">{{ alert.device }}</strong>
                    <time class="alert-ts font-mono">{{ alert.time }}</time>
                  </div>
                  <div class="alert-msg-text text-ellipsis">{{ alert.message }}</div>
                </div>
              </div>
            </div>
            <div class="corner-mark top-l"></div><div class="corner-mark top-r"></div>
            <div class="corner-mark btm-l"></div><div class="corner-mark btm-r"></div>
          </div>

        </aside>

      </main>

    </div>
  </div>
</template>

<style scoped>
/* ════════════ 全屏与容器主题 ════════════ */
.dv-wrapper {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background-color: #030c1e;
  background-image: 
    radial-gradient(circle at 50% 10%, rgba(0, 240, 255, 0.08) 0%, transparent 60%),
    linear-gradient(rgba(0, 240, 255, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 240, 255, 0.03) 1px, transparent 1px);
  background-size: 100% 100%, 36px 36px, 36px 36px;
  color: #c5d8ea;
  z-index: 100;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  user-select: none;
}

.dv-wrapper.is-fullscreen {
  position: fixed;
  z-index: 99999;
}

.dv-screen {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 10px 18px 14px;
  box-sizing: border-box;
  height: 100%;
  gap: 10px;
}

/* ════════════ 1. Header 样式 ════════════ */
.dv-header {
  height: 60px;
  display: grid;
  grid-template-columns: 320px 1fr 380px;
  align-items: center;
  position: relative;
  border-bottom: 1px solid rgba(0, 240, 255, 0.2);
  background: linear-gradient(180deg, rgba(0, 240, 255, 0.06) 0%, rgba(3, 12, 30, 0.6) 100%);
}

.header-side { display: flex; align-items: center; }
.header-left { gap: 16px; }
.time-box { display: flex; align-items: center; gap: 8px; color: #00f0ff; font-size: 13px; font-weight: 600; }
.live-blink-dot { width: 7px; height: 7px; border-radius: 50%; background: #00ff9d; box-shadow: 0 0 8px #00ff9d; animation: pulse-dot 1.8s infinite; }
@keyframes pulse-dot { 0%, 100% { opacity: 1; transform: scale(1); } 50% { opacity: 0.4; transform: scale(0.8); } }

.running-box { display: flex; flex-direction: column; border-left: 1px solid rgba(0, 240, 255, 0.2); padding-left: 12px; }
.kicker-tag { font-size: 10px; color: #7b94ad; letter-spacing: 0.05em; }
.run-val { font-size: 12px; color: #00ff9d; }

.header-center { text-align: center; }
.header-title-glow { font-size: 23px; font-weight: 800; letter-spacing: 3px; background: linear-gradient(180deg, #ffffff 30%, #00f0ff 100%); -webkit-background-clip: text; -webkit-text-fill-color: transparent; text-shadow: 0 0 20px rgba(0, 240, 255, 0.5); }
.header-subtitle { font-size: 9px; color: rgba(0, 240, 255, 0.7); letter-spacing: 2px; margin-top: 1px; font-family: 'Roboto Mono', monospace; }

.header-right { justify-content: flex-end; gap: 12px; }
.lake-nodes { display: flex; gap: 6px; }
.node-pill { font-size: 10px; padding: 2px 7px; border-radius: 3px; background: rgba(0, 240, 255, 0.08); border: 1px solid rgba(0, 240, 255, 0.25); color: #9db2c9; display: flex; align-items: center; gap: 4px; }
.node-pill.online .dot { width: 5px; height: 5px; border-radius: 50%; background: #00ff9d; box-shadow: 0 0 6px #00ff9d; }

.action-buttons { display: flex; gap: 6px; }
.icon-btn, .nav-btn { background: rgba(0, 240, 255, 0.1); border: 1px solid rgba(0, 240, 255, 0.35); color: #00f0ff; border-radius: 4px; cursor: pointer; display: inline-flex; align-items: center; gap: 4px; font-size: 12px; padding: 4px 10px; transition: all 0.25s; }
.icon-btn:hover, .nav-btn:hover { background: rgba(0, 240, 255, 0.25); box-shadow: 0 0 12px rgba(0, 240, 255, 0.4); color: #fff; }
.nav-btn.primary { background: rgba(0, 240, 255, 0.2); border-color: #00f0ff; font-weight: 600; }

/* ════════════ 2. 7大核心 KPI 阵列 ════════════ */
.kpi-array { display: grid; grid-template-columns: repeat(7, 1fr); gap: 10px; height: 68px; }
.kpi-card { background: rgba(4, 19, 44, 0.7); border: 1px solid rgba(0, 240, 255, 0.2); border-radius: 5px; padding: 6px 10px; display: flex; align-items: center; gap: 8px; position: relative; overflow: hidden; }
.kpi-card::after { content: ''; position: absolute; top: 0; left: 0; width: 100%; height: 2px; background: linear-gradient(90deg, transparent, rgba(0, 240, 255, 0.6), transparent); }

.kpi-icon { width: 34px; height: 34px; border-radius: 6px; display: grid; place-items: center; font-size: 17px; flex-shrink: 0; }
.kpi-icon.blue { background: rgba(42, 114, 255, 0.15); color: #2a72ff; border: 1px solid rgba(42, 114, 255, 0.4); }
.kpi-icon.green { background: rgba(0, 255, 157, 0.15); color: #00ff9d; border: 1px solid rgba(0, 255, 157, 0.4); }
.kpi-icon.cyan { background: rgba(0, 240, 255, 0.15); color: #00f0ff; border: 1px solid rgba(0, 240, 255, 0.4); }
.kpi-icon.purple { background: rgba(168, 85, 247, 0.15); color: #a855f7; border: 1px solid rgba(168, 85, 247, 0.4); }
.kpi-icon.amber { background: rgba(255, 183, 0, 0.15); color: #ffb700; border: 1px solid rgba(255, 183, 0, 0.4); }
.kpi-icon.teal { background: rgba(20, 184, 166, 0.15); color: #14b8a6; border: 1px solid rgba(20, 184, 166, 0.4); }
.kpi-icon.red { background: rgba(255, 61, 104, 0.15); color: #ff3d68; border: 1px solid rgba(255, 61, 104, 0.4); }

.kpi-body { display: flex; flex-direction: column; min-width: 0; }
.kpi-label { font-size: 10px; color: #8fa4bf; white-space: nowrap; }
.kpi-num { font-size: 16px; font-weight: 700; line-height: 1.2; }
.kpi-num small { font-size: 10px; font-weight: 400; color: #7b94ad; }
.kpi-sub { font-size: 9px; color: #657b94; white-space: nowrap; }

/* ════════════ 3. 主体三列排布 ════════════ */
.dv-main-grid { flex: 1; display: grid; grid-template-columns: 270px 1fr 310px; gap: 10px; min-height: 0; }
.grid-col { display: flex; flex-direction: column; gap: 10px; min-height: 0; }

/* 通用面板外壳 */
.dv-panel {
  background: rgba(4, 17, 39, 0.75);
  border: 1px solid rgba(0, 240, 255, 0.18);
  border-radius: 4px;
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 0;
  flex: 1;
  box-shadow: inset 0 0 20px rgba(0, 240, 255, 0.03);
}

.panel-header {
  height: 32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 10px;
  background: linear-gradient(90deg, rgba(0, 240, 255, 0.15) 0%, rgba(0, 240, 255, 0.02) 100%);
  border-bottom: 1px solid rgba(0, 240, 255, 0.15);
  flex-shrink: 0;
}

.panel-title-text { font-size: 12px; font-weight: 700; color: #00f0ff; display: flex; align-items: center; gap: 6px; }
.panel-title-text::before { content: ''; display: inline-block; width: 3px; height: 11px; background: #00f0ff; box-shadow: 0 0 6px #00f0ff; }

.panel-tag { font-size: 9px; font-family: 'Roboto Mono', monospace; padding: 1px 5px; border-radius: 2px; background: rgba(0, 240, 255, 0.1); color: #7b94ad; border: 1px solid rgba(0, 240, 255, 0.2); }
.panel-tag.tag-green { color: #00ff9d; border-color: rgba(0, 255, 157, 0.4); }
.panel-tag.tag-cyan { color: #00f0ff; border-color: rgba(0, 240, 255, 0.4); }
.panel-tag.tag-amber { color: #ffb700; border-color: rgba(255, 183, 0, 0.4); }
.panel-tag.tag-red { color: #ff3d68; border-color: rgba(255, 61, 104, 0.4); }

.panel-body { flex: 1; position: relative; padding: 8px; overflow: hidden; display: flex; flex-direction: column; }
.chart-box { width: 100%; height: 100%; min-height: 100px; }

/* 赛博折角 */
.corner-mark { position: absolute; width: 8px; height: 8px; border: 2px solid transparent; pointer-events: none; }
.corner-mark.top-l { top: -1px; left: -1px; border-top-color: #00f0ff; border-left-color: #00f0ff; }
.corner-mark.top-r { top: -1px; right: -1px; border-top-color: #00f0ff; border-right-color: #00f0ff; }
.corner-mark.btm-l { bottom: -1px; left: -1px; border-bottom-color: #00f0ff; border-left-color: #00f0ff; }
.corner-mark.btm-r { bottom: -1px; right: -1px; border-bottom-color: #00f0ff; border-right-color: #00f0ff; }

/* ════════════ ★ 重构核心：中间三区域主视窗 ★ ════════════ */
.roadheader-trio-panel {
  flex: 1.45;
}

.trio-header {
  padding: 0 10px;
}

.trio-title-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.machine-status-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 3px;
  display: flex;
  align-items: center;
  gap: 5px;
}
.machine-status-badge.cutting { background: rgba(0, 255, 157, 0.15); color: #00ff9d; border: 1px solid rgba(0, 255, 157, 0.35); }
.machine-status-badge.high_load { background: rgba(255, 183, 0, 0.15); color: #ffb700; border: 1px solid rgba(255, 183, 0, 0.35); }
.machine-status-badge.warning { background: rgba(255, 61, 104, 0.15); color: #ff3d68; border: 1px solid rgba(255, 61, 104, 0.35); }

.scenario-toggles {
  display: flex;
  align-items: center;
  gap: 4px;
}

.sc-label {
  font-size: 10px;
  color: #7b94ad;
}

.sc-btn {
  background: rgba(0, 240, 255, 0.08);
  border: 1px solid rgba(0, 240, 255, 0.2);
  color: #8fa4bf;
  border-radius: 3px;
  font-size: 10px;
  padding: 2px 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.sc-btn:hover, .sc-btn.active {
  background: rgba(0, 240, 255, 0.25);
  color: #00f0ff;
  border-color: #00f0ff;
  box-shadow: 0 0 6px rgba(0, 240, 255, 0.4);
}

/* 三区域排布容器 */
.trio-body {
  flex: 1;
  display: grid;
  grid-template-columns: 210px 1fr 220px;
  gap: 8px;
  padding: 8px;
  min-height: 0;
}

.trio-sub-col {
  background: rgba(2, 13, 31, 0.7);
  border: 1px solid rgba(0, 240, 255, 0.12);
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  padding: 6px;
  min-height: 0;
}

.sub-col-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  font-weight: 700;
  color: #00f0ff;
  border-bottom: 1px solid rgba(0, 240, 255, 0.12);
  padding-bottom: 4px;
  margin-bottom: 6px;
}

.sub-col-title small {
  font-size: 9px;
  font-weight: 400;
  color: #7b94ad;
}

/* 左边环境传感器卡片 */
.env-cards-grid, .device-cards-grid {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 5px;
  overflow-y: auto;
}

.env-item, .dev-item {
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 3px;
  padding: 4px 6px;
}

.env-item.warn, .dev-item.warn {
  border-color: rgba(255, 183, 0, 0.35);
  background: rgba(255, 183, 0, 0.04);
}

.dev-item.alert {
  border-color: rgba(255, 61, 104, 0.4);
  background: rgba(255, 61, 104, 0.06);
}

.env-item-header, .dev-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 10px;
  color: #8fa4bf;
}

.env-status, .dev-tag {
  font-size: 9px;
}
.dev-tag.tag-green { color: #00ff9d; }
.dev-tag.tag-amber { color: #ffb700; }
.dev-tag.tag-red { color: #ff3d68; }

.env-val-row, .dev-val-row {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-top: 1px;
}

.env-val, .dev-val {
  font-size: 15px;
  font-weight: 700;
}

.env-unit, .dev-unit {
  font-size: 10px;
  color: #7b94ad;
}

.dev-limit {
  font-size: 9px;
  color: #657b94;
  margin-left: auto;
}

.env-bar, .dev-bar {
  height: 2px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 1px;
  margin-top: 3px;
  overflow: hidden;
}
.env-bar-fill, .dev-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #00f0ff, #00ff9d);
}

/* 中间掘进机图像展示视窗 */
.sub-machine {
  background: radial-gradient(circle at center, rgba(0, 240, 255, 0.09) 0%, rgba(2, 11, 26, 0.9) 80%);
  border: 1px solid rgba(0, 240, 255, 0.2);
}

.machine-display-frame {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  position: relative;
}

.machine-head-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 6px;
  border-bottom: 1px solid rgba(0, 240, 255, 0.15);
}

.machine-type-tag {
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.5px;
}

.machine-attitude-box {
  display: flex;
  gap: 8px;
  font-size: 10px;
  color: #8fa4bf;
}
.machine-attitude-box em {
  color: #00f0ff;
  font-style: normal;
}

/* 掘进机图片区 */
.machine-img-wrapper {
  flex: 1;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 140px;
}

.cyber-glow-bg {
  position: absolute;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(0, 240, 255, 0.2) 0%, transparent 70%);
  filter: blur(12px);
}

.machine-img {
  max-width: 90%;
  max-height: 155px;
  object-fit: contain;
  filter: drop-shadow(0 0 15px rgba(0, 240, 255, 0.45));
  z-index: 2;
  transition: transform 0.3s;
}

.machine-img:hover {
  transform: scale(1.03);
}

/* 锚点精准定位 */
.hotspot {
  position: absolute;
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 4px;
}
.hotspot.pt-cutter { left: 4%; top: 48%; }
.hotspot.pt-pump { right: 28%; top: 16%; }
.hotspot.pt-track { right: 26%; bottom: 8%; }

.hotspot-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #00f0ff;
  box-shadow: 0 0 8px #00f0ff;
  animation: pulse-dot 1.5s infinite;
}

.hotspot-tip {
  font-size: 9px;
  color: #d8e8f8;
  background: rgba(3, 16, 38, 0.85);
  border: 1px solid rgba(0, 240, 255, 0.4);
  padding: 1px 4px;
  border-radius: 2px;
  white-space: nowrap;
}

/* 底部参数带 */
.machine-foot-bar {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 4px;
  background: rgba(0, 240, 255, 0.05);
  border: 1px solid rgba(0, 240, 255, 0.15);
  border-radius: 3px;
  padding: 4px;
  text-align: center;
}

.foot-param span {
  display: block;
  font-size: 9px;
  color: #7b94ad;
}

.foot-param strong {
  font-size: 13px;
}
.foot-param small {
  font-size: 9px;
  font-weight: normal;
  color: #7b94ad;
}

.trend-panel {
  flex: 0.85;
}

/* ════════════ 右侧：AI 动态预测性维护 (PHM) ════════════ */
.phm-panel {
  flex: 1.35;
}

.phm-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 6px;
}

.ai-hero-card {
  background: rgba(2, 15, 36, 0.85);
  border: 1px solid rgba(0, 240, 255, 0.2);
  border-radius: 4px;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: all 0.3s;
}

.ai-hero-card.critical {
  border-color: rgba(255, 61, 104, 0.5);
  background: rgba(255, 61, 104, 0.05);
}

.ai-hero-card.attention {
  border-color: rgba(255, 183, 0, 0.4);
}

.ai-head-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.ai-title-block strong {
  display: block;
  font-size: 11px;
  color: #fff;
}

.ai-title-block small {
  font-size: 9px;
  color: #00f0ff;
}

.ai-badge-box {
  font-size: 9px;
  padding: 1px 5px;
  border-radius: 2px;
  font-weight: 700;
  font-family: 'Roboto Mono', monospace;
}
.ai-badge-box.healthy { background: rgba(0, 255, 157, 0.2); color: #00ff9d; }
.ai-badge-box.attention { background: rgba(255, 183, 0, 0.2); color: #ffb700; }
.ai-badge-box.critical { background: rgba(255, 61, 104, 0.2); color: #ff3d68; }

.ai-score-metric {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  background: rgba(0, 240, 255, 0.04);
  border-radius: 3px;
  padding: 4px 8px;
}

.metric-block .m-lbl {
  font-size: 9px;
  color: #7b94ad;
}

.m-val-row {
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.score-huge { font-size: 20px; font-weight: 800; }
.rul-huge { font-size: 18px; font-weight: 800; }
.score-unit { font-size: 10px; color: #7b94ad; }

.ai-advice-box {
  background: rgba(0, 240, 255, 0.08);
  border: 1px dashed rgba(0, 240, 255, 0.25);
  border-radius: 3px;
  padding: 4px 6px;
  font-size: 10px;
  color: #d0e2f5;
  display: flex;
  align-items: center;
  gap: 5px;
  line-height: 1.3;
}
.ai-advice-box .el-icon {
  color: #ffb700;
  flex-shrink: 0;
}

/* 机组健康对比 */
.phm-fleet-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.phm-mini-item {
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 3px;
  padding: 3px 6px;
}

.p-mini-head {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: #8fa4bf;
}
.p-mini-head .p-rul { color: #ffb700; }

.p-mini-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
}

.p-bar-track {
  flex: 1;
  height: 3px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 2px;
  overflow: hidden;
}
.p-bar-val {
  height: 100%;
  background: linear-gradient(90deg, #2a72ff, #00ff9d);
}
.p-score {
  font-size: 10px;
  color: #00f0ff;
  width: 28px;
  text-align: right;
}

/* 遥测流与告警 */
.telemetry-table-header {
  display: grid;
  grid-template-columns: 55px 75px 1fr 60px;
  font-size: 10px;
  color: #00f0ff;
  padding: 3px 6px;
  background: rgba(0, 240, 255, 0.08);
  border-bottom: 1px solid rgba(0, 240, 255, 0.15);
}

.telemetry-scroll-wrapper {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.telemetry-row-item {
  display: grid;
  grid-template-columns: 55px 75px 1fr 60px;
  align-items: center;
  padding: 3px 6px;
  font-size: 10px;
  border-bottom: 1px dashed rgba(255, 255, 255, 0.04);
}
.telemetry-row-item.highlight { background: rgba(0, 240, 255, 0.25); }

.alerts-body {
  display: flex;
  flex-direction: column;
  gap: 5px;
  overflow-y: auto;
}

.alert-strip {
  display: flex;
  gap: 6px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 3px;
  padding: 4px 6px;
}
.alert-strip.critical { border-left: 3px solid #ff3d68; }
.alert-strip.warning { border-left: 3px solid #ffb700; }
.alert-strip.info { border-left: 3px solid #00f0ff; }

.alert-dev-row {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
}
.alert-msg-text { font-size: 10px; color: #8fa4bf; margin-top: 1px; }

/* 辅助字体与颜色 */
.font-mono { font-family: 'Roboto Mono', monospace; }
.text-ellipsis { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.txt-blue { color: #2a72ff; }
.txt-green { color: #00ff9d; }
.txt-cyan { color: #00f0ff; }
.txt-purple { color: #a855f7; }
.txt-amber { color: #ffb700; }
.txt-teal { color: #14b8a6; }
.txt-red { color: #ff3d68; }
.txt-orange { color: #ff9900; }

::-webkit-scrollbar { width: 3px; height: 3px; }
::-webkit-scrollbar-thumb { background: rgba(0, 240, 255, 0.25); border-radius: 2px; }
::-webkit-scrollbar-track { background: transparent; }
</style>
