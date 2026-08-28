<script setup lang="ts">
import { onMounted, onUnmounted, onActivated, onDeactivated, ref } from 'vue'
import { Connection, Delete, Refresh, VideoPlay, VideoPause } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { realApi } from '../../api/realApi'
import { useAuthStore } from '../../stores/auth'

type BinarySession = { id: string; host: string; port: number; status: string; deviceId: string; deviceName: string; lastFrameAt?: number; invalidFrameCount?: number }
type Listener = { port: number; status: string }

const authStore = useAuthStore()
const binaryHost = ref('192.168.1.158')
const binaryPort = ref<number | undefined>(9000)
const binaryDeviceId = ref('')
const listenPort = ref<number | undefined>()
const binarySessions = ref<BinarySession[]>([])
const listeners = ref<Listener[]>([])
const devices = ref<any[]>([])
const loading = ref(false)
const binaryConnecting = ref(false)
const listening = ref(false)
let refreshTimer: ReturnType<typeof setInterval> | undefined

async function refresh() {
  loading.value = true
  try {
    const [activeBinarySessions, activeListeners, deviceList] = await Promise.all([
      realApi.getBinaryTcpConnections(),
      realApi.getTcpListeners(),
      realApi.getDevices()
    ])
    binarySessions.value = activeBinarySessions || []
    listeners.value = activeListeners || []
    devices.value = deviceList || []
  } catch (error: any) {
    ElMessage.error(error?.message || '无法获取 TCP 状态')
  } finally {
    loading.value = false
  }
}

async function connectBinary() {
  if (!binaryHost.value.trim() || !binaryPort.value || !binaryDeviceId.value) {
    return ElMessage.warning('请选择设备并输入目标 IP 和端口')
  }
  binaryConnecting.value = true
  try {
    await realApi.connectBinaryTcpClient(binaryHost.value.trim(), binaryPort.value, binaryDeviceId.value)
    ElMessage.success(`已连接掘进机 ${binaryHost.value}:${binaryPort.value}`)
    await refresh()
  } catch (error: any) {
    ElMessage.error(error?.message || '掘进机 TCP 连接失败')
  } finally {
    binaryConnecting.value = false
  }
}

async function startListener() {
  if (!listenPort.value) return ElMessage.warning('请输入本地监听端口')
  listening.value = true
  try {
    await realApi.startTcpListener(listenPort.value)
    ElMessage.success(`正在监听 ${listenPort.value}`)
    listenPort.value = undefined
    await refresh()
  } catch (error: any) {
    ElMessage.error(error?.message || '启动监听失败')
  } finally {
    listening.value = false
  }
}

async function disconnectBinary(id: string) {
  try {
    await realApi.disconnectBinaryTcpClient(id)
    await refresh()
  } catch (error: any) {
    ElMessage.error(error?.message || '断开失败')
  }
}

function formatTime(time?: number) {
  return time ? new Date(time).toLocaleString() : '-'
}

async function stopListener(activePort: number) {
  try {
    await realApi.stopTcpListener(activePort)
    await refresh()
  } catch (error: any) {
    ElMessage.error(error?.message || '停止监听失败')
  }
}

function startPolling() {
  stopPolling()
  refreshTimer = setInterval(refresh, 5000)
}
function stopPolling() {
  if (refreshTimer) { clearInterval(refreshTimer); refreshTimer = undefined }
}

onMounted(() => {
  refresh()
  startPolling()
})

onActivated(() => { startPolling() })
onDeactivated(() => { stopPolling() })
onUnmounted(() => { stopPolling() })
</script>

