<script setup lang="ts">
defineOptions({ name: 'DeviceDetail' })
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDeviceStore } from '../../stores/device'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../api'
import { realApi } from '../../api/realApi'
import {
  ArrowLeft,
  Refresh,
  Odometer,
  Switch,
  CopyDocument
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const deviceStore = useDeviceStore()

const regeneratedApiKey = ref('')
const regeneratingApiKey = ref(false)
const deviceApiKey = computed(() => regeneratedApiKey.value || (device.value as any)?.apiKey || '')

function copyApiKey() {
  if (deviceApiKey.value) {
    navigator.clipboard.writeText(deviceApiKey.value)
    ElMessage.success('API Key 已复制到剪贴板')
  }
}

async function regenerateApiKey() {
  try {
    await ElMessageBox.confirm('重新生成后，旧 API Key 将无法用于新的设备连接。', '重新生成 API Key', {
      type: 'warning', confirmButtonText: '重新生成', cancelButtonText: '取消'
    })
  } catch {
    return
  }
  regeneratingApiKey.value = true
  try {
    regeneratedApiKey.value = await realApi.regenerateDeviceApiKey(deviceId.value)
    ElMessage.success('新 API Key 已生成，请立即复制保存')
  } catch (e: any) {
    ElMessage.error(e?.message || 'API Key 重新生成失败')
  } finally {
    regeneratingApiKey.value = false
  }
}

function copySensorId(id: string) {
  navigator.clipboard.writeText(id)
  ElMessage.success('传感器 ID 已复制')
}

const deviceId = computed(() => route.params.id as string)
const commandInput = ref('')
const commandHistory = ref<{ command: string; time: string; response?: string; status?: string }[]>([])
const activeTab = ref('overview')
const refreshing = ref(false)
const detailLoading = ref(true)
const detailError = ref('')

const device = computed(() => deviceStore.currentDevice)
const actuatorCount = computed(() => ((device.value as any)?.actuators?.length) || 0)
const actuators = computed(() => ((device.value as any)?.actuators) || [])

// ===== 执行器控制 =====
const actuatorOperating = ref<Record<string, boolean>>({})

/** 执行器实时状态：优先取实时值 value，无值时回退默认配置 */
function getActuatorLiveStatus(actuator: any) {
  if (actuator.value !== undefined && actuator.value !== null) {
    return actuator.value === 1 || actuator.value === 'on' ? 'on' : 'off'
  }
  return actuator.defaultValue === 'on' ? 'on' : 'off'
}

async function sendActuatorCommand(actuator: any, command: string) {
  const key = `${deviceId.value}:${actuator.name}`
  if (actuatorOperating.value[key]) return
  actuatorOperating.value[key] = true
  try {
    await deviceStore.sendCommand(deviceId.value, command, { actuator: actuator.name })
    ElMessage.success(`[${actuator.name}] 指令发送成功: ${command.toUpperCase()}`)
    // 本地立即反映状态（轮询随后会用服务端权威值覆盖）
    const target = actuators.value.find((a: any) => a.id === actuator.id)
    if (target) target.value = command.toLowerCase() === 'on' || command === '1' ? 1 : 0
    // 拉取服务端权威状态，保证与后端一致
    await deviceStore.fetchDeviceById(deviceId.value)
  } catch (e: any) {
    ElMessage.error(e.message || '指令发送失败')
  } finally {
    actuatorOperating.value[key] = false
  }
}

function goBack() {
  router.push('/devices')
}

async function refreshAll() {
  refreshing.value = true
  try {
    await loadDevice(deviceId.value)
    ElMessage.success('刷新成功')
  } catch {
    ElMessage.error('刷新失败，请重试')
  } finally {
    refreshing.value = false
  }
}

async function sendCommand() {
  if (!commandInput.value.trim() || !device.value) return
  const cmd = commandInput.value.trim()
  try {
    const result = await deviceStore.sendCommand(deviceId.value, cmd)
    commandHistory.value.unshift({
      command: cmd,
      time: new Date().toLocaleTimeString(),
      response: result.message,
      status: 'success'
    })
    ElMessage.success('命令发送成功')
  } catch {
    commandHistory.value.unshift({
      command: cmd,
      time: new Date().toLocaleTimeString(),
      status: 'failed'
    })
  }
  commandInput.value = ''
}

async function loadCommandLogs() {
  try {
    const logs = await api.getCommandLogs(deviceId.value)
    commandHistory.value = logs.map((l: any) => ({
      command: l.command,
      time: new Date(l.sentAt).toLocaleTimeString(),
      response: l.response,
      status: l.status === 'EXECUTED' ? 'success' : 'info'
    }))
  } catch { /* ignore */ }
}

async function loadDevice(id: string) {
  detailLoading.value = true
  detailError.value = ''
  deviceStore.currentDevice = null
  try {
    const loaded = await deviceStore.fetchDeviceById(id)
    if (!loaded) {
      detailError.value = '未找到该设备，设备可能已被删除或你没有访问权限。'
      return
    }
    await loadCommandLogs()
    deviceStore.startPollDevice(id, 3000)
  } catch (e: any) {
    detailError.value = e?.message || '设备详情加载失败，请稍后重试。'
  } finally {
    detailLoading.value = false
  }
}

watch(deviceId, (newId, oldId) => {
  if (newId !== oldId) void loadDevice(newId)
})

function getStatusType(status: string) {
  return status === 'online' ? 'success' : status === 'offline' ? 'info' : 'warning'
}

function getStatusText(status: string) {
  return status === 'online' ? '在线' : status === 'offline' ? '离线' : '告警'
}

onMounted(async () => {
  await loadDevice(deviceId.value)
})

onUnmounted(() => {
  deviceStore.stopRealtimeUpdates()
})
</script>

<template>
  <div class="device-detail">
    <div class="page-header">
      <div class="header-left">
        <el-button link :icon="ArrowLeft" @click="goBack">返回</el-button>
        <h2>{{ device?.name || '设备详情' }}</h2>
      </div>
    </div>

    <el-row :gutter="20" v-if="device">
      <el-col :span="24">
        <el-card>
          <el-tabs v-model="activeTab">
            <el-tab-pane label="概览" name="overview">
              <!-- 设备摘要横幅 -->
              <div class="summary-bar">
                <div class="summary-item">
                  <span class="summary-label">状态</span>
                  <el-tag :type="getStatusType(device.status)" size="large">{{ getStatusText(device.status) }}</el-tag>
                </div>
                <div class="summary-item">
                  <span class="summary-label"><el-icon :size="14"><Odometer /></el-icon> 传感器</span>
                  <span class="summary-value">{{ device.sensors.length }}</span>
                </div>
                <div class="summary-item">
                  <span class="summary-label"><el-icon :size="14"><Switch /></el-icon> 执行器</span>
                  <span class="summary-value">{{ actuatorCount }}</span>
                </div>
                <div class="summary-item">
                  <span class="summary-label">位置</span>
                  <span class="summary-value">{{ device.location || '-' }}</span>
                </div>
                <div class="summary-item">
                  <span class="summary-label">最后活跃</span>
                  <span class="summary-value">{{ new Date(device.lastActive).toLocaleString() }}</span>
                </div>
                <div class="summary-actions">
                  <el-button size="small" :icon="Refresh" :loading="refreshing" @click="refreshAll">刷新</el-button>
                </div>
              </div>

              <div class="overview-content">
                <el-descriptions :column="2" border>
                  <el-descriptions-item label="设备ID">{{ device.id }}</el-descriptions-item>
                  <el-descriptions-item label="设备名称">{{ device.name }}</el-descriptions-item>
                  <el-descriptions-item label="设备类型">{{ device.type }}</el-descriptions-item>
                  <el-descriptions-item label="创建时间">{{ new Date(device.createdAt).toLocaleString() }}</el-descriptions-item>
                  <el-descriptions-item label="API Key" :span="2">
                    <div class="api-key-row">
                      <code class="api-key-text">{{ deviceApiKey }}</code>
                      <el-button :icon="CopyDocument" size="small" text type="primary" @click="copyApiKey">复制</el-button>
                      <el-button size="small" text type="warning" :loading="regeneratingApiKey" @click="regenerateApiKey">重新生成</el-button>
                    </div>
                  </el-descriptions-item>
                  <el-descriptions-item label="描述" :span="2">{{ device.description || '-' }}</el-descriptions-item>
                </el-descriptions>

                <!-- Sensors Data Section -->
                <div class="section-header">
                  <div class="section-title">
                    <el-icon :size="18" color="var(--sensor-color)"><Odometer /></el-icon>
                    <span>数据采集 — 传感器实时数据</span>
                    <el-tag size="small" type="info" round>{{ device.sensors.length }} 个</el-tag>
                  </div>
                </div>
                <el-row :gutter="15" class="sensor-cards" v-if="device.sensors.length > 0">
                  <el-col :span="8" v-for="sensor in device.sensors" :key="sensor.id">
                    <div class="sensor-card">
                      <div class="sensor-card-header">
                        <span class="sensor-type-badge">{{ sensor.type }}</span>
                        <el-button
                          size="small"
                          text
                          type="primary"
                          class="copy-id-btn"
                          @click="copySensorId(sensor.id)"
                        >
                          <el-icon :size="12"><CopyDocument /></el-icon>
                        </el-button>
                      </div>
                      <div class="sensor-name">{{ sensor.name }}</div>
                      <div class="sensor-id-row">
                        <code class="sensor-id-code">{{ sensor.id }}</code>
                      </div>
                      <div class="sensor-value">
                        {{ sensor.value.toFixed(2) }}
                        <span class="sensor-unit">{{ sensor.unit }}</span>
                      </div>
                      <el-progress
                        :percentage="Math.min(100, Math.max(0, (sensor.value - sensor.min) / (sensor.max - sensor.min) * 100))"
                        :status="sensor.value > sensor.max * 0.9 ? 'exception' : ''"
                        :stroke-width="8"
                      />
                      <div class="sensor-range-label">
                        范围: {{ sensor.min }} ~ {{ sensor.max }} {{ sensor.unit }}
                      </div>
                    </div>
                  </el-col>
                </el-row>
                <div v-else class="empty-section">
                  <el-icon :size="28" color="var(--text-muted)"><Odometer /></el-icon>
                  <span>暂无传感器数据</span>
                </div>

                <!-- Actuators Section -->
                <div class="section-header" style="margin-top: 24px;">
                  <div class="section-title">
                    <el-icon :size="18" color="var(--actuator-color)"><Switch /></el-icon>
                    <span>执行控制 — 执行器状态</span>
                    <el-tag size="small" type="warning" round>{{ actuatorCount }} 个</el-tag>
                  </div>
                </div>
                <el-row :gutter="15" class="actuator-cards" v-if="actuators?.length > 0">
                  <el-col :span="8" v-for="actuator in actuators" :key="actuator.id">
                    <div class="actuator-card">
                      <div class="actuator-card-header">
                        <span class="actuator-type-badge">{{ actuator.type }}</span>
                        <el-tag :type="getActuatorLiveStatus(actuator) === 'on' ? 'success' : 'info'" size="small" round>
                          {{ getActuatorLiveStatus(actuator) === 'on' ? '已开启' : '已关闭' }}
                        </el-tag>
                      </div>
                      <div class="actuator-name">{{ actuator.name }}</div>
                      <div class="actuator-meta">
                        <span class="actuator-cmd">指令: {{ actuator.commandType }}</span>
                      </div>
                      <div class="actuator-actions">
                        <el-button
                          size="small"
                          type="success"
                          plain
                          :loading="actuatorOperating[`${deviceId}:${actuator.name}`]"
                          @click="sendActuatorCommand(actuator, 'on')"
                        >开启</el-button>
                        <el-button
                          size="small"
                          type="danger"
                          plain
                          :loading="actuatorOperating[`${deviceId}:${actuator.name}`]"
                          @click="sendActuatorCommand(actuator, 'off')"
                        >关闭</el-button>
                      </div>
                    </div>
                  </el-col>
                </el-row>
                <div v-else class="empty-section">
                  <el-icon :size="28" color="var(--text-muted)"><Switch /></el-icon>
                  <span>暂无执行器配置</span>
                </div>
              </div>
            </el-tab-pane>

            <el-tab-pane label="命令控制" name="command">
              <div class="command-panel">
                <div class="command-input-area">
                  <el-input
                    v-model="commandInput"
                    placeholder="输入命令 (如: reboot, setInterval:5000, getStatus)"
                    @keyup.enter="sendCommand"
                  >
                    <template #append>
                      <el-button type="primary" @click="sendCommand">发送</el-button>
                    </template>
                  </el-input>
                </div>
                <div class="command-history">
                  <div v-for="(item, index) in commandHistory" :key="index" class="history-item">
                    <div class="history-command">
                      <span class="time">[{{ item.time }}]</span>
                      <el-tag size="small">发送</el-tag>
                      <span class="cmd">{{ item.command }}</span>
                    </div>
                    <div v-if="item.response" class="history-response">
                      <el-tag type="success" size="small">响应</el-tag>
                      <span>{{ item.response }}</span>
                    </div>
                  </div>
                  <el-empty v-if="commandHistory.length === 0" description="暂无命令记录" />
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>

    <el-card v-else class="detail-state">
      <el-skeleton v-if="detailLoading" :rows="6" animated />
      <el-result
        v-else
        :icon="detailError ? 'error' : 'info'"
        :title="detailError ? '设备详情加载失败' : '暂无设备详情'"
        :sub-title="detailError || '请从设备列表重新选择设备。'"
      >
        <template #extra>
          <el-button @click="goBack">返回设备列表</el-button>
          <el-button v-if="detailError" type="primary" @click="loadDevice(deviceId)">重试</el-button>
        </template>
      </el-result>
    </el-card>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 15px;
}

