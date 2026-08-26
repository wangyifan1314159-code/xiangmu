<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Link, CircleCheckFilled, CircleCloseFilled, Delete } from '@element-plus/icons-vue'
import { realApi } from '../../api/realApi'
import { useAuthStore } from '../../stores/auth'

interface TcpConnection {
  gatewayId: string | null
  deviceIds: string[]
  deviceCount: number
  connectionMode: 'GATEWAY' | 'DEVICE'
  deviceId: string
  channelId: string
  remoteAddress: string
  connectedAt: number
  onlineSeconds: number
}

const authStore = useAuthStore()
const connections = ref<TcpConnection[]>([])
const status = ref<{ enabled: boolean; onlineDevices: number; onlineConnections?: number }>({ enabled: false, onlineDevices: 0 })
const loading = ref(false)
let refreshTimer: ReturnType<typeof setInterval> | null = null

async function fetchConnections() {
  loading.value = true
  try {
    const [conns, tcpStatus] = await Promise.all([
      realApi.getTcpConnections(),
      realApi.getTcpStatus()
    ])
    connections.value = conns || []
    status.value = tcpStatus || { enabled: false, onlineDevices: 0 }
  } catch {
    connections.value = []
  } finally {
    loading.value = false
  }
}

function formatDuration(seconds: number): string {
  if (seconds < 60) return `${seconds} 秒`
  if (seconds < 3600) return `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒`
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  return `${h} 时 ${m} 分`
}

function formatTime(ts: number): string {
  if (!ts) return ''
  const d = new Date(ts)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`
  return d.toLocaleString()
}

async function handleDisconnect(conn: TcpConnection) {
  try {
    await ElMessageBox.confirm(
      `确定要强制断开设备「${conn.deviceId}」的 TCP 连接吗？设备将立即下线并需要重新连接。`,
      '断开连接确认',
      { type: 'warning', confirmButtonText: '强制断开', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户取消
  }
  try {
    await realApi.disconnectTcpConnection(conn.deviceId)
    ElMessage.success(`已断开 ${conn.deviceId} 的 TCP 连接`)
    fetchConnections()
  } catch (e: any) {
    ElMessage.error(e?.message || '断开失败')
  }
}

onMounted(() => {
  fetchConnections()
  refreshTimer = setInterval(fetchConnections, 5000) // 每 5 秒刷新
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<template>
  <div class="tcp-connections">
    <div class="page-header">
      <h2>连接实例</h2>
      <p>查看设备 TCP 长连接实例（JSON 行协议，端口 1884）</p>
    </div>

    <!-- 状态卡片 -->
    <el-row :gutter="20" class="stat-row">
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-inner">
            <div class="stat-icon online"><el-icon :size="26"><Link /></el-icon></div>
            <div>
              <div class="stat-label">TCP 通道</div>
              <div class="stat-value">{{ status.enabled ? '已启用' : '未启用' }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-inner">
            <div class="stat-icon online"><el-icon :size="26"><CircleCheckFilled /></el-icon></div>
            <div>
              <div class="stat-label">在线连接数</div>
              <div class="stat-value">{{ status.onlineConnections ?? status.onlineDevices }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-inner">
            <div class="stat-icon total"><el-icon :size="26"><Refresh /></el-icon></div>
            <div>
              <div class="stat-label">自动刷新</div>
              <div class="stat-value">每 5 秒</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span>在线连接实例</span>
          <div>
            <el-button :icon="Refresh" size="small" :loading="loading" @click="fetchConnections">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table :data="connections" v-loading="loading" empty-text="暂无在线 TCP 连接" stripe>
        <el-table-column label="连接类型" width="130">
          <template #default="{ row }">
            <el-tag :type="row.connectionMode === 'GATEWAY' ? 'primary' : 'info'">
              {{ row.connectionMode === 'GATEWAY' ? '网关连接' : '设备连接' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="网关 / 设备" min-width="180">
          <template #default="{ row }">
            <span class="device-id">{{ row.gatewayId || row.deviceId }}</span>
            <div v-if="row.gatewayId" class="device-count">挂载 {{ row.deviceCount }} 台设备</div>
          </template>
        </el-table-column>
        <el-table-column v-if="connections.some(c => c.connectionMode === 'GATEWAY')" type="expand" width="52">
          <template #default="{ row }">
            <div class="device-list" v-if="row.deviceIds?.length">
              <span v-for="id in row.deviceIds" :key="id" class="device-chip">{{ id }}</span>
            </div>
            <span v-else class="device-list-empty">暂无可见设备</span>
          </template>
        </el-table-column>
        <el-table-column prop="remoteAddress" label="远端地址" min-width="180" />
        <el-table-column prop="channelId" label="通道 ID" min-width="120">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.channelId }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="连接时间" min-width="130">
          <template #default="{ row }">{{ formatTime(row.connectedAt) }}</template>
        </el-table-column>
        <el-table-column label="在线时长" min-width="120">
          <template #default="{ row }">
            <span class="online-dot" />
            {{ formatDuration(row.onlineSeconds) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default>
            <el-tag size="small" type="success">在线</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="authStore.isAdmin" label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" :icon="Delete" @click="handleDisconnect(row)">断开</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="connections.length === 0 && status.enabled" class="empty-hint">
        <el-icon><CircleCloseFilled /></el-icon>
        <span>暂无设备建立 TCP 长连接。设备连接方式参见「接口文档 - TCP 接入」。</span>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page-header {
  margin-bottom: 20px;
}
.page-header h2 {
  margin: 0 0 6px;
  font-size: 22px;
}
.page-header p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
}
.stat-row {
  margin-bottom: 20px;
}
.stat-inner {
  display: flex;
  align-items: center;
  gap: 14px;
}
.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-icon.online {
  background: rgba(103, 194, 58, 0.12);
  color: #67c23a;
}
.stat-icon.total {
  background: rgba(64, 158, 255, 0.12);
  color: #409eff;
}
.stat-label {
  font-size: 13px;
  color: var(--text-secondary);
}
.stat-value {
  font-size: 20px;
  font-weight: 600;
  margin-top: 2px;
}
.table-card {
  border-radius: 8px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.device-id {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 13px;
}
.device-count {
  color: var(--text-secondary);
  font-size: 12px;
  margin-top: 4px;
}
.device-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 4px 24px;
}
.device-chip {
  padding: 4px 8px;
  border: 1px solid var(--border-light);
  border-radius: 4px;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
}
.device-list-empty {
  padding-left: 24px;
  color: var(--text-secondary);
}
.online-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #67c23a;
  margin-right: 6px;
  vertical-align: middle;
}
.empty-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px 0;
  color: var(--text-secondary);
  font-size: 13px;
}
</style>