<template>
  <div class="tcp-device-connect">
    <div class="page-header">
      <div>
        <h2>设备连接</h2>
        <p>连接掘进机二进制数据流，持续解析并写入设备中心。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" circle title="刷新" @click="refresh" />
    </div>

    <el-alert v-if="!authStore.isAdmin" type="warning" :closable="false" show-icon title="仅管理员可创建或关闭 TCP 连接" />

    <el-row :gutter="16" class="connect-tools">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never">
          <template #header>掘进机二进制通道</template>
          <el-form label-position="top" @submit.prevent="connectBinary">
            <el-form-item label="设备中心设备"><el-select v-model="binaryDeviceId" filterable placeholder="选择已创建的设备" aria-label="设备中心设备"><el-option v-for="device in devices" :key="device.id" :label="device.name" :value="device.id" /></el-select></el-form-item>
            <el-row :gutter="12">
              <el-col :xs="24" :sm="15"><el-form-item label="目标 IP"><el-input v-model="binaryHost" aria-label="掘进机目标 IP" placeholder="192.168.1.158" /></el-form-item></el-col>
              <el-col :xs="24" :sm="9"><el-form-item label="端口"><el-input-number v-model="binaryPort" :min="1" :max="65535" controls-position="right" /></el-form-item></el-col>
            </el-row>
            <el-button type="primary" :icon="Connection" :loading="binaryConnecting" :disabled="!authStore.isAdmin" native-type="submit">连接掘进机</el-button>
          </el-form>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="10">
        <el-card shadow="never">
          <template #header>本地监听</template>
          <el-form label-position="top" @submit.prevent="startListener">
            <el-form-item label="本地监听端口"><el-input-number v-model="listenPort" :min="1" :max="65535" controls-position="right" /></el-form-item>
            <el-button type="primary" plain :icon="VideoPlay" :loading="listening" :disabled="!authStore.isAdmin" native-type="submit">监听</el-button>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="session-card">
      <template #header><div class="card-header"><span>掘进机二进制会话</span><el-tag type="warning">{{ binarySessions.length }} 个</el-tag></div></template>
      <el-table :data="binarySessions" v-loading="loading" empty-text="暂无掘进机二进制连接" stripe>
        <el-table-column prop="deviceName" label="绑定设备" min-width="150" />
        <el-table-column label="目标" min-width="160"><template #default="{ row }"><code>{{ row.host }}:{{ row.port }}</code></template></el-table-column>
        <el-table-column label="状态" width="130"><template #default="{ row }"><el-tag :type="row.status === 'CONNECTED' ? 'success' : row.status === 'NO_DATA' ? 'warning' : 'info'">{{ row.status === 'CONNECTED' ? '已连接' : row.status === 'NO_DATA' ? '已连接·无数据' : '重连中' }}</el-tag></template></el-table-column>
        <el-table-column label="最近收帧" min-width="170"><template #default="{ row }">{{ formatTime(row.lastFrameAt) }}</template></el-table-column>
        <el-table-column label="无效帧" width="100"><template #default="{ row }">{{ row.invalidFrameCount || 0 }}</template></el-table-column>
        <el-table-column v-if="authStore.isAdmin" label="操作" width="90"><template #default="{ row }"><el-button link type="danger" :icon="Delete" @click="disconnectBinary(row.id)">断开</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="session-card">
      <template #header><div class="card-header"><span>本地监听</span><el-tag>{{ listeners.length }} 个</el-tag></div></template>
      <el-empty v-if="listeners.length === 0" description="未启动本地监听" />
      <div v-else class="listener-list"><div v-for="listener in listeners" :key="listener.port" class="listener-row"><span><span class="online-dot" />端口 {{ listener.port }}</span><el-button v-if="authStore.isAdmin" link type="danger" :icon="VideoPause" @click="stopListener(listener.port)">停止</el-button></div></div>
    </el-card>
  </div>
</template>

<style scoped>
.tcp-device-connect { max-width: 1200px; margin: 0 auto; }
.page-header, .card-header, .listener-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.page-header { margin-bottom: 20px; }.page-header h2 { margin: 0; font-size: 24px; }.page-header p { margin: 7px 0 0; color: var(--text-secondary); font-size: 13px; }
.connect-tools { margin: 16px 0; }.session-card { margin-top: 16px; border-radius: 8px; }.listener-list { display: grid; gap: 8px; }.listener-row { padding: 10px 12px; border: 1px solid var(--border-color); border-radius: 6px; } code { color: var(--text-primary); }
.online-dot { display: inline-block; width: 8px; height: 8px; margin-right: 8px; border-radius: 50%; background: var(--color-success); }
@media (max-width: 768px) { .connect-tools :deep(.el-col) { margin-bottom: 12px; } }
</style>