.header-left h2 {
  margin: 0;
  font-size: 22px;
  color: var(--text-primary);
}

.overview-content {
  padding: 10px 0;
}

.sensor-cards {
  margin-top: 15px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 20px;
  margin-bottom: 15px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.empty-section {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 30px 0;
  color: var(--text-muted);
  font-size: 14px;
  background: var(--bg-hover);
  border-radius: 8px;
  border: 1px dashed var(--border-color);
}

.sensor-card {
  background: var(--bg-hover);
  border-radius: 10px;
  padding: 16px;
  margin-bottom: 15px;
  border: 1px solid var(--border-light);
  transition: all 0.25s;
}

.sensor-card:hover {
  border-color: var(--sensor-color);
  box-shadow: 0 2px 12px var(--accent-glow);
}

.sensor-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.copy-id-btn {
  padding: 2px;
  opacity: 0.5;
  transition: opacity 0.15s;
}

.sensor-card:hover .copy-id-btn {
  opacity: 1;
}

.sensor-id-row {
  margin-bottom: 4px;
}

.sensor-id-code {
  font-family: monospace;
  font-size: 10px;
  color: var(--text-muted);
  background: var(--bg-hover);
  padding: 1px 6px;
  border-radius: 3px;
}

.sensor-type-badge {
  font-size: 11px;
  color: var(--sensor-color);
  background: var(--sensor-bg);
  padding: 2px 8px;
  border-radius: 4px;
  text-transform: uppercase;
}

.sensor-name {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.sensor-value {
  font-size: 28px;
  font-weight: bold;
  color: var(--text-primary);
  margin-bottom: 10px;
}

.sensor-unit {
  font-size: 14px;
  color: var(--text-muted);
  margin-left: 4px;
}

.sensor-range-label {
  margin-top: 8px;
  font-size: 11px;
  color: var(--text-muted);
  text-align: right;
}

/* Actuator Cards */
.actuator-cards {
  margin-top: 0;
}

.actuator-card {
  background: var(--bg-hover);
  border-radius: 10px;
  padding: 16px;
  margin-bottom: 15px;
  border: 1px solid var(--border-light);
  transition: all 0.25s;
}

.actuator-card:hover {
  border-color: var(--actuator-color);
  box-shadow: 0 2px 12px var(--accent-glow);
}

.actuator-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.actuator-type-badge {
  font-size: 11px;
  color: var(--actuator-color);
  background: var(--actuator-bg);
  padding: 2px 8px;
  border-radius: 4px;
  text-transform: uppercase;
}

.actuator-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.actuator-meta {
  margin-bottom: 10px;
}

.actuator-cmd {
  font-size: 12px;
  color: var(--text-muted);
  font-family: monospace;
}

.actuator-actions {
  display: flex;
  gap: 8px;
}

.command-panel {
  padding: 10px 0;
}

.command-input-area {
  margin-bottom: 20px;
}

.command-history {
  max-height: 400px;
  overflow-y: auto;
}

.history-item {
  padding: 10px;
  background: var(--bg-hover);
  border-radius: 6px;
  margin-bottom: 10px;
}

.history-command {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 5px;
}

.time {
  color: var(--text-muted);
  font-size: 12px;
}

.cmd {
  font-family: monospace;
  color: var(--accent-light);
}

.history-response {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-left: 20px;
  color: var(--success);
}

/* Summary Bar */
.summary-bar {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 14px 18px;
  margin-bottom: 18px;
  background: var(--bg-hover);
  border-radius: 10px;
  border: 1px solid var(--border-light);
  flex-wrap: wrap;
}

.summary-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.summary-label {
  font-size: 13px;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 3px;
}

.summary-value {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.summary-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

.api-key-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.api-key-text {
  font-family: monospace;
  font-size: 13px;
  color: var(--accent);
  background: var(--bg-hover);
  padding: 2px 8px;
  border-radius: 4px;
  word-break: break-all;
}

.detail-state {
  min-height: 260px;
}
</style>
