<script setup lang="ts">
defineOptions({ name: 'DeviceList' })
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useDeviceStore } from '../../stores/device'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  Plus, Search, Edit, Delete, View, Odometer, Switch, Refresh, Document, SwitchButton,
  Cpu, VideoPlay, VideoPause, Grid, Memo, Monitor, CopyDocument, Location, Timer,
  Operation, Setting, Check, Close, CaretRight, InfoFilled, WarningFilled, Promotion
} from '@element-plus/icons-vue'
import { api } from '../../api'

const router = useRouter()
const deviceStore = useDeviceStore()

// ===== 视图状态 =====
const viewMode = ref<'grid' | 'table' | 'monitor'>('grid')
const searchQuery = ref('')
const statusFilter = ref<string>('')
const typeFilter = ref<string>('')
const refreshing = ref(false)
const selectedDeviceIds = ref<string[]>([])

// ===== 模拟测试 =====
const simRunning = ref(false)
const simInterval = ref(5)

// ===== 命令抽屉 / 弹窗 =====
const commandDrawerVisible = ref(false)
const targetDevice = ref<any>(null)
const customCommand = ref('')
const customParams = ref('')
const commandLogs = ref<any[]>([])
const commandLogLoading = ref(false)
const sendingCommand = ref(false)

// ===== 设备添加 / 编辑弹窗 =====
const dialogVisible = ref(false)
const isEdit = ref(false)
const currentDeviceId = ref('')
const deviceFormRef = ref<FormInstance>()
const isSubmitting = ref(false)
const credentialDialogVisible = ref(false)
const issuedCredential = ref<{ deviceId: string; apiKey: string } | null>(null)

const deviceForm = ref({
  name: '',
  type: '',
  location: '',
  status: 'offline' as 'online' | 'offline' | 'warning',
  description: '',
  sensors: [] as any[],
  actuators: [] as any[]
})

const deviceRules: FormRules = {
  name: [
    { required: true, message: '请输入设备名称', trigger: 'blur' },
    { min: 2, max: 30, message: '长度在 2 到 30 个字符', trigger: 'blur' }
  ],
  type: [
    { required: true, message: '请选择或输入设备类型', trigger: 'change' }
  ]
}

// ===== 传感器/执行器 子弹窗 =====
const sensorDialogVisible = ref(false)
const editingSensorIndex = ref<number>(-1)
const sensorForm = ref({
  id: '',
  name: '',
  type: '',
  dataType: 'float',
  unit: '',
  min: 0,
  max: 100
})

const actuatorDialogVisible = ref(false)
const editingActuatorIndex = ref<number>(-1)
const actuatorForm = ref({
  id: '',
  name: '',
  type: '',
  commandType: 'toggle',
  defaultValue: 'off',
  parameters: ''
})

// ===== 执行器单机操作记录与加载态 =====
const lastActuatorCmd = ref<Record<string, { cmd: string; time: string; ok: boolean }>>({})
const actuatorOperating = ref<Record<string, boolean>>({})
const statusToggling = ref<Record<string, boolean>>({})

// ===== 常量与预设字典 =====
const presetDeviceTypes = [
  '环境监测终端',
  '智能电表网关',
  '机房动力监控',
  '温控调节终端',
  '工业PLC控制器',
  '水务传感终端',
  '安防监控网关',
  '通用物联网设备'
]

const sensorTypes = [
  { label: '温度 (Temperature)', value: 'temperature', unit: '°C', min: -20, max: 80 },
  { label: '湿度 (Humidity)', value: 'humidity', unit: '%', min: 0, max: 100 },
  { label: '光照 (Light)', value: 'light', unit: 'lux', min: 0, max: 10000 },
  { label: 'PM2.5', value: 'pm25', unit: 'μg/m³', min: 0, max: 500 },
  { label: 'CO₂ 浓度', value: 'co2', unit: 'ppm', min: 300, max: 3000 },
  { label: 'TVOC', value: 'tvoc', unit: 'mg/m³', min: 0, max: 10 },
  { label: '电压 (Voltage)', value: 'voltage', unit: 'V', min: 180, max: 260 },
  { label: '电流 (Current)', value: 'current', unit: 'A', min: 0, max: 30 },
  { label: '功率 (Power)', value: 'power', unit: 'W', min: 0, max: 5000 },
  { label: '烟雾 (Smoke)', value: 'smoke', unit: '%', min: 0, max: 100 },
  { label: '水浸 (Water)', value: 'water', unit: 'level', min: 0, max: 1 },
  { label: '气压 (Pressure)', value: 'pressure', unit: 'hPa', min: 900, max: 1100 },
  { label: '风速 (Wind Speed)', value: 'windspeed', unit: 'm/s', min: 0, max: 50 },
  { label: '噪声 (Noise)', value: 'noise', unit: 'dB', min: 20, max: 120 }
]

const dataTypes = [
  { label: '浮点型 (Float)', value: 'float' },
  { label: '整型 (Integer)', value: 'integer' },
  { label: '布尔型 (Boolean)', value: 'boolean' },
  { label: '字符串 (String)', value: 'string' }
]

const actuatorTypes = [
  { label: '主开关 (Switch)', value: 'switch', cmd: 'toggle' },
  { label: '电机驱动 (Motor)', value: 'motor', cmd: 'setValue' },
  { label: '电磁阀门 (Valve)', value: 'valve', cmd: 'toggle' },
  { label: '继电器 (Relay)', value: 'relay', cmd: 'toggle' },
  { label: '蜂鸣报警器 (Buzzer)', value: 'buzzer', cmd: 'pulse' },
  { label: 'LED指示灯 (LED)', value: 'led', cmd: 'toggle' },
  { label: '散热风扇 (Fan)', value: 'fan', cmd: 'toggle' },
  { label: '增压水泵 (Pump)', value: 'pump', cmd: 'toggle' }
]

const commandTypes = [
  { label: '开关切换 (Toggle)', value: 'toggle' },
  { label: '开启 (Turn On)', value: 'on' },
  { label: '关闭 (Turn Off)', value: 'off' },
  { label: '设置数值 (Set Value)', value: 'setValue' },
  { label: '脉冲触发 (Pulse)', value: 'pulse' }
]

const presetCommands = [
  { label: '心跳检测', cmd: 'ping', desc: '测试设备连通性' },
  { label: '查询状态', cmd: 'getStatus', desc: '获取设备即时运行数据' },
  { label: '同步时钟', cmd: 'syncTime', desc: '校准设备系统时间' },
  { label: '设备重启', cmd: 'reboot', desc: '软重启设备控制模块' },
  { label: '清除告警', cmd: 'clearAlerts', desc: '复位异常报警信号' }
]

// ===== 计算与过滤 =====
const availableTypes = computed(() => {
  const types = new Set<string>()
  deviceStore.devices.forEach(d => {
    if (d.type) types.add(d.type)
  })
  return Array.from(types)
})

const filteredDevices = computed(() => {
  let result = deviceStore.devices
  if (searchQuery.value) {
    const q = searchQuery.value.trim().toLowerCase()
    result = result.filter(d =>
      d.name.toLowerCase().includes(q) ||
      d.id.toLowerCase().includes(q) ||
      (d.type && d.type.toLowerCase().includes(q)) ||
      (d.location && d.location.toLowerCase().includes(q))
    )
  }
  if (statusFilter.value) {
    result = result.filter(d => d.status === statusFilter.value)
  }
  if (typeFilter.value) {
    result = result.filter(d => d.type === typeFilter.value)
  }
  return result
})

const allSensorRows = computed(() => {
  const rows: any[] = []
  for (const device of filteredDevices.value) {
    for (const sensor of (device.sensors || []).filter((s: any) => !s.isActuator)) {
      rows.push({ device, sensor })
    }
  }
  return rows
})

const allActuatorRows = computed(() => {
  const rows: any[] = []
  for (const device of filteredDevices.value) {
    for (const actuator of (device.actuators || [])) {
      rows.push({ device, actuator })
    }
  }
  return rows
})

// ===== 快捷过滤标签 =====
function handleFilterTag(status: string) {
  if (statusFilter.value === status) {
    statusFilter.value = ''
  } else {
    statusFilter.value = status
  }
}

// ===== 辅助函数 =====
function getSensorTypeLabel(type: string) {
  return sensorTypes.find(t => t.value === type)?.label || type
}

function getActuatorTypeLabel(type: string) {
  return actuatorTypes.find(t => t.value === type)?.label || type
}

function getDataTypeLabel(type: string) {
  return dataTypes.find(t => t.value === type)?.label || type
}

function getCommandTypeLabel(type: string) {
  return commandTypes.find(t => t.value === type)?.label || type
}

const sensorTypeColorMap: Record<string, string> = {
  temperature: 'danger',
  humidity: 'primary',
  light: 'warning',
  pm25: 'info',
  co2: 'success',
  tvoc: 'warning',
  voltage: 'danger',
  current: 'warning',
  power: 'success',
  smoke: 'danger',
  water: 'primary',
  pressure: 'info',
  windspeed: 'success',
  noise: 'warning'
}

function getSensorTagType(type: string) {
  return sensorTypeColorMap[type] || 'info'
}

function getActuatorLiveStatus(actuator: any) {
  if (actuator.value !== undefined && actuator.value !== null) {
    return actuator.value === 1 || actuator.value === 'on' ? 'on' : 'off'
  }
  return actuator.defaultValue || 'off'
}

