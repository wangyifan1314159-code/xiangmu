<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Bell, CircleCheck, Refresh, WarningFilled, Document } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { realApi } from '../../api/realApi'

const loading = ref(false)
const records = ref<any[]>([])
const stats = ref<Record<string, number>>({})
const level = ref('')
const status = ref('TRIGGERED')
const total = ref(0)
const page = ref(0)
const size = 20
const unhandled = computed(() => stats.value.triggered || stats.value.TRIGGERED || 0)

// 详情与数据帧追溯对话框
const detailDialogVisible = ref(false)
const currentRecord = ref<any>(null)

function showDetail(row: any) {
  currentRecord.value = row
  detailDialogVisible.value = true
}

async function load() {
  loading.value = true
  try {
    const [result, summary] = await Promise.all([
      realApi.getAlertRecords({ level: level.value || undefined, status: status.value || undefined, page: page.value, size }),
      realApi.getAlertStats()
    ])
    records.value = result?.content || []
    total.value = result?.totalElements || 0
    stats.value = summary || {}
  } catch (e: any) {
    ElMessage.error('加载告警失败: ' + (e?.message || '未知错误'))
  } finally { loading.value = false }
}

async function acknowledge(row: any) {
  try {
    await realApi.acknowledgeAlert(row.id)
    ElMessage.success('告警已确认')
    load()
  } catch (e: any) {
    ElMessage.error('确认告警失败: ' + (e?.message || '未知错误'))
  }
}

async function resolve(row: any) {
  try {
    await realApi.resolveAlert(row.id)
    ElMessage.success('告警已解决')
    load()
  } catch (e: any) {
    ElMessage.error('解决告警失败: ' + (e?.message || '未知错误'))
  }
}

function levelType(value: string) { return value === 'CRITICAL' ? 'danger' : value === 'WARNING' ? 'warning' : 'info' }
function levelLabel(value: string) { return value === 'CRITICAL' ? '严重' : value === 'WARNING' ? '警告' : '提示' }
function formatTime(value: string) { return value ? new Date(value).toLocaleString() : '--' }
function changePage(value: number) { page.value = value - 1; load() }
onMounted(load)
</script>

