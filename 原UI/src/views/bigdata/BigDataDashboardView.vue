<template>
  <div class="bigdata-dashboard">
    <!-- 顶部数据概览卡片 -->
    <el-row :gutter="16" class="metric-cards">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card pulse-border">
          <div class="stat-title">实时数据接入 (QPS)</div>
          <div class="stat-value text-primary">{{ currentQps.toLocaleString() }} <span class="unit">pts/s</span></div>
          <div class="stat-footer">Flink 流处理延迟: <strong class="text-success">{{ currentLatency }} ms</strong></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-title">湖仓累计存储容量</div>
          <div class="stat-value text-success">{{ currentStorage }}</div>
          <div class="stat-footer">存储引擎: <strong>Iceberg + MinIO</strong></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-title">在线活跃设备数</div>
          <div class="stat-value text-warning">{{ activeDevices.toLocaleString() }} <span class="unit">台</span></div>
          <div class="stat-footer">高并发分区数: <strong>32 Partitions</strong></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-title">数据质量清洗合格率</div>
          <div class="stat-value text-info">{{ cleanRate }}</div>
          <div class="stat-footer">死值与物理极值过滤生效中</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 中间：实时趋势与设备健康度 (PHM) -->
    <el-row :gutter="16" style="margin-top: 16px;">
      <el-col :xs="24" :lg="16">
        <el-card shadow="hover" class="chart-card">
          <template #header>
            <div class="card-header">
              <span>🌊 实时 1 分钟窗口流式聚合趋势 (Doris & Flink)</span>
              <div class="header-tags">
                <el-tag type="success" effect="dark" class="animate-pulse">● 实时流已连接</el-tag>
                <el-tag type="info" size="small" style="margin-left: 8px;">刷新周期: 3s</el-tag>
              </div>
            </div>
          </template>
          <div ref="chartRef" style="height: 340px; width: 100%;"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card shadow="hover" class="chart-card">
          <template #header>
            <div class="card-header">
              <span>🩺 设备健康度与预测性维护 (PHM)</span>
              <el-tag type="warning" size="small">AI 预测</el-tag>
            </div>
          </template>
          <el-table :data="healthScores" size="small" style="width: 100%">
            <el-table-column prop="deviceId" label="设备 ID" width="120" />
            <el-table-column label="健康评分" width="110">
              <template #default="{ row }">
                <el-progress
                  :percentage="row.healthScore"
                  :status="row.healthScore > 85 ? 'success' : row.healthScore > 60 ? 'warning' : 'exception'"
                />
              </template>
            </el-table-column>
            <el-table-column prop="rulDays" label="剩余寿命" width="80">
              <template #default="{ row }">{{ row.rulDays }} 天</template>
            </el-table-column>
            <el-table-column prop="status" label="状态">
              <template #default="{ row }">
                <el-tag :type="row.status === 'HEALTHY' ? 'success' : row.status === 'ATTENTION' ? 'warning' : 'danger'" size="small">
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 底部：大数据湖仓分层与流处理任务状态 -->
    <el-row :gutter="16" style="margin-top: 16px;">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>🏗️ 湖仓一体分层数据流向拓扑 (OneData Pipeline)</span>
              <el-tag type="primary" size="small">实时拓扑正常</el-tag>
            </div>
          </template>
          <el-steps :active="4" align-center finish-status="success">
            <el-step title="接入层 (Ingestion)" description="EMQX -> Kafka 32 Partitions (85,420 QPS)" />
            <el-step title="流计算层 (Stream Processing)" description="Flink 1.18 ETL & CEP (延迟 42ms)" />
            <el-step title="湖仓层 (Lakehouse & OLAP)" description="Apache Doris DWS + Iceberg 历史湖" />
            <el-step title="数据服务层 (Data API)" description="统一指标中台 & 秒级交互式大屏" />
          </el-steps>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'

const currentQps = ref(85420)
const currentLatency = ref(42)
const currentStorage = ref('4.2 TB')
const activeDevices = ref(12500)
const cleanRate = ref('99.85%')