function copyText(text: string, label: string = '内容') {
  if (!text) return
  navigator.clipboard.writeText(text)
  ElMessage.success(`${label}已复制到剪贴板`)
}

function formatRelativeTime(timestamp: string | number) {
  if (!timestamp) return '-'
  const date = new Date(timestamp)
  const now = new Date()
  const diffSec = Math.floor((now.getTime() - date.getTime()) / 1000)
  if (diffSec < 60) return '刚刚'
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)} 分钟前`
  if (diffSec < 86400) return `${Math.floor(diffSec / 3600)} 小时前`
  return date.toLocaleDateString() + ' ' + date.toLocaleTimeString().slice(0, 5)
}

// ===== 设备单体快速操作 =====
async function toggleDeviceStatus(device: any) {
  if (!device || statusToggling.value[device.id]) return
  const newStatus = device.status === 'online' ? 'offline' : 'online'
  const actionLabel = newStatus === 'online' ? '上线' : '下线'
  statusToggling.value[device.id] = true
  try {
    await deviceStore.updateDeviceStatus(device.id, newStatus)
    ElMessage.success(`设备 "${device.name}" 已切换为${actionLabel}`)
  } catch (e: any) {
    ElMessage.error(e.message || '切换状态失败')
    await deviceStore.fetchDevices()
  } finally {
    statusToggling.value[device.id] = false
  }
}

async function sendActuatorCommand(deviceId: string, actuator: any, command: string) {
  const key = `${deviceId}:${actuator.name}`
  actuatorOperating.value[key] = true
  try {
    const result = await deviceStore.sendCommand(deviceId, command, { actuator: actuator.name })
    lastActuatorCmd.value[key] = {
      cmd: command,
      time: new Date().toLocaleTimeString(),
      ok: true
    }
    ElMessage.success(`[${actuator.name}] 指令发送成功: ${command.toUpperCase()}`)
    await deviceStore.silentRefreshDevices()
  } catch (e: any) {
    lastActuatorCmd.value[key] = {
      cmd: command,
      time: new Date().toLocaleTimeString(),
      ok: false
    }
    ElMessage.error(e.message || '指令发送失败')
  } finally {
    actuatorOperating.value[key] = false
  }
}

function viewDetail(device: any) {
  router.push(`/devices/${device.id}`)
}

async function handleDelete(device: any) {
  try {
    await ElMessageBox.confirm(
      `确定要删除设备 "${device.name}" (ID: ${device.id}) 吗？此操作不可恢复。`,
      '确认删除设备',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
        type: 'warning'
      }
    )
    await deviceStore.deleteDevice(device.id)
    ElMessage.success(`设备 "${device.name}" 已成功删除`)
    selectedDeviceIds.value = selectedDeviceIds.value.filter(id => id !== device.id)
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

// ===== 批量操作 =====
function handleTableSelectionChange(selection: any[]) {
  selectedDeviceIds.value = selection.map(d => d.id)
}

function toggleCardSelection(id: string) {
  const index = selectedDeviceIds.value.indexOf(id)
  if (index > -1) {
    selectedDeviceIds.value.splice(index, 1)
  } else {
    selectedDeviceIds.value.push(id)
  }
}

async function handleBatchStatus(status: 'online' | 'offline') {
  if (selectedDeviceIds.value.length === 0) return
  const label = status === 'online' ? '批量上线' : '批量下线'
  try {
    await ElMessageBox.confirm(
      `确定将选中的 ${selectedDeviceIds.value.length} 台设备全部切换为【${status === 'online' ? '在线' : '离线'}】状态吗？`,
      label,
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'info' }
    )
    await deviceStore.batchUpdateStatus(selectedDeviceIds.value, status)
    ElMessage.success(`已成功${label} ${selectedDeviceIds.value.length} 台设备`)
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.message || '批量操作失败')
  }
}

async function handleBatchDelete() {
  if (selectedDeviceIds.value.length === 0) return
  try {
    await ElMessageBox.confirm(
      `确定要彻底删除选中的 ${selectedDeviceIds.value.length} 台设备吗？此操作不可恢复。`,
      '确认批量删除',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deviceStore.batchDeleteDevices(selectedDeviceIds.value)
    ElMessage.success(`已成功删除 ${selectedDeviceIds.value.length} 台设备`)
    selectedDeviceIds.value = []
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.message || '批量删除失败')
  }
}

// ===== 快捷指令抽屉 =====
function openCommandDrawer(device: any) {
  targetDevice.value = device
  customCommand.value = ''
  customParams.value = ''
  commandDrawerVisible.value = true
  loadTargetCommandLogs(device.id)
}

async function loadTargetCommandLogs(deviceId: string) {
  commandLogLoading.value = true
  try {
    commandLogs.value = await api.getCommandLogs(deviceId)
  } catch (e: any) {
    commandLogs.value = []
  } finally {
    commandLogLoading.value = false
  }
}

async function executeCommand(cmd: string, rawParams?: string) {
  if (!targetDevice.value || !cmd.trim()) return
  sendingCommand.value = true
  let parsedParams: any = undefined
  if (rawParams && rawParams.trim()) {
    try {
      parsedParams = JSON.parse(rawParams.trim())
    } catch {
      parsedParams = { raw: rawParams.trim() }
    }
  }
  try {
    const res = await deviceStore.sendCommand(targetDevice.value.id, cmd.trim(), parsedParams)
    ElMessage.success(`指令 [${cmd}] 发送成功: ${res.message || 'OK'}`)
    customCommand.value = ''
    customParams.value = ''
    await loadTargetCommandLogs(targetDevice.value.id)
    await deviceStore.silentRefreshDevices()
  } catch (e: any) {
    ElMessage.error(e.message || '指令执行失败')
  } finally {
    sendingCommand.value = false
  }
}

// ===== 添加与编辑设备弹窗 =====
function showAddDialog() {
  isEdit.value = false
  currentDeviceId.value = ''
  deviceForm.value = {
    name: '',
    type: '环境监测终端',
    location: '',
    status: 'online',
    description: '',
    sensors: [],
    actuators: []
  }
  dialogVisible.value = true
}

function showEditDialog(device: any) {
  isEdit.value = true
  currentDeviceId.value = device.id
  deviceForm.value = {
    name: device.name,
    type: device.type || '通用设备',
    location: device.location || '',
    status: device.status || 'offline',
    description: device.description || '',
    sensors: JSON.parse(JSON.stringify(device.sensors || [])).filter((s: any) => !s.isActuator),
    actuators: JSON.parse(JSON.stringify(device.actuators || []))
  }
  dialogVisible.value = true
}

async function saveDevice() {
  if (!deviceFormRef.value) return
  await deviceFormRef.value.validate(async (valid) => {
    if (!valid) return
    isSubmitting.value = true
    try {
      const payload = {
        name: deviceForm.value.name.trim(),
        type: deviceForm.value.type,
        location: deviceForm.value.location.trim(),
        status: deviceForm.value.status,
        description: deviceForm.value.description.trim(),
        sensors: [
          ...deviceForm.value.sensors,
          ...deviceForm.value.actuators.map((a: any) => ({
            ...a,
            value: a.value ?? 0,
            min: 0,
            max: 1,
            unit: '',
            dataType: 'boolean',
            isActuator: true
          }))
        ]
      }
      if (isEdit.value) {
        await deviceStore.updateDevice(currentDeviceId.value, payload)
        ElMessage.success(`设备 "${payload.name}" 更新成功`)
      } else {
        const created = await deviceStore.createDevice(payload)
        issuedCredential.value = { deviceId: created.id, apiKey: (created as any).apiKey }
        credentialDialogVisible.value = true
        ElMessage.success(`设备 "${payload.name}" 创建成功`)
      }
      dialogVisible.value = false
      await deviceStore.fetchDevices()
    } catch (e: any) {
      ElMessage.error(e.message || '保存设备失败')
    } finally {
      isSubmitting.value = false
    }
  })
}

// ===== 传感器配置子弹窗 =====
function openSensorDialog(sensor?: any, index?: number) {
  if (sensor && index !== undefined) {
    editingSensorIndex.value = index
    sensorForm.value = { ...sensor }
  } else {
    editingSensorIndex.value = -1
    sensorForm.value = {
      id: '',
      name: '',
      type: 'temperature',
      dataType: 'float',
      unit: '°C',
      min: -20,
      max: 80
    }
  }
  sensorDialogVisible.value = true
}

function handleSensorTypeChange(val: string) {
  const found = sensorTypes.find(t => t.value === val)
  if (found) {
    sensorForm.value.unit = found.unit
    sensorForm.value.min = found.min
    sensorForm.value.max = found.max
    if (!sensorForm.value.name || sensorTypes.some(t => t.label.includes(sensorForm.value.name))) {
      sensorForm.value.name = found.label.split(' ')[0]
    }
  }
}

function saveSensor() {
  if (!sensorForm.value.name.trim() || !sensorForm.value.type) {
    ElMessage.warning('请填写传感器名称和类型')
    return
  }
  const sensorData = {
    id: sensorForm.value.id.trim() || `sensor_${Date.now()}_${Math.floor(Math.random() * 1000)}`,
    name: sensorForm.value.name.trim(),
    type: sensorForm.value.type,
    dataType: sensorForm.value.dataType,
    unit: sensorForm.value.unit,
    value: 0,
    min: sensorForm.value.min,
    max: sensorForm.value.max,
    isActuator: false
  }
  if (editingSensorIndex.value >= 0) {
    deviceForm.value.sensors[editingSensorIndex.value] = sensorData
  } else {
    deviceForm.value.sensors.push(sensorData)
  }
  sensorDialogVisible.value = false
}

function removeSensor(index: number) {
  deviceForm.value.sensors.splice(index, 1)
}

function applyQuickSensorPreset(typeVal: string) {
  const found = sensorTypes.find(t => t.value === typeVal)
  if (!found) return
  const id = `s_${found.value}_${Date.now()}`
  deviceForm.value.sensors.push({
    id,
    name: found.label.split(' ')[0],
    type: found.value,
    dataType: 'float',
    unit: found.unit,
    value: 0,
    min: found.min,
    max: found.max,
    isActuator: false
  })
  ElMessage.success(`已添加预设传感器: ${found.label.split(' ')[0]}`)
}

// ===== 执行器配置子弹窗 =====
function openActuatorDialog(actuator?: any, index?: number) {
  if (actuator && index !== undefined) {
    editingActuatorIndex.value = index
    actuatorForm.value = { ...actuator }
  } else {
    editingActuatorIndex.value = -1
    actuatorForm.value = {
      id: '',
      name: '',
      type: 'switch',
      commandType: 'toggle',
      defaultValue: 'off',
      parameters: ''
    }
  }
  actuatorDialogVisible.value = true
}

function handleActuatorTypeChange(val: string) {
  const found = actuatorTypes.find(t => t.value === val)
  if (found) {
    actuatorForm.value.commandType = found.cmd
    if (!actuatorForm.value.name) {
      actuatorForm.value.name = found.label.split(' ')[0]
    }
  }
}

function saveActuator() {
  if (!actuatorForm.value.name.trim() || !actuatorForm.value.type) {
    ElMessage.warning('请填写执行器名称和类型')
    return
  }
  const actuatorData = {
    id: actuatorForm.value.id.trim() || `act_${Date.now()}_${Math.floor(Math.random() * 1000)}`,
    name: actuatorForm.value.name.trim(),
    type: actuatorForm.value.type,
    commandType: actuatorForm.value.commandType,
    defaultValue: actuatorForm.value.defaultValue,
    parameters: actuatorForm.value.parameters,
    value: actuatorForm.value.defaultValue === 'on' ? 1 : 0,
    isActuator: true
  }
  if (editingActuatorIndex.value >= 0) {
    deviceForm.value.actuators[editingActuatorIndex.value] = actuatorData
  } else {
    deviceForm.value.actuators.push(actuatorData)
  }
  actuatorDialogVisible.value = false
}

function removeActuator(index: number) {
  deviceForm.value.actuators.splice(index, 1)
}

function applyQuickActuatorPreset(typeVal: string) {
  const found = actuatorTypes.find(t => t.value === typeVal)
  if (!found) return
  const id = `a_${found.value}_${Date.now()}`
  deviceForm.value.actuators.push({
    id,
    name: found.label.split(' ')[0],
    type: found.value,
    commandType: found.cmd,
    defaultValue: 'off',
    parameters: '',
    value: 0,
    isActuator: true
  })
  ElMessage.success(`已添加预设执行器: ${found.label.split(' ')[0]}`)
}

// ===== 模拟测试控制 =====
async function toggleSimulation() {
  try {
    if (simRunning.value) {
      await api.stopDataUpload()
      await api.stopCommandDelivery()
      simRunning.value = false
      ElMessage.success('模拟测试已停止')
    } else {
      await api.startDataUpload(simInterval.value)
      await api.startCommandDelivery(simInterval.value)
      simRunning.value = true
      ElMessage.success(`数据模拟运行中，每 ${simInterval.value}s 自动上报`)
    }
  } catch (e: any) {
    ElMessage.error(e.message || '模拟控制失败')
  }
}

async function checkSimStatus() {
  try {
    const status = await api.getSimulationStatus()
    simRunning.value = status.dataUploadRunning || status.commandDeliveryRunning
  } catch { /* ignore */ }
}

async function refreshAllDevices() {
  refreshing.value = true
  try {
    await deviceStore.fetchDevices()
    ElMessage.success('设备数据已全部刷新')
  } catch (e: any) {
    ElMessage.error(e.message || '刷新失败')
  } finally {
    refreshing.value = false
  }
}

let listPollTimer: ReturnType<typeof setInterval> | null = null

onMounted(async () => {
  await deviceStore.fetchDevices()
  checkSimStatus()
  listPollTimer = setInterval(() => deviceStore.silentRefreshDevices(), 3000)
})

onUnmounted(() => {
  if (listPollTimer) clearInterval(listPollTimer)
})
</script>

<template>
  <div class="device-management-page">
    <!-- 顶部标题与快速统计看板 -->
    <div class="page-top-header">
      <div class="header-intro">
        <h2>设备管理</h2>
        <span class="header-sub">集中管控物联网终端节点、传感器遥测数据与执行器指令分发</span>
      </div>
      <div class="header-actions">
        <el-button-group class="sim-btn-group">
          <el-button
            :type="simRunning ? 'danger' : 'success'"
            :icon="simRunning ? VideoPause : VideoPlay"
            @click="toggleSimulation"
            size="default"
          >
            {{ simRunning ? '停止模拟' : '启动模拟' }}
          </el-button>
          <el-input-number
            v-model="simInterval"
            :min="1"
            :max="60"
            size="default"
            style="width: 110px"
            :disabled="simRunning"
            controls-position="right"
          />
        </el-button-group>
        <el-button :icon="Refresh" :loading="refreshing" @click="refreshAllDevices" size="default">
          刷新
        </el-button>
        <el-button type="primary" :icon="Plus" @click="showAddDialog" size="default">
          添加设备
        </el-button>
      </div>
    </div>

    <!-- 状态指标看板 (点击直接过滤) -->
    <div class="metrics-cards-grid">
      <div
        class="metric-card"
        :class="{ active: statusFilter === '' }"
        @click="statusFilter = ''"
      >
        <div class="metric-icon-wrap all-icon">
          <el-icon :size="24"><Cpu /></el-icon>
        </div>
        <div class="metric-info">
          <div class="metric-label">全部设备</div>
          <div class="metric-value">{{ deviceStore.totalCount }}</div>
        </div>
        <div class="metric-indicator all" />
      </div>

      <div
        class="metric-card"
        :class="{ active: statusFilter === 'online' }"
        @click="handleFilterTag('online')"
      >
        <div class="metric-icon-wrap online-icon">
          <el-icon :size="24"><Check /></el-icon>
        </div>
        <div class="metric-info">
          <div class="metric-label">在线运行</div>
          <div class="metric-value text-online">{{ deviceStore.onlineCount }}</div>
        </div>
        <div class="metric-indicator online" />
      </div>

      <div
        class="metric-card"
        :class="{ active: statusFilter === 'offline' }"
        @click="handleFilterTag('offline')"
      >
        <div class="metric-icon-wrap offline-icon">
          <el-icon :size="24"><Close /></el-icon>
        </div>
        <div class="metric-info">
          <div class="metric-label">离线断连</div>
          <div class="metric-value text-offline">{{ deviceStore.offlineCount }}</div>
        </div>
        <div class="metric-indicator offline" />
      </div>

      <div
        class="metric-card"
        :class="{ active: statusFilter === 'warning' }"
        @click="handleFilterTag('warning')"
      >
        <div class="metric-icon-wrap warning-icon">
          <el-icon :size="24"><WarningFilled /></el-icon>
        </div>
        <div class="metric-info">
          <div class="metric-label">告警异常</div>
          <div class="metric-value text-warning">{{ deviceStore.warningCount }}</div>
        </div>
        <div class="metric-indicator warning" />
      </div>
    </div>

    <!-- 工具栏与筛选区域 -->
    <div class="action-toolbar">
      <div class="toolbar-left">
        <el-input
          v-model="searchQuery"
          placeholder="搜索设备名称、ID、安装位置、类型..."
          :prefix-icon="Search"
          clearable
          class="search-input"
        />
        <el-select v-model="typeFilter" placeholder="设备类型" clearable class="filter-select">
          <el-option v-for="t in availableTypes" :key="t" :label="t" :value="t" />
        </el-select>
        <el-select v-model="statusFilter" placeholder="状态筛选" clearable class="filter-select-sm">
          <el-option label="在线" value="online" />
          <el-option label="离线" value="offline" />
          <el-option label="告警" value="warning" />
        </el-select>

        <span class="filter-stat-text">
          共找到 <b>{{ filteredDevices.length }}</b> 台设备
        </span>
      </div>

      <div class="toolbar-right">
        <!-- 批量操作胶囊 (当有勾选时浮现) -->
        <transition name="el-fade-in-linear">
          <div v-if="selectedDeviceIds.length > 0" class="batch-bar">
            <span class="batch-count">已选 {{ selectedDeviceIds.length }} 台</span>
            <el-button size="small" type="success" plain @click="handleBatchStatus('online')">批量上线</el-button>
            <el-button size="small" type="info" plain @click="handleBatchStatus('offline')">批量下线</el-button>
            <el-button size="small" type="danger" plain @click="handleBatchDelete">批量删除</el-button>
            <el-button size="small" text @click="selectedDeviceIds = []">取消选择</el-button>
          </div>
        </transition>

        <!-- 视图切换模式 -->
        <el-radio-group v-model="viewMode" size="default" class="view-mode-toggle">
          <el-radio-button value="grid">
            <el-icon><Grid /></el-icon> 卡片
          </el-radio-button>
          <el-radio-button value="table">
            <el-icon><Memo /></el-icon> 表格
          </el-radio-button>
          <el-radio-button value="monitor">
            <el-icon><Monitor /></el-icon> 监控看板
          </el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <!-- ==================== 模式 1: 卡片网格视图 ==================== -->
    <div v-if="viewMode === 'grid'" class="grid-view-container">
      <div v-if="filteredDevices.length === 0" class="empty-state-box">
        <el-icon :size="48" color="var(--text-muted)"><Cpu /></el-icon>
        <p class="empty-title">未找到匹配的设备</p>
        <p class="empty-desc">尝试调整过滤条件，或点击右上角“添加设备”按钮创建新设备</p>
        <el-button type="primary" :icon="Plus" @click="showAddDialog">添加设备</el-button>
      </div>

      <div v-else class="device-card-grid">
        <div
          v-for="device in filteredDevices"
          :key="device.id"
          class="device-card"
          :class="[
            'status-' + device.status,
            { selected: selectedDeviceIds.includes(device.id) }
          ]"
        >
          <!-- 卡片头部 -->
          <div class="card-header">
            <div class="card-header-left">
              <el-checkbox
                :model-value="selectedDeviceIds.includes(device.id)"
                @change="toggleCardSelection(device.id)"
                class="card-select-checkbox"
              />
              <div class="status-indicator-dot" :class="device.status" />
              <div class="card-title-group">
                <span class="card-title" :title="device.name">{{ device.name }}</span>
                <span class="card-type-tag">{{ device.type || '通用设备' }}</span>
              </div>
            </div>

            <div class="card-header-right">
              <el-tooltip :content="device.status === 'online' ? '点击切换为离线' : '点击切换为在线'" placement="top">
                <el-switch
                  :model-value="device.status === 'online'"
                  :loading="statusToggling[device.id]"
                  inline-prompt
                  active-text="ON"
                  inactive-text="OFF"
                  @change="toggleDeviceStatus(device)"
                  class="quick-switch"
                />
              </el-tooltip>
            </div>
          </div>

          <!-- 卡片核心属性信息 -->
          <div class="card-meta-row">
            <div class="meta-item id-meta" @click="copyText(device.id, '设备ID')">
              <span class="meta-label">ID:</span>
              <code class="meta-id-code">{{ device.id }}</code>
              <el-icon :size="12" class="copy-icon"><CopyDocument /></el-icon>
            </div>
            <div class="meta-item" v-if="device.location">
              <el-icon :size="13" color="var(--text-muted)"><Location /></el-icon>
              <span class="meta-text">{{ device.location }}</span>
            </div>
            <div class="meta-item">
              <el-icon :size="13" color="var(--text-muted)"><Timer /></el-icon>
              <span class="meta-text">{{ formatRelativeTime(device.lastActive) }}</span>
            </div>
          </div>

          <!-- 传感器数据概览 (若有) -->
          <div class="card-section" v-if="device.sensors && device.sensors.length > 0">
            <div class="section-badge-bar">
              <span class="badge-title">
                <el-icon :size="13" color="var(--sensor-color)"><Odometer /></el-icon>
                传感器实时读数 ({{ device.sensors.length }})
              </span>
            </div>
            <div class="mini-sensors-wrap">
              <div
                v-for="s in device.sensors"
                :key="s.id"
                class="mini-sensor-chip"
                :class="{ 'chip-warn': s.value > s.max * 0.9 }"
              >
                <span class="chip-name">{{ s.name }}</span>
                <span class="chip-val">{{ typeof s.value === 'number' ? s.value.toFixed(1) : s.value }}</span>
                <span class="chip-unit">{{ s.unit }}</span>
              </div>
            </div>
          </div>

          <!-- 执行器控制概览 (若有) -->
          <div class="card-section" v-if="device.actuators && device.actuators.length > 0">
            <div class="section-badge-bar">
              <span class="badge-title">
                <el-icon :size="13" color="var(--actuator-color)"><Switch /></el-icon>
                执行器控制 ({{ device.actuators.length }})
              </span>
            </div>
            <div class="mini-actuators-wrap">
              <div
                v-for="a in device.actuators"
                :key="a.id"
                class="mini-actuator-item"
              >
                <span class="act-name" :title="a.name">{{ a.name }}</span>
                <el-tag
                  size="small"
                  :type="getActuatorLiveStatus(a) === 'on' ? 'success' : 'info'"
                  effect="dark"
                  class="act-state-tag"
                >
                  {{ getActuatorLiveStatus(a) === 'on' ? 'ON' : 'OFF' }}
                </el-tag>
                <div class="act-actions">
                  <el-button
                    size="small"
                    type="success"
                    :loading="actuatorOperating[`${device.id}:${a.name}`]"
                    @click="sendActuatorCommand(device.id, a, 'on')"
                  >
                    开
                  </el-button>
                  <el-button
                    size="small"
                    type="danger"
                    :loading="actuatorOperating[`${device.id}:${a.name}`]"
                    @click="sendActuatorCommand(device.id, a, 'off')"
                  >
                    关
                  </el-button>
                </div>
              </div>
            </div>
          </div>

          <!-- 底部主操作工具栏 -->
          <div class="card-footer-actions">
            <el-button
              size="small"
              type="primary"
              plain
              :icon="Promotion"
              @click="openCommandDrawer(device)"
            >
              控制命令
            </el-button>
            <el-button
              size="small"
              :icon="Edit"
              @click="showEditDialog(device)"
            >
              编辑
            </el-button>
            <el-button
              size="small"
              :icon="View"
              @click="viewDetail(device)"
            >
              详情
            </el-button>
            <el-dropdown trigger="click" @command="(cmd: string) => {
              if (cmd === 'delete') handleDelete(device)
              if (cmd === 'logs') openCommandDrawer(device)
            }">
              <el-button size="small" :icon="Operation" class="more-btn" />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="logs">
                    <el-icon><Document /></el-icon> 指令日志
                  </el-dropdown-item>
                  <el-dropdown-item command="delete" divided style="color: var(--danger)">
                    <el-icon><Delete /></el-icon> 删除设备
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </div>
    </div>

    <!-- ==================== 模式 2: 表格列表视图 ==================== -->
    <div v-else-if="viewMode === 'table'" class="table-view-container">
      <el-table
        :data="filteredDevices"
        style="width: 100%"
        v-loading="deviceStore.loading"
        row-key="id"
        class="main-device-table"
        @selection-change="handleTableSelectionChange"
      >
        <el-table-column type="selection" width="45" align="center" />
        
        <!-- 展开行查看传感器与执行器 -->
        <el-table-column type="expand" width="45">
          <template #default="{ row }">
            <div class="table-expand-wrapper">
              <el-row :gutter="20">
                <el-col :span="12">
                  <div class="expand-panel">
                    <div class="expand-header">
                      <el-icon color="var(--sensor-color)"><Odometer /></el-icon>
                      <span>传感器实时数据 ({{ (row.sensors || []).length }})</span>
                    </div>
                    <div v-if="(row.sensors || []).length > 0" class="expand-grid">
                      <div v-for="s in row.sensors" :key="s.id" class="expand-chip">
                        <span class="exp-name">{{ s.name }}</span>
                        <span class="exp-val">{{ typeof s.value === 'number' ? s.value.toFixed(2) : s.value }} {{ s.unit }}</span>
                        <el-tag size="small" :type="s.value > s.max * 0.9 ? 'danger' : 'success'" effect="plain">
                          {{ s.value > s.max * 0.9 ? '超限' : '正常' }}
                        </el-tag>
                      </div>
                    </div>
                    <div v-else class="expand-empty">暂无传感器</div>
                  </div>
                </el-col>

                <el-col :span="12">
                  <div class="expand-panel">
                    <div class="expand-header">
                      <el-icon color="var(--actuator-color)"><Switch /></el-icon>
                      <span>执行器控制 ({{ (row.actuators || []).length }})</span>
                    </div>
                    <div v-if="(row.actuators || []).length > 0" class="expand-grid">
                      <div v-for="a in row.actuators" :key="a.id" class="expand-chip act-chip">
                        <span class="exp-name">{{ a.name }}</span>
                        <el-tag size="small" :type="getActuatorLiveStatus(a) === 'on' ? 'success' : 'info'" effect="dark">
                          {{ getActuatorLiveStatus(a) === 'on' ? 'ON' : 'OFF' }}
                        </el-tag>
                        <div class="exp-btns">
                          <el-button
                            size="small"
                            type="success"
                            :loading="actuatorOperating[`${row.id}:${a.name}`]"
                            @click="sendActuatorCommand(row.id, a, 'on')"
                          >
                            开
                          </el-button>
                          <el-button
                            size="small"
                            type="danger"
                            :loading="actuatorOperating[`${row.id}:${a.name}`]"
                            @click="sendActuatorCommand(row.id, a, 'off')"
                          >
                            关
                          </el-button>
                        </div>
                      </div>
                    </div>
                    <div v-else class="expand-empty">暂无执行器</div>
                  </div>
                </el-col>
              </el-row>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="设备信息" min-width="200">
          <template #default="{ row }">
            <div class="tbl-device-info">
              <div class="tbl-name-row">
                <span class="status-indicator-dot" :class="row.status" />
                <span class="tbl-device-name">{{ row.name }}</span>
              </div>
              <div class="tbl-id-row" @click="copyText(row.id, '设备ID')">
                <code>{{ row.id }}</code>
                <el-icon :size="12"><CopyDocument /></el-icon>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="设备类型" min-width="130">
          <template #default="{ row }">
            <el-tag size="small" round effect="plain">{{ row.type || '通用设备' }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="安装位置" min-width="140">
          <template #default="{ row }">
            <span class="tbl-location">
              <el-icon v-if="row.location" :size="12" style="margin-right:2px"><Location /></el-icon>
              {{ row.location || '-' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="运行状态" width="120" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 'online'"
              :loading="statusToggling[row.id]"
              inline-prompt
              active-text="在线"
              inactive-text="离线"
              @change="toggleDeviceStatus(row)"
            />
          </template>
        </el-table-column>

        <el-table-column label="传感器 / 执行器" width="150" align="center">
          <template #default="{ row }">
            <div class="tbl-count-badges">
              <el-tooltip content="传感器数量" placement="top">
                <span class="count-pill sensor-pill">
                  <el-icon :size="12"><Odometer /></el-icon> {{ (row.sensors || []).length }}
                </span>
              </el-tooltip>
              <el-tooltip content="执行器数量" placement="top">
                <span class="count-pill act-pill">
                  <el-icon :size="12"><Switch /></el-icon> {{ (row.actuators || []).length }}
                </span>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="最后活跃" width="160">
          <template #default="{ row }">
            <span class="tbl-time">{{ formatRelativeTime(row.lastActive) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="260" fixed="right" align="center">
          <template #default="{ row }">
            <div class="tbl-actions">
              <el-button size="small" type="primary" link :icon="Promotion" @click="openCommandDrawer(row)">
                命令
              </el-button>
              <el-button size="small" type="primary" link :icon="Edit" @click="showEditDialog(row)">
                编辑
              </el-button>
              <el-button size="small" link :icon="View" @click="viewDetail(row)">
                详情
              </el-button>
              <el-button size="small" type="danger" link :icon="Delete" @click="handleDelete(row)">
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- ==================== 模式 3: 集中监控看板 ==================== -->
    <div v-else-if="viewMode === 'monitor'" class="monitor-view-container">
      <el-row :gutter="16" class="two-panel-row">
        <!-- 传感器集中面板 -->
        <el-col :span="12">
          <div class="data-panel sensor-data-panel">
            <div class="panel-top-bar">
              <div class="panel-top-left">
                <div class="panel-icon-box sensor-icon-box">
                  <el-icon :size="18"><Odometer /></el-icon>
                </div>
                <div>
                  <div class="panel-top-title">传感器数据总览</div>
                  <div class="panel-top-sub">All Sensor Telemetry</div>
                </div>
                <el-tag size="small" type="info" round effect="plain">{{ allSensorRows.length }}</el-tag>
              </div>
            </div>

            <div class="panel-table-wrap" v-loading="deviceStore.loading">
              <el-table :data="allSensorRows" style="width: 100%" size="small" class="data-table" v-if="allSensorRows.length > 0">
                <el-table-column label="所属设备" min-width="130">
                  <template #default="{ row }">
                    <div class="cell-device">
                      <span class="cell-device-status" :class="row.device.status" />
                      <span class="cell-device-name">{{ row.device.name }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="传感器" min-width="100">
                  <template #default="{ row }">
                    <span class="cell-mono">{{ row.sensor.name }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="类型" min-width="100">
                  <template #default="{ row }">
                    <el-tag size="small" :type="getSensorTagType(row.sensor.type)" round effect="plain">
                      {{ getSensorTypeLabel(row.sensor.type) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="当前数值" min-width="110" align="right">
                  <template #default="{ row }">
                    <span :class="['cell-value', { 'cell-value-warn': row.sensor.value > row.sensor.max * 0.9 }]">
                      {{ typeof row.sensor.value === 'number' ? row.sensor.value.toFixed(2) : row.sensor.value }}
                    </span>
                    <span class="cell-unit">{{ row.sensor.unit }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="阈值状态" width="90" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.sensor.value > row.sensor.max * 0.9 ? 'danger' : 'success'" size="small" round effect="dark">
                      {{ row.sensor.value > row.sensor.max * 0.9 ? '超限' : '正常' }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
              <div v-else class="panel-empty-table">
                <el-icon :size="32" color="var(--text-muted)"><Odometer /></el-icon>
                <p>暂无传感器数据</p>
              </div>
            </div>
          </div>
        </el-col>

        <!-- 执行器集中面板 -->
        <el-col :span="12">
          <div class="data-panel actuator-data-panel">
            <div class="panel-top-bar">
              <div class="panel-top-left">
                <div class="panel-icon-box actuator-icon-box">
                  <el-icon :size="18"><Switch /></el-icon>
                </div>
                <div>
                  <div class="panel-top-title">执行器控制中心</div>
                  <div class="panel-top-sub">All Actuator Control</div>
                </div>
                <el-tag size="small" type="warning" round effect="plain">{{ allActuatorRows.length }}</el-tag>
              </div>
            </div>

            <div class="panel-table-wrap" v-loading="deviceStore.loading">
              <el-table :data="allActuatorRows" style="width: 100%" size="small" class="data-table" v-if="allActuatorRows.length > 0">
                <el-table-column label="所属设备" min-width="130">
                  <template #default="{ row }">
                    <div class="cell-device">
                      <span class="cell-device-status" :class="row.device.status" />
                      <span class="cell-device-name">{{ row.device.name }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="执行器" min-width="100">
                  <template #default="{ row }">
                    <span class="cell-mono">{{ row.actuator.name }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="当前状态" min-width="100" align="center">
                  <template #default="{ row }">
                    <el-tag
                      :type="getActuatorLiveStatus(row.actuator) === 'on' ? 'success' : 'info'"
                      size="small" effect="dark"
                    >
                      {{ getActuatorLiveStatus(row.actuator) === 'on' ? 'ON 已开启' : 'OFF 已关闭' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="快速控制" min-width="130" align="center">
                  <template #default="{ row }">
                    <div class="actuator-btn-group">
                      <el-button
                        size="small"
                        type="success"
                        :loading="actuatorOperating[`${row.device.id}:${row.actuator.name}`]"
                        @click="sendActuatorCommand(row.device.id, row.actuator, 'on')"
                      >
                        ON
                      </el-button>
                      <el-button
                        size="small"
                        type="danger"
                        :loading="actuatorOperating[`${row.device.id}:${row.actuator.name}`]"
                        @click="sendActuatorCommand(row.device.id, row.actuator, 'off')"
                      >
                        OFF
                      </el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
              <div v-else class="panel-empty-table">
                <el-icon :size="32" color="var(--text-muted)"><Switch /></el-icon>
                <p>暂无执行器数据</p>
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- ==================== 抽屉: 设备指令控制与日志 ==================== -->
    <el-drawer
      v-model="commandDrawerVisible"
      :title="`设备指令控制 — ${targetDevice?.name || ''}`"
      size="540px"
      destroy-on-close
    >
      <div class="drawer-content" v-if="targetDevice">
        <!-- 设备信息简述条 -->
        <div class="drawer-device-card">
          <div class="ddc-row">
            <span class="ddc-label">设备名称:</span>
            <span class="ddc-val bold">{{ targetDevice.name }}</span>
            <el-tag size="small" :type="targetDevice.status === 'online' ? 'success' : 'info'" effect="dark">
              {{ targetDevice.status === 'online' ? '在线' : '离线' }}
            </el-tag>
          </div>
          <div class="ddc-row">
            <span class="ddc-label">设备 ID:</span>
            <code class="ddc-code">{{ targetDevice.id }}</code>
            <el-button text type="primary" size="small" @click="copyText(targetDevice.id, '设备ID')">复制</el-button>
          </div>
        </div>

        <!-- 常用预设快捷指令 -->
        <div class="drawer-section">
          <div class="drawer-section-title">
            <el-icon><Promotion /></el-icon> 常用快捷指令
          </div>
          <div class="preset-commands-grid">
            <el-button
              v-for="p in presetCommands"
              :key="p.cmd"
              class="preset-cmd-btn"
              :loading="sendingCommand"
              @click="executeCommand(p.cmd)"
            >
              <div class="cmd-btn-inner">
                <span class="cmd-label">{{ p.label }}</span>
                <code class="cmd-code">{{ p.cmd }}</code>
              </div>
            </el-button>
          </div>
        </div>

        <!-- 自定义指令输入 -->
        <div class="drawer-section">
          <div class="drawer-section-title">
            <el-icon><Setting /></el-icon> 自定义指令下发
          </div>
          <el-form label-position="top">
            <el-form-item label="指令名称 (Command)">
              <el-input
                v-model="customCommand"
                placeholder="例如: setInterval, setThreshold, reboot"
                clearable
              />
            </el-form-item>
            <el-form-item label="指令参数 (JSON / 字符串)">
              <el-input
                v-model="customParams"
                type="textarea"
                rows="2"
                placeholder='可选，如: {"interval": 3000} 或 100'
              />
            </el-form-item>
            <el-button
              type="primary"
              :icon="Promotion"
              style="width: 100%"
              :disabled="!customCommand.trim()"
              :loading="sendingCommand"
              @click="executeCommand(customCommand, customParams)"
            >
              发送指令到设备
            </el-button>
          </el-form>
        </div>

        <!-- 指令执行日志 -->
        <div class="drawer-section">
          <div class="drawer-section-title" style="display:flex;justify-content:space-between;align-items:center;">
            <span><el-icon><Document /></el-icon> 历史指令响应记录</span>
            <el-button size="small" text :icon="Refresh" @click="loadTargetCommandLogs(targetDevice.id)">刷新日志</el-button>
          </div>
          <div class="logs-container" v-loading="commandLogLoading">
            <div v-if="commandLogs.length === 0" class="empty-logs">
              暂无下发记录
            </div>
            <div v-for="(log, idx) in commandLogs" :key="idx" class="log-item">
              <div class="log-top">
                <span class="log-cmd"><code>{{ log.command }}</code></span>
                <el-tag size="small" :type="log.status === 'EXECUTED' ? 'success' : 'info'">{{ log.status }}</el-tag>
                <span class="log-time">{{ log.sentAt ? new Date(log.sentAt).toLocaleTimeString() : '' }}</span>
              </div>
              <div class="log-resp" v-if="log.response">
                <span class="resp-label">响应:</span>
                <span class="resp-text">{{ log.response }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- ==================== 弹窗: 设备新增 / 编辑 ==================== -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑设备配置' : '新增物联网设备'"
      width="860px"
      destroy-on-close
      class="device-modal"
    >
      <el-form
        ref="deviceFormRef"
        :model="deviceForm"
        :rules="deviceRules"
        label-width="95px"
        class="device-form-wrap"
      >
        <!-- 基础信息面板 -->
        <div class="form-section-card">
          <div class="form-section-title">
            <el-icon color="var(--accent)"><Cpu /></el-icon>
            <span>设备基本信息</span>
          </div>

          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="设备名称" prop="name">
                <el-input v-model="deviceForm.name" placeholder="如: 1号温湿度采集终端" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="设备类型" prop="type">
                <el-select
                  v-model="deviceForm.type"
                  placeholder="选择或输入类型"
                  filterable
                  allow-create
                  default-first-option
                  style="width: 100%"
                >
                  <el-option v-for="t in presetDeviceTypes" :key="t" :label="t" :value="t" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="安装位置">
                <el-input v-model="deviceForm.location" placeholder="如: A座机房-机柜03" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="初始状态">
                <el-radio-group v-model="deviceForm.status">
                  <el-radio-button value="online">在线</el-radio-button>
                  <el-radio-button value="offline">离线</el-radio-button>
                  <el-radio-button value="warning">告警</el-radio-button>
                </el-radio-group>
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="描述备注">
            <el-input v-model="deviceForm.description" type="textarea" rows="2" placeholder="填写设备用途或备注信息（可选）" />
          </el-form-item>
        </div>

        <!-- 传感器配置面板 -->
        <div class="form-section-card">
          <div class="form-section-header">
            <div class="form-section-title">
              <el-icon color="var(--sensor-color)"><Odometer /></el-icon>
              <span>数据采集 — 传感器配置</span>
              <el-tag size="small" type="info" round>{{ deviceForm.sensors.length }} 个</el-tag>
            </div>
            <div class="section-actions">
              <el-dropdown trigger="click" @command="applyQuickSensorPreset">
                <el-button size="small" plain>
                  快速套用预设 <el-icon><Plus /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-for="st in sensorTypes" :key="st.value" :command="st.value">
                      {{ st.label }} ({{ st.unit }})
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-button size="small" type="primary" plain :icon="Plus" @click="openSensorDialog()">
                自定义添加
              </el-button>
            </div>
          </div>

          <div class="config-items-container">
            <div v-if="deviceForm.sensors.length === 0" class="config-empty">
              <el-icon :size="24" color="var(--text-muted)"><Odometer /></el-icon>
              <span>尚未添加传感器，可点击上方“快速套用预设”或“自定义添加”</span>
            </div>
            <div v-else class="config-chip-grid">
              <div v-for="(s, idx) in deviceForm.sensors" :key="s.id || idx" class="config-item-chip sensor-theme">
                <div class="chip-main">
                  <div class="chip-title">{{ s.name }}</div>
                  <div class="chip-meta">
                    <el-tag size="small" :type="getSensorTagType(s.type)" effect="plain">{{ getSensorTypeLabel(s.type) }}</el-tag>
                    <span class="chip-range">{{ s.min }} ~ {{ s.max }} {{ s.unit }}</span>
                  </div>
                </div>
                <div class="chip-ops">
                  <el-button link type="primary" :icon="Edit" @click="openSensorDialog(s, idx)" />
                  <el-button link type="danger" :icon="Delete" @click="removeSensor(idx)" />
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 执行器配置面板 -->
        <div class="form-section-card">
          <div class="form-section-header">
            <div class="form-section-title">
              <el-icon color="var(--actuator-color)"><Switch /></el-icon>
              <span>控制输出 — 执行器配置</span>
              <el-tag size="small" type="warning" round>{{ deviceForm.actuators.length }} 个</el-tag>
            </div>
            <div class="section-actions">
              <el-dropdown trigger="click" @command="applyQuickActuatorPreset">
                <el-button size="small" plain>
                  快速套用预设 <el-icon><Plus /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-for="at in actuatorTypes" :key="at.value" :command="at.value">
                      {{ at.label }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-button size="small" type="warning" plain :icon="Plus" @click="openActuatorDialog()">
                自定义添加
              </el-button>
            </div>
          </div>

          <div class="config-items-container">
            <div v-if="deviceForm.actuators.length === 0" class="config-empty">
              <el-icon :size="24" color="var(--text-muted)"><Switch /></el-icon>
              <span>尚未添加执行器，可点击上方“快速套用预设”或“自定义添加”</span>
            </div>
            <div v-else class="config-chip-grid">
              <div v-for="(a, idx) in deviceForm.actuators" :key="a.id || idx" class="config-item-chip actuator-theme">
                <div class="chip-main">
                  <div class="chip-title">{{ a.name }}</div>
                  <div class="chip-meta">
                    <el-tag size="small" type="warning" effect="plain">{{ getActuatorTypeLabel(a.type) }}</el-tag>
                    <span class="chip-range">默认: {{ a.defaultValue }} · 指令: {{ a.commandType }}</span>
                  </div>
                </div>
                <div class="chip-ops">
                  <el-button link type="primary" :icon="Edit" @click="openActuatorDialog(a, idx)" />
                  <el-button link type="danger" :icon="Delete" @click="removeActuator(idx)" />
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="isSubmitting" @click="saveDevice">保存设备配置</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="credentialDialogVisible" title="TCP 连接凭据" width="520px" :close-on-click-modal="false">
      <el-alert title="完整 API Key 仅在创建时显示，请立即保存。" type="warning" :closable="false" show-icon />
      <el-descriptions v-if="issuedCredential" :column="1" border style="margin-top: 16px">
        <el-descriptions-item label="设备 ID">
          <code>{{ issuedCredential.deviceId }}</code>
          <el-button text type="primary" @click="copyText(issuedCredential.deviceId, '设备 ID')">复制</el-button>
        </el-descriptions-item>
        <el-descriptions-item label="API Key">
          <code>{{ issuedCredential.apiKey }}</code>
          <el-button text type="primary" @click="copyText(issuedCredential.apiKey, 'API Key')">复制</el-button>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="credentialDialogVisible = false">我已保存</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 子弹窗: 传感器编辑 ==================== -->
    <el-dialog
      v-model="sensorDialogVisible"
      :title="editingSensorIndex >= 0 ? '编辑传感器' : '添加传感器'"
      width="480px"
      destroy-on-close
    >
      <el-form :model="sensorForm" label-width="90px">
        <el-form-item label="传感器名称" required>
          <el-input v-model="sensorForm.name" placeholder="如: 环境温度传感器" />
        </el-form-item>
        <el-form-item label="传感器类型" required>
          <el-select v-model="sensorForm.type" placeholder="选择类型" style="width: 100%" @change="handleSensorTypeChange">
            <el-option v-for="t in sensorTypes" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="单位">
              <el-input v-model="sensorForm.unit" placeholder="°C、%、lux" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数据类型">
              <el-select v-model="sensorForm.dataType" style="width: 100%">
                <el-option v-for="t in dataTypes" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="最小值">
              <el-input-number v-model="sensorForm.min" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大值">
              <el-input-number v-model="sensorForm.max" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="传感器 ID">
          <el-input v-model="sensorForm.id" placeholder="留空自动生成" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sensorDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSensor">确定</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 子弹窗: 执行器编辑 ==================== -->
    <el-dialog
      v-model="actuatorDialogVisible"
      :title="editingActuatorIndex >= 0 ? '编辑执行器' : '添加执行器'"
      width="480px"
      destroy-on-close
    >
      <el-form :model="actuatorForm" label-width="90px">
        <el-form-item label="执行器名称" required>
          <el-input v-model="actuatorForm.name" placeholder="如: 主排风扇控制" />
        </el-form-item>
        <el-form-item label="执行器类型" required>
          <el-select v-model="actuatorForm.type" placeholder="选择类型" style="width: 100%" @change="handleActuatorTypeChange">
            <el-option v-for="t in actuatorTypes" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="指令类型">
              <el-select v-model="actuatorForm.commandType" style="width: 100%">
                <el-option v-for="t in commandTypes" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="默认状态">
              <el-radio-group v-model="actuatorForm.defaultValue" size="small">
                <el-radio-button value="on">开</el-radio-button>
                <el-radio-button value="off">关</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="执行器 ID">
          <el-input v-model="actuatorForm.id" placeholder="留空自动生成" />
        </el-form-item>
        <el-form-item label="额外参数">
          <el-input v-model="actuatorForm.parameters" type="textarea" rows="2" placeholder='可选 JSON，如 {"timeout": 3000}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actuatorDialogVisible = false">取消</el-button>
        <el-button type="warning" @click="saveActuator">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.device-management-page {
  padding-bottom: 30px;
}

/* 顶部标题区 */
.page-top-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
  flex-wrap: wrap;
  gap: 12px;
}

.header-intro h2 {
  margin: 0 0 4px 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
}

.header-sub {
  font-size: 13px;
  color: var(--text-muted);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.sim-btn-group {
  display: flex;
  align-items: center;
}

/* 指标卡片网格 */
.metrics-cards-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.metric-card {
  position: relative;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  cursor: pointer;
  transition: all 0.22s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

.metric-card:hover {
  transform: translateY(-2px);
  border-color: var(--accent);
  box-shadow: 0 4px 16px var(--accent-glow);
}

.metric-card.active {
  border-color: var(--accent);
  background: var(--bg-hover);
  box-shadow: 0 4px 18px var(--accent-glow);
}

.metric-icon-wrap {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.all-icon { background: rgba(99, 102, 241, 0.12); color: #6366f1; }
.online-icon { background: rgba(52, 211, 153, 0.12); color: #10b981; }
.offline-icon { background: rgba(156, 163, 175, 0.12); color: #9ca3af; }
.warning-icon { background: rgba(245, 158, 11, 0.12); color: #f59e0b; }

.metric-info {
  flex: 1;
}

.metric-label {
  font-size: 13px;
  color: var(--text-muted);
  font-weight: 500;
  margin-bottom: 2px;
}

.metric-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.2;
}

.text-online { color: #10b981; }
.text-offline { color: var(--text-muted); }
.text-warning { color: #f59e0b; }

.metric-indicator {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 3px;
  opacity: 0;
  transition: opacity 0.2s;
}

.metric-card.active .metric-indicator,
.metric-card:hover .metric-indicator {
  opacity: 1;
}

.metric-indicator.all { background: #6366f1; }
.metric-indicator.online { background: #10b981; }
.metric-indicator.offline { background: #9ca3af; }
.metric-indicator.warning { background: #f59e0b; }

/* 筛选与操作工具栏 */
.action-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 12px 16px;
  margin-bottom: 18px;
  flex-wrap: wrap;
  gap: 12px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.search-input { width: 280px; }
.filter-select { width: 140px; }
.filter-select-sm { width: 110px; }

.filter-stat-text {
  font-size: 13px;
  color: var(--text-muted);
  margin-left: 6px;
}

.filter-stat-text b {
  color: var(--accent);
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.batch-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--bg-hover);
  padding: 3px 8px;
  border-radius: 6px;
  border: 1px dashed var(--accent);
}

.batch-count {
  font-size: 12px;
  font-weight: 600;
  color: var(--accent);
}

/* ==================== 卡片网格布局 ==================== */
.device-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 16px;
}

.device-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  transition: all 0.22s ease;
  position: relative;
}

.device-card:hover {
  border-color: var(--accent);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}

.device-card.selected {
  border-color: var(--accent);
  background: var(--bg-hover);
}

.device-card.status-online { border-top: 3px solid #10b981; }
.device-card.status-offline { border-top: 3px solid #9ca3af; }
.device-card.status-warning { border-top: 3px solid #f59e0b; }

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.card-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.card-select-checkbox {
  margin-right: 2px;
}

.status-indicator-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-indicator-dot.online {
  background: #10b981;
  box-shadow: 0 0 8px rgba(16, 185, 129, 0.7);
}

.status-indicator-dot.offline {
  background: #9ca3af;
}

.status-indicator-dot.warning {
  background: #f59e0b;
  box-shadow: 0 0 8px rgba(245, 158, 11, 0.7);
}

.card-title-group {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 180px;
}

.card-type-tag {
  font-size: 11px;
  color: var(--text-muted);
}

.card-meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border-light);
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

.id-meta {
  cursor: pointer;
}

.id-meta:hover .copy-icon {
  color: var(--accent);
}

.meta-id-code {
  font-family: monospace;
  font-size: 11px;
  background: var(--bg-hover);
  padding: 1px 5px;
  border-radius: 4px;
  color: var(--text-muted);
}

.copy-icon {
  color: var(--text-muted);
}

/* 卡片内部传感器与执行器小部件 */
.card-section {
  margin-bottom: 10px;
}

.section-badge-bar {
  display: flex;
  align-items: center;
  margin-bottom: 6px;
}

.badge-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  gap: 4px;
}

.mini-sensors-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.mini-sensor-chip {
  display: flex;
  align-items: center;
  gap: 4px;
  background: var(--bg-hover);
  border: 1px solid var(--border-light);
  border-radius: 6px;
  padding: 3px 8px;
  font-size: 12px;
}

.mini-sensor-chip.chip-warn {
  border-color: #f87171;
  background: rgba(248, 113, 113, 0.1);
}

.chip-name { color: var(--text-muted); }
.chip-val { font-weight: 600; color: var(--text-primary); }
.chip-unit { font-size: 10px; color: var(--text-muted); }

.mini-actuators-wrap {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.mini-actuator-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--bg-hover);
  border: 1px solid var(--border-light);
  border-radius: 6px;
  padding: 4px 8px;
}

.act-name {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-primary);
  max-width: 110px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.act-actions {
  display: flex;
  gap: 4px;
}

.act-actions .el-button {
  padding: 2px 8px;
  font-size: 11px;
  min-height: 22px;
}

.card-footer-actions {
  margin-top: auto;
  padding-top: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  border-top: 1px solid var(--border-light);
}

.card-footer-actions .el-button {
  flex: 1;
}

.card-footer-actions .more-btn {
  flex: 0 0 32px;
  padding: 0;
}

/* ==================== 表格列表样式 ==================== */
.table-view-container {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  overflow: hidden;
}

.tbl-device-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.tbl-name-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.tbl-device-name {
  font-weight: 600;
  color: var(--text-primary);
  font-size: 14px;
}

.tbl-id-row {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}

.tbl-id-row code {
  font-family: monospace;
  font-size: 11px;
  color: var(--text-muted);
}

.tbl-id-row:hover code {
  color: var(--accent);
}

.tbl-location {
  font-size: 13px;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
}

.tbl-time {
  font-size: 12px;
  color: var(--text-muted);
}

.tbl-count-badges {
  display: flex;
  gap: 6px;
  justify-content: center;
}

.count-pill {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  padding: 2px 7px;
  border-radius: 10px;
  font-weight: 600;
}

.sensor-pill {
  background: var(--sensor-bg);
  color: var(--sensor-color);
}

.act-pill {
  background: var(--actuator-bg);
  color: var(--actuator-color);
}

.tbl-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

/* 表格展开行 */
.table-expand-wrapper {
  padding: 12px 20px;
  background: var(--bg-hover);
  border-radius: 8px;
  margin: 6px 12px;
}

.expand-panel {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 8px;
  padding: 10px 14px;
  min-height: 90px;
}

.expand-header {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.expand-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.expand-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--bg-hover);
  border: 1px solid var(--border-light);
  border-radius: 6px;
  padding: 4px 10px;
  font-size: 12px;
}

.exp-name { font-weight: 500; color: var(--text-primary); }
.exp-val { color: var(--text-secondary); font-family: monospace; }
.expand-empty { font-size: 12px; color: var(--text-muted); padding: 10px 0; }

.exp-btns { display: flex; gap: 3px; margin-left: 4px; }
.exp-btns .el-button { padding: 2px 6px; font-size: 10px; height: 20px; }

/* ==================== 集中监控看板样式 ==================== */
.data-panel {
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--border-color);
}

.sensor-data-panel { border-top: 3px solid var(--sensor-color); }
.actuator-data-panel { border-top: 3px solid var(--actuator-color); }

.panel-top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: var(--bg-hover);
  border-bottom: 1px solid var(--border-light);
}

.panel-top-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.panel-icon-box {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.sensor-icon-box { background: var(--sensor-bg); color: var(--sensor-color); }
.actuator-icon-box { background: var(--actuator-bg); color: var(--actuator-color); }

.panel-top-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.panel-top-sub {
  font-size: 10px;
  color: var(--text-muted);
  text-transform: uppercase;
}

.panel-table-wrap {
  background: var(--bg-card);
  min-height: 320px;
}

.cell-device { display: flex; align-items: center; gap: 6px; }
.cell-device-status { width: 7px; height: 7px; border-radius: 50%; }
.cell-device-status.online { background: #10b981; }
.cell-device-status.offline { background: #9ca3af; }
.cell-device-status.warning { background: #f59e0b; }

.cell-device-name { font-weight: 500; font-size: 13px; color: var(--text-primary); }
.cell-mono { font-size: 13px; color: var(--text-primary); font-weight: 500; }
.cell-value { font-size: 15px; font-weight: 700; color: var(--text-primary); }
.cell-value-warn { color: var(--danger); }
.cell-unit { font-size: 11px; color: var(--text-muted); margin-left: 2px; }

.panel-empty-table {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 50px 0;
  color: var(--text-muted);
  gap: 8px;
}

/* ==================== 抽屉样式 ==================== */
.drawer-content {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.drawer-device-card {
  background: var(--bg-hover);
  border: 1px solid var(--border-light);
  border-radius: 8px;
  padding: 12px 14px;
}

.ddc-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.ddc-row:last-child { margin-bottom: 0; }
.ddc-label { font-size: 13px; color: var(--text-muted); width: 70px; }
.ddc-val { font-size: 14px; color: var(--text-primary); }
.ddc-val.bold { font-weight: 600; }
.ddc-code { font-family: monospace; font-size: 12px; background: var(--bg-card); padding: 1px 6px; border-radius: 4px; }

.drawer-section {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 10px;
  padding: 14px;
}

.drawer-section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 12px;
}

.preset-commands-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.preset-cmd-btn {
  height: auto;
  padding: 8px 12px;
  justify-content: flex-start;
  text-align: left;
}

.cmd-btn-inner {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}

.cmd-label { font-size: 13px; font-weight: 600; color: var(--text-primary); }
.cmd-code { font-size: 11px; color: var(--accent); font-family: monospace; }

.logs-container {
  max-height: 220px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.empty-logs {
  text-align: center;
  color: var(--text-muted);
  font-size: 12px;
  padding: 20px 0;
}

.log-item {
  background: var(--bg-hover);
  border-radius: 6px;
  padding: 8px 10px;
  font-size: 12px;
  border-left: 3px solid var(--accent);
}

.log-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.log-cmd code { font-family: monospace; font-weight: 600; color: var(--text-primary); }
.log-time { font-size: 11px; color: var(--text-muted); }
.log-resp { display: flex; gap: 6px; font-size: 11px; color: var(--text-secondary); }
.resp-label { color: var(--text-muted); }

/* ==================== 表单弹窗样式 ==================== */
.form-section-card {
  background: var(--bg-hover);
  border: 1px solid var(--border-light);
  border-radius: 10px;
  padding: 14px 16px;
  margin-bottom: 14px;
}

.form-section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.form-section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 6px;
}

.section-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.config-items-container {
  min-height: 50px;
}

.config-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px 0;
  color: var(--text-muted);
  font-size: 12px;
  background: var(--bg-card);
  border: 1px dashed var(--border-color);
  border-radius: 8px;
}

.config-chip-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 8px;
}

.config-item-chip {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 8px;
  padding: 8px 10px;
  transition: all 0.15s;
}

.config-item-chip.sensor-theme { border-left: 3px solid var(--sensor-color); }
.config-item-chip.actuator-theme { border-left: 3px solid var(--actuator-color); }

.chip-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.chip-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 3px;
}

.chip-range {
  font-size: 11px;
  color: var(--text-muted);
}

.chip-ops {
  display: flex;
  gap: 2px;
}

/* 空状态 */
.empty-state-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
  background: var(--bg-card);
  border: 1px dashed var(--border-color);
  border-radius: 12px;
  color: var(--text-muted);
  text-align: center;
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 12px 0 4px 0;
}

.empty-desc {
  font-size: 13px;
  color: var(--text-muted);
  margin: 0 0 16px 0;
}

/* 响应式调整 */
@media (max-width: 1200px) {
  .metrics-cards-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .two-panel-row .el-col {
    max-width: 100%;
    flex: 0 0 100%;
    margin-bottom: 16px;
  }
}

@media (max-width: 768px) {
  .metrics-cards-grid {
    grid-template-columns: 1fr;
  }
  .action-toolbar {
    flex-direction: column;
    align-items: stretch;
  }
  .toolbar-left,
  .toolbar-right {
    width: 100%;
    justify-content: space-between;
  }
  .search-input {
    width: 100%;
  }
}
</style>

<style scoped>
.device-management-page { max-width: 1440px; margin: 0 auto; }
.page-top-header { margin-bottom: 24px; }
.header-intro h2 { font-size: 26px; letter-spacing: -.02em; }
.header-intro h2::after { content: 'DEVICE CENTER'; display: block; margin-top: 5px; color: var(--color-cyan); font: 10px/1 'Roboto Mono', monospace; letter-spacing: .14em; }
.header-sub { display: block; margin-top: 10px; color: var(--text-secondary); }
.header-actions :deep(.el-button) { height: 36px; border-radius: 6px; }
.metrics-cards-grid { gap: 12px; margin-bottom: 12px; }
.metric-card { min-height: 106px; padding: 16px 18px; border-radius: 8px; background: var(--bg-card); box-shadow: none; }
.metric-card::before { content: ''; position: absolute; top: 0; bottom: 0; left: 0; width: 3px; background: var(--border-color); }
.metric-card:hover { transform: none; border-color: var(--border-color); box-shadow: none; background: var(--bg-hover); }
.metric-card.active { border-color: rgba(22,119,255,.55); background: var(--color-primary-soft); box-shadow: inset 3px 0 0 var(--color-primary); }
.metric-card.active::before { background: var(--color-primary); }.metric-card:has(.online-icon)::before { background: var(--color-success); }.metric-card:has(.warning-icon)::before { background: var(--color-warning); }
.metric-icon-wrap { width: 36px; height: 36px; border-radius: 6px; }.metric-info { display: flex; align-items: baseline; gap: 9px; }.metric-label { color: var(--text-secondary); font-size: 13px; }.metric-value { color: var(--text-primary); font: 700 26px/1 'Roboto Mono', monospace; }.metric-indicator { display: none; }
.action-toolbar { padding: 14px; margin-bottom: 12px; border: 1px solid var(--border-color); border-radius: 8px; background: var(--bg-card); }
.search-input { width: min(320px, 100%); }.search-input :deep(.el-input__wrapper), .filter-select :deep(.el-select__wrapper), .filter-select-sm :deep(.el-select__wrapper) { min-height: 36px; background: var(--bg-secondary); box-shadow: 0 0 0 1px var(--border-color) inset !important; }
.filter-stat-text { color: var(--text-muted); font-size: 12px; }.filter-stat-text b { color: var(--text-primary); font-family: 'Roboto Mono', monospace; }
.view-mode-toggle :deep(.el-radio-button__inner) { border-color: var(--border-color); background: var(--bg-secondary); color: var(--text-secondary); box-shadow: none; }.view-mode-toggle :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) { background: var(--color-primary-soft); border-color: var(--color-primary); color: var(--text-primary); box-shadow: -1px 0 0 0 var(--color-primary); }
.device-card-grid { gap: 12px; }.device-card { border-radius: 8px; background: var(--bg-card); border-color: var(--border-color); box-shadow: none; }.device-card:hover { transform: none; border-color: rgba(22,119,255,.5); box-shadow: 0 8px 22px rgba(0,0,0,.14); }.device-card.selected { outline: 1px solid var(--color-primary); background: var(--bg-hover); }
.card-title { color: var(--text-primary); }.card-type-tag, .meta-item, .sensor-mini-name { color: var(--text-secondary); }.status-indicator-dot { box-shadow: 0 0 0 3px rgba(100,116,139,.12); }.status-indicator-dot.online { box-shadow: 0 0 0 3px rgba(34,197,94,.12); }.card-section { border-top-color: var(--border-light); }.section-badge-bar { color: var(--text-muted); font: 10px 'Roboto Mono', monospace; letter-spacing: .08em; }.mini-sensor, .mini-actuator { border-radius: 6px; background: var(--bg-secondary); border-color: var(--border-light); }.sensor-mini-value { color: var(--text-primary); font-family: 'Roboto Mono', monospace; }.card-footer-actions { border-top-color: var(--border-light); }
.data-panel { border-radius: 8px; border-color: var(--border-color); box-shadow: none; background: var(--bg-card); }.panel-top-bar { border-bottom-color: var(--border-color); background: transparent; }.panel-top-title { color: var(--text-primary); }.panel-top-sub { color: var(--text-muted); font-family: 'Roboto Mono', monospace; letter-spacing: .06em; }.empty-state-box { min-height: 280px; border-radius: 8px; background: var(--bg-card); border-color: var(--border-color); }
@media (max-width: 900px) { .metrics-cards-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }.action-toolbar { align-items: stretch; }.toolbar-left, .toolbar-right { flex-wrap: wrap; }.header-actions { width: 100%; flex-wrap: wrap; } }
@media (max-width: 560px) { .metrics-cards-grid { grid-template-columns: 1fr; }.metric-card { min-height: 82px; }.header-actions :deep(.el-button-group) { display: none; }.search-input { width: 100%; }.toolbar-left { width: 100%; }.filter-select, .filter-select-sm { flex: 1; min-width: 0; } }
</style>