<template>
  <div class="alert-center">
    <section class="page-header">
      <div>
        <span>ALARM CENTER</span>
        <h1>报警中心</h1>
        <p>集中处理矿洞甲烷超限、设备异常与流式数据告警（按时间倒序排列）。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </section>

    <section class="metric-grid">
      <article><Bell /><span>待处理</span><strong>{{ unhandled }}</strong></article>
      <article><WarningFilled /><span>当前页告警</span><strong>{{ records.length }}</strong></article>
      <article><CircleCheck /><span>记录总数</span><strong>{{ total }}</strong></article>
    </section>

    <section class="panel">
      <div class="toolbar">
        <el-select v-model="status" clearable placeholder="处理状态" @change="page = 0; load()">
          <el-option label="待处理" value="TRIGGERED" />
          <el-option label="已确认" value="ACKNOWLEDGED" />
          <el-option label="已解决" value="RESOLVED" />
        </el-select>
        <el-select v-model="level" clearable placeholder="告警等级" @change="page = 0; load()">
          <el-option label="严重" value="CRITICAL" />
          <el-option label="警告" value="WARNING" />
          <el-option label="提示" value="INFO" />
        </el-select>
      </div>

      <el-table :data="records" v-loading="loading" empty-text="暂无告警记录">
        <el-table-column label="等级" width="90">
          <template #default="{ row }">
            <el-tag :type="levelType(row.level)" size="small">{{ levelLabel(row.level) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deviceName" label="设备" min-width="140">
          <template #default="{ row }">{{ row.deviceName || row.deviceId || '--' }}</template>
        </el-table-column>
        <el-table-column prop="ruleName" label="告警内容" min-width="220">
          <template #default="{ row }">{{ row.title || row.ruleName || '--' }}</template>
        </el-table-column>
        <el-table-column label="浓度 / 阈值" min-width="140">
          <template #default="{ row }">
            <span v-if="row.sensorValue != null" :class="{ 'danger-text': row.sensorValue >= (row.thresholdValue || 0) }">
              <strong>{{ row.sensorValue }}</strong> / {{ row.thresholdValue }} ppm
            </span>
            <span v-else>--</span>
          </template>
        </el-table-column>
        <el-table-column label="触发时间" min-width="165">
          <template #default="{ row }">{{ formatTime(row.triggeredAt) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'TRIGGERED' ? 'danger' : row.status === 'ACKNOWLEDGED' ? 'warning' : 'success'">
              {{ row.status === 'TRIGGERED' ? '待处理' : row.status === 'ACKNOWLEDGED' ? '已确认' : '已解决' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="info" :icon="Document" @click="showDetail(row)">溯源</el-button>
            <el-button v-if="row.status === 'TRIGGERED'" link type="primary" @click="acknowledge(row)">确认</el-button>
            <el-button v-if="row.status !== 'RESOLVED'" link type="success" @click="resolve(row)">解决</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > size"
        class="pagination"
        layout="prev, pager, next"
        :current-page="page + 1"
        :page-size="size"
        :total="total"
        @current-change="changePage"
      />
    </section>

    <!-- 数据帧追溯与告警详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="告警追溯与数据帧详情" width="600px">
      <div v-if="currentRecord" class="detail-container">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="设备名称">{{ currentRecord.deviceName || currentRecord.deviceId }}</el-descriptions-item>
          <el-descriptions-item label="设备ID">{{ currentRecord.deviceId }}</el-descriptions-item>
          <el-descriptions-item label="告警等级">
            <el-tag :type="levelType(currentRecord.level)" size="small">{{ levelLabel(currentRecord.level) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">{{ currentRecord.status }}</el-descriptions-item>
          <el-descriptions-item label="传感器类型">{{ currentRecord.sensorType || 'methane' }}</el-descriptions-item>
          <el-descriptions-item label="触发数值">{{ currentRecord.sensorValue != null ? `${currentRecord.sensorValue} ppm` : '--' }}</el-descriptions-item>
          <el-descriptions-item label="报警阈值">{{ currentRecord.thresholdValue != null ? `${currentRecord.thresholdValue} ppm (1.0%)` : '--' }}</el-descriptions-item>
          <el-descriptions-item label="触发时间">{{ formatTime(currentRecord.triggeredAt) }}</el-descriptions-item>
        </el-descriptions>

        <div class="raw-frame-box">
          <h4>触发数据帧原文（溯源记录）</h4>
          <pre>{{ currentRecord.rawFrame || currentRecord.detail || '无原始数据帧记录' }}</pre>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.alert-center { max-width: 1440px; margin: 0 auto; padding-bottom: 24px; }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 24px; }
.page-header span { color: var(--color-cyan); font: 10px/1.2 'Roboto Mono', monospace; letter-spacing: .12em; }
.page-header h1 { margin: 7px 0 0; color: var(--text-primary); font-size: 30px; }
.page-header p { margin: 8px 0 0; color: var(--text-secondary); font-size: 13px; }
.page-header :deep(.el-button) { border-radius: 6px; }
.metric-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin-bottom: 12px; }
.metric-grid article, .panel { background: var(--bg-card); border: 1px solid var(--border-color); border-radius: 8px; }
.metric-grid article { display: grid; grid-template-columns: 34px 1fr; gap: 3px 12px; padding: 16px; color: var(--text-secondary); }
.metric-grid article svg { grid-row: span 2; box-sizing: content-box; width: 18px; height: 18px; padding: 8px; border-radius: 6px; color: var(--color-primary); background: var(--color-primary-soft); }
.metric-grid article strong { color: var(--text-primary); font: 700 26px/1 'Roboto Mono', monospace; }
.panel { padding: 16px; }
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; }
.toolbar :deep(.el-select) { width: 150px; }
.pagination { justify-content: flex-end; margin-top: 16px; }
.danger-text { color: #f56c6c; font-weight: bold; }
.raw-frame-box { margin-top: 16px; background: rgba(0, 0, 0, 0.2); border: 1px solid var(--border-color); border-radius: 6px; padding: 12px; }
.raw-frame-box h4 { margin: 0 0 8px; font-size: 13px; color: var(--text-primary); }
.raw-frame-box pre { margin: 0; font-family: 'Roboto Mono', monospace; font-size: 12px; color: var(--color-cyan); white-space: pre-wrap; word-break: break-all; max-height: 180px; overflow-y: auto; }
@media (max-width: 700px) { .page-header { flex-direction: column; } .metric-grid { grid-template-columns: 1fr; } .toolbar { flex-wrap: wrap; } .panel { overflow-x: auto; } .panel :deep(.el-table) { min-width: 760px; } }
</style>