const healthScores = ref([
  { deviceId: 'DEV-LIGHT-001', healthScore: 96, rulDays: 450, status: 'HEALTHY' },
  { deviceId: 'DEV-TEMP-002', healthScore: 78, rulDays: 85, status: 'ATTENTION' },
  { deviceId: 'DEV-PRESS-003', healthScore: 92, rulDays: 320, status: 'HEALTHY' },
  { deviceId: 'DEV-VIBR-004', healthScore: 54, rulDays: 14, status: 'CRITICAL' }
])

const chartRef = ref<HTMLElement | null>(null)
let chartInstance: echarts.ECharts | null = null
let refreshTimer: number | null = null

const hours = Array.from({ length: 24 }, (_, i) => `${i}:00`)
const avgValues = ref(hours.map((_, i) => Math.round((25 + Math.sin(i / 2) * 8 + Math.random() * 2) * 10) / 10))
const maxValues = ref(avgValues.value.map(v => Math.round((v + 5 + Math.random() * 3) * 10) / 10))
const minValues = ref(avgValues.value.map(v => Math.round((v - 4 - Math.random() * 2) * 10) / 10))

const initChart = () => {
  if (!chartRef.value) return
  chartInstance = echarts.init(chartRef.value)
  updateChartOption()
}

const updateChartOption = () => {
  if (!chartInstance) return
  const option: echarts.EChartsOption = {
    tooltip: { trigger: 'axis' },
    legend: { data: ['平均值 (Avg)', '最高值 (Max)', '最低值 (Min)'] },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: hours },
    yAxis: { type: 'value', name: '指标读数' },
    animationDuration: 800,
    series: [
      {
        name: '平均值 (Avg)',
        type: 'line',
        smooth: true,
        data: avgValues.value,
        itemStyle: { color: '#409EFF' },
        areaStyle: { opacity: 0.15 }
      },
      {
        name: '最高值 (Max)',
        type: 'line',
        smooth: true,
        data: maxValues.value,
        itemStyle: { color: '#F56C6C' }
      },
      {
        name: '最低值 (Min)',
        type: 'line',
        smooth: true,
        data: minValues.value,
        itemStyle: { color: '#67C23A' }
      }
    ]
  }
  chartInstance.setOption(option)
}

// 比赛演示自动微波动动画，确保大屏始终动感十足
const startDynamicSimulation = () => {
  refreshTimer = window.setInterval(() => {
    // 波动 QPS
    currentQps.value = Math.round(85000 + Math.random() * 1200 - 600)
    currentLatency.value = Math.round(40 + Math.random() * 6 - 3)

    // 滑动末尾点
    const lastIdx = avgValues.value.length - 1
    const newAvg = Math.round((avgValues.value[lastIdx] + (Math.random() * 1.6 - 0.8)) * 10) / 10
    avgValues.value[lastIdx] = newAvg
    maxValues.value[lastIdx] = Math.round((newAvg + 5 + Math.random() * 2) * 10) / 10
    minValues.value[lastIdx] = Math.round((newAvg - 4 - Math.random() * 2) * 10) / 10

    updateChartOption()
  }, 3000)
}

const handleResize = () => chartInstance?.resize()

onMounted(() => {
  initChart()
  startDynamicSimulation()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
})
</script>

<style scoped>
.bigdata-dashboard {
  padding: 16px;
}
.stat-card {
  border-radius: 8px;
  transition: all 0.3s;
}
.stat-title {
  font-size: 14px;
  color: var(--el-text-color-secondary);
}
.stat-value {
  font-size: 26px;
  font-weight: bold;
  margin: 8px 0;
}
.unit {
  font-size: 14px;
  font-weight: normal;
  color: var(--el-text-color-secondary);
}
.stat-footer {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
.text-primary { color: var(--el-color-primary); }
.text-success { color: var(--el-color-success); }
.text-warning { color: var(--el-color-warning); }
.text-info { color: var(--el-color-info); }
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: bold;
}
.animate-pulse {
  animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
</style>