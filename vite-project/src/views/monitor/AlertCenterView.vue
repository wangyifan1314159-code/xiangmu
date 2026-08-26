<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Bell, CircleCheck, Refresh, WarningFilled } from '@element-plus/icons-vue'
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
  } finally { loading.value = false }
}
async function acknowledge(row: any) { await realApi.acknowledgeAlert(row.id); ElMessage.success('告警已确认'); load() }
async function resolve(row: any) { await realApi.resolveAlert(row.id); ElMessage.success('告警已解决'); load() }
function levelType(value: string) { return value === 'CRITICAL' ? 'danger' : value === 'WARNING' ? 'warning' : 'info' }
function levelLabel(value: string) { return value === 'CRITICAL' ? '严重' : value === 'WARNING' ? '警告' : '提示' }
function formatTime(value: string) { return value ? new Date(value).toLocaleString() : '--' }
function changePage(value: number) { page.value = value - 1; load() }
onMounted(load)
</script>

<template>
  <div class="alert-center">
    <section class="page-header"><div><span>ALARM CENTER</span><h1>报警中心</h1><p>集中处理来自设备与数据规则的真实告警记录。</p></div><el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button></section>
    <section class="metric-grid"><article><Bell /><span>待处理</span><strong>{{ unhandled }}</strong></article><article><WarningFilled /><span>当前页告警</span><strong>{{ records.length }}</strong></article><article><CircleCheck /><span>记录总数</span><strong>{{ total }}</strong></article></section>
    <section class="panel"><div class="toolbar"><el-select v-model="status" clearable placeholder="处理状态" @change="page = 0; load()"><el-option label="待处理" value="TRIGGERED" /><el-option label="已确认" value="ACKNOWLEDGED" /><el-option label="已解决" value="RESOLVED" /></el-select><el-select v-model="level" clearable placeholder="告警等级" @change="page = 0; load()"><el-option label="严重" value="CRITICAL" /><el-option label="警告" value="WARNING" /><el-option label="提示" value="INFO" /></el-select></div><el-table :data="records" v-loading="loading" empty-text="暂无告警记录"><el-table-column label="等级" width="90"><template #default="{ row }"><el-tag :type="levelType(row.level)" size="small">{{ levelLabel(row.level) }}</el-tag></template></el-table-column><el-table-column prop="deviceName" label="设备" min-width="140"><template #default="{ row }">{{ row.deviceName || row.deviceId || '--' }}</template></el-table-column><el-table-column prop="ruleName" label="告警内容" min-width="190"><template #default="{ row }">{{ row.ruleName || row.title || '--' }}</template></el-table-column><el-table-column label="时间" min-width="165"><template #default="{ row }">{{ formatTime(row.triggeredAt) }}</template></el-table-column><el-table-column prop="status" label="状态" width="100" /><el-table-column label="操作" width="150" fixed="right"><template #default="{ row }"><el-button v-if="row.status === 'TRIGGERED'" link type="primary" @click="acknowledge(row)">确认</el-button><el-button v-if="row.status !== 'RESOLVED'" link type="success" @click="resolve(row)">解决</el-button></template></el-table-column></el-table><el-pagination v-if="total > size" class="pagination" layout="prev, pager, next" :current-page="page + 1" :page-size="size" :total="total" @current-change="changePage" /></section>
  </div>
</template>

<style scoped>
.alert-center { max-width: 1440px; margin: 0 auto; padding-bottom: 24px; }.page-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 24px; }.page-header span { color: var(--color-cyan); font: 10px/1.2 'Roboto Mono', monospace; letter-spacing: .12em; }.page-header h1 { margin: 7px 0 0; color: var(--text-primary); font-size: 30px; }.page-header p { margin: 8px 0 0; color: var(--text-secondary); font-size: 13px; }.page-header :deep(.el-button) { border-radius: 6px; }.metric-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin-bottom: 12px; }.metric-grid article, .panel { background: var(--bg-card); border: 1px solid var(--border-color); border-radius: 8px; }.metric-grid article { display: grid; grid-template-columns: 34px 1fr; gap: 3px 12px; padding: 16px; color: var(--text-secondary); }.metric-grid article svg { grid-row: span 2; box-sizing: content-box; width: 18px; height: 18px; padding: 8px; border-radius: 6px; color: var(--color-primary); background: var(--color-primary-soft); }.metric-grid article strong { color: var(--text-primary); font: 700 26px/1 'Roboto Mono', monospace; }.panel { padding: 16px; }.toolbar { display: flex; gap: 10px; margin-bottom: 14px; }.toolbar :deep(.el-select) { width: 150px; }.pagination { justify-content: flex-end; margin-top: 16px; }@media (max-width: 700px) { .page-header { flex-direction: column; }.metric-grid { grid-template-columns: 1fr; }.toolbar { flex-wrap: wrap; }.panel { overflow-x: auto; }.panel :deep(.el-table) { min-width: 760px; } }
</style>
