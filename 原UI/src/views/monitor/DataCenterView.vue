<script setup lang="ts">
defineOptions({ name: 'DataCenter' })
import { ref, onMounted, computed } from 'vue'
import { useDeviceStore } from '../../stores/device'
import { ElMessage } from 'element-plus'
import { Search, Download } from '@element-plus/icons-vue'

const deviceStore = useDeviceStore()

const searchForm = ref({
  deviceId: '',
  sensorId: '',
  startTime: '',
  endTime: ''
})

const dataList = ref<any[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)

// 快捷时间范围
const timeRange = ref('')

function formatDateTime(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function setTimeRange(range: string) {
  timeRange.value = range
  const now = new Date()
  const end = formatDateTime(now)
  let start: string
  switch (range) {
    case '1h':  start = formatDateTime(new Date(now.getTime() - 60 * 60 * 1000)); break
    case '6h':  start = formatDateTime(new Date(now.getTime() - 6 * 60 * 60 * 1000)); break
    case '24h': start = formatDateTime(new Date(now.getTime() - 24 * 60 * 60 * 1000)); break
    case '7d':  start = formatDateTime(new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000)); break
    default:    start = ''; break
  }
  searchForm.value.startTime = start
  searchForm.value.endTime = end
}

const filteredData = computed(() => {
  // 后端已按条件过滤并分页，前端不再重复过滤
  return dataList.value
})

const paginatedData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredData.value.slice(start, start + pageSize.value)
})

async function fetchData() {
  loading.value = true
  try {
    currentPage.value = 1
    dataList.value = await deviceStore.fetchDeviceData(
      searchForm.value.deviceId || undefined,
      searchForm.value.sensorId || undefined,
      200,
      searchForm.value.startTime || undefined,
      searchForm.value.endTime || undefined
    )
    if (dataList.value.length === 0) {
      ElMessage.info('未查询到数据，请确认设备已上报数据或调整时间范围')
    } else {
      ElMessage.success(`查询到 ${dataList.value.length} 条数据`)
    }
  } catch (e: any) {
    ElMessage.error('查询失败: ' + (e.message || '未知错误'))
    dataList.value = []
  } finally {
    loading.value = false
  }
}

function exportData() {
  if (filteredData.value.length === 0) {
    ElMessage.warning('没有可导出的数据')
    return
  }
  const csvContent = [
    ['设备ID', '传感器ID', '数值', '时间'].join(','),
    ...filteredData.value.map(d => [d.deviceId, d.sensorId, d.value, d.timestamp].join(','))
  ].join('\n')

  const blob = new Blob(['﻿' + csvContent], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `device_data_${new Date().toISOString().slice(0, 10)}.csv`
  link.click()
  ElMessage.success('数据导出成功')
}

const actuatorTypeSet = new Set(['switch', 'relay', 'motor', 'valve', 'buzzer', 'led', 'fan', 'pump'])

function getDeviceName(deviceId: string) {
  const device = deviceStore.devices.find(d => d.id === deviceId)
  return device?.name || deviceId
}

function getSensorName(deviceId: string, sensorId: string) {
  const device = deviceStore.devices.find(d => d.id === deviceId)
  const allSensors = [...((device as any)?.sensors || []), ...((device as any)?.actuators || [])]
  const sensor = allSensors.find((s: any) => s.id === sensorId)
  return sensor?.name || sensorId
}

function isActuator(deviceId: string, sensorId: string) {
  const device = deviceStore.devices.find(d => d.id === deviceId)
  const allSensors = [...((device as any)?.sensors || []), ...((device as any)?.actuators || [])]
  const sensor = allSensors.find((s: any) => s.id === sensorId)
  return sensor ? actuatorTypeSet.has(sensor.type?.toLowerCase()) : false
}

function formatValue(row: any) {
  if (row.value == null) return '-'
  if (isActuator(row.deviceId, row.sensorId)) {
    return Number(row.value) === 1 ? 'ON' : 'OFF'
  }
  return Number(row.value).toFixed(2)
}

onMounted(() => {
  deviceStore.fetchDevices()
  fetchData()
})
</script>

<template>
  <div class="data-center">
    <div class="page-header">
      <h2>数据中心</h2>
      <p>查询和管理设备历史数据</p>
    </div>

    <el-card>
      <div class="search-bar">
        <el-select v-model="searchForm.deviceId" placeholder="选择设备" clearable style="width: 200px">
          <el-option
            v-for="device in deviceStore.devices"
            :key="device.id"
            :label="device.name"
            :value="device.id"
          />
        </el-select>

        <el-date-picker
          v-model="searchForm.startTime"
          type="datetime"
          placeholder="开始时间"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DDTHH:mm:ss"
          style="width: 200px"
        />
        <el-date-picker
          v-model="searchForm.endTime"
          type="datetime"
          placeholder="结束时间"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DDTHH:mm:ss"
          style="width: 200px"
        />

        <el-button-group>
          <el-button :type="timeRange === '1h' ? 'primary' : ''" @click="setTimeRange('1h')">1小时</el-button>
          <el-button :type="timeRange === '6h' ? 'primary' : ''" @click="setTimeRange('6h')">6小时</el-button>
          <el-button :type="timeRange === '24h' ? 'primary' : ''" @click="setTimeRange('24h')">24小时</el-button>
          <el-button :type="timeRange === '7d' ? 'primary' : ''" @click="setTimeRange('7d')">7天</el-button>
        </el-button-group>

        <el-button type="primary" :icon="Search" @click="fetchData">查询历史数据</el-button>
        <el-button :icon="Download" @click="exportData">导出数据</el-button>
      </div>

      <el-table :data="paginatedData" v-loading="loading" style="width: 100%">
        <el-table-column type="index" width="50" />
        <el-table-column label="设备名称" min-width="150">
          <template #default="{ row }">
            {{ getDeviceName(row.deviceId) }}
          </template>
        </el-table-column>
        <el-table-column label="传感器" min-width="120">
          <template #default="{ row }">
            {{ getSensorName(row.deviceId, row.sensorId) }}
          </template>
        </el-table-column>
        <el-table-column prop="value" label="数值" min-width="100">
          <template #default="{ row }">
            <span style="font-weight: 500;">{{ formatValue(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="timestamp" label="时间" min-width="160">
          <template #default="{ row }">
            {{ row.timestamp ? new Date(row.timestamp).toLocaleString() : '-' }}
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          :current-page="currentPage"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="filteredData.length"
          layout="total, sizes, prev, pager, next"
          @current-change="currentPage = $event"
          @size-change="pageSize = $event"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 8px;
  font-size: 22px;
  color: var(--text-primary);
}

.page-header p {
  margin: 0;
  color: var(--text-muted);
  font-size: 14px;
}

.search-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 20px;
  align-items: center;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
