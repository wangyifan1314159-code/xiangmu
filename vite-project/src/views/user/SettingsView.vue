<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Bell, Clock } from '@element-plus/icons-vue'

const settings = ref({
  notifications: {
    email: true,
    sms: false,
    push: true,
    alertThreshold: 80
  },
  data: {
    autoRefresh: true,
    refreshInterval: 5,
    dataRetention: 30
  }
})

const saving = ref(false)

function saveSettings() {
  saving.value = true
  setTimeout(() => {
    saving.value = false
    ElMessage.success('设置已保存')
  }, 500)
}

function resetDefaults() {
  settings.value = {
    notifications: { email: true, sms: false, push: true, alertThreshold: 80 },
    data: { autoRefresh: true, refreshInterval: 5, dataRetention: 30 }
  }
  ElMessage.info('已恢复默认设置')
}
</script>

<template>
  <div class="settings-page">
    <div class="page-header">
      <h2>系统设置</h2>
      <span class="page-desc">配置平台运行参数</span>
    </div>

    <div class="settings-body">
      <el-card>
        <template #header>
          <div class="card-header">
            <el-icon :size="18"><Bell /></el-icon>
            <span>告警设置</span>
          </div>
        </template>
        <el-form :model="settings.notifications" label-width="120px" label-position="left">
          <el-form-item label="邮件通知">
            <el-switch v-model="settings.notifications.email" />
          </el-form-item>
          <el-form-item label="短信通知">
            <el-switch v-model="settings.notifications.sms" />
          </el-form-item>
          <el-form-item label="推送通知">
            <el-switch v-model="settings.notifications.push" />
          </el-form-item>
          <el-form-item label="告警阈值">
            <div class="slider-wrap">
              <el-slider v-model="settings.notifications.alertThreshold" :max="100" show-input size="small" />
            </div>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card>
        <template #header>
          <div class="card-header">
            <el-icon :size="18"><Clock /></el-icon>
            <span>数据设置</span>
          </div>
        </template>
        <el-form :model="settings.data" label-width="120px" label-position="left">
          <el-form-item label="自动刷新">
            <el-switch v-model="settings.data.autoRefresh" />
          </el-form-item>
          <el-form-item label="刷新间隔">
            <div class="inline-control">
              <el-input-number v-model="settings.data.refreshInterval" :min="1" :max="60" size="small" />
              <span class="inline-unit">秒</span>
            </div>
          </el-form-item>
          <el-form-item label="数据保留">
            <div class="inline-control">
              <el-input-number v-model="settings.data.dataRetention" :min="7" :max="365" size="small" />
              <span class="inline-unit">天</span>
            </div>
          </el-form-item>
        </el-form>
      </el-card>

      <div class="action-bar">
        <el-button @click="resetDefaults">恢复默认</el-button>
        <el-button type="primary" :loading="saving" @click="saveSettings">保存设置</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.settings-page {
  max-width: 780px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  color: var(--text-primary);
}

.page-desc {
  font-size: 13px;
  color: var(--text-muted);
}

.settings-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.card-header .el-icon {
  color: var(--accent);
}

.inline-control {
  display: flex;
  align-items: center;
  gap: 8px;
}

.inline-unit {
  font-size: 13px;
  color: var(--text-muted);
}

.slider-wrap {
  width: 100%;
  max-width: 360px;
}

.action-bar {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 8px;
}
</style>
