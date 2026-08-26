<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { useDeviceStore } from '../../stores/device'
import { ElMessage } from 'element-plus'
import { User, Message, Key, Camera, Edit, Cpu, DataLine } from '@element-plus/icons-vue'

const authStore = useAuthStore()
const deviceStore = useDeviceStore()

const avatarUrl = ref(authStore.user?.avatar || '')
const activeTab = ref('profile')
const uploading = ref(false)

const profileForm = ref({
  username: authStore.user?.username || '',
  email: authStore.user?.email || '',
  phone: authStore.user?.phone || ''
})

const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

function triggerUpload() {
  const input = document.getElementById('avatar-input') as HTMLInputElement
  input?.click()
}

function handleAvatarChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error('头像大小不能超过 2MB')
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    avatarUrl.value = reader.result as string
    ElMessage.success('头像已更新')
  }
  reader.readAsDataURL(file)
}

function updateProfile() {
  ElMessage.success('个人信息更新成功')
}

function changePassword() {
  if (!passwordForm.value.oldPassword) { ElMessage.error('请输入原密码'); return }
  if (passwordForm.value.newPassword.length < 6) { ElMessage.error('新密码至少 6 位'); return }
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    ElMessage.error('两次输入的密码不一致')
    return
  }
  ElMessage.success('密码修改成功')
  passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
}

onMounted(() => {
  deviceStore.fetchDevices()
})
</script>

<template>
  <div class="user-profile">
    <!-- 顶部头像卡片 -->
    <div class="profile-hero">
      <div class="avatar-section" @click="triggerUpload">
        <el-badge :value="null" class="avatar-badge">
          <div class="avatar-wrapper">
            <el-avatar :size="88" :src="avatarUrl || undefined">
              <el-icon :size="40"><User /></el-icon>
            </el-avatar>
            <div class="avatar-overlay">
              <el-icon :size="20"><Camera /></el-icon>
              <span>更换头像</span>
            </div>
          </div>
        </el-badge>
        <input id="avatar-input" type="file" accept="image/*" hidden @change="handleAvatarChange">
        <h2 class="hero-name">{{ authStore.user?.username }}</h2>
        <p class="hero-role">{{ authStore.user?.role === 'admin' ? '管理员' : '普通用户' }}</p>
        <p class="hero-date">注册于 {{ authStore.user?.createdAt ? new Date(authStore.user.createdAt).toLocaleDateString() : '-' }}</p>
      </div>

      <!-- 快捷统计 -->
      <div class="hero-stats">
        <div class="hero-stat">
          <el-icon :size="22" color="var(--accent)"><Cpu /></el-icon>
          <div class="hero-stat-num">{{ deviceStore.totalCount }}</div>
          <div class="hero-stat-label">设备总数</div>
        </div>
        <div class="hero-stat">
          <el-icon :size="22" color="var(--success)"><DataLine /></el-icon>
          <div class="hero-stat-num">{{ deviceStore.onlineCount }}</div>
          <div class="hero-stat-label">在线设备</div>
        </div>
      </div>
    </div>

    <!-- 信息编辑 -->
    <el-card class="info-card" shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="基本信息" name="profile">
          <el-form :model="profileForm" label-width="80px" class="profile-form">
            <el-form-item label="用户名">
              <el-input v-model="profileForm.username" disabled>
                <template #prefix><el-icon><User /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="profileForm.email">
                <template #prefix><el-icon><Message /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="profileForm.phone" placeholder="绑定手机号">
                <template #prefix><el-icon><Message /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Edit" @click="updateProfile">保存修改</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="修改密码" name="password">
          <el-form :model="passwordForm" label-width="80px" class="profile-form">
            <el-form-item label="原密码">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password>
                <template #prefix><el-icon><Key /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="passwordForm.newPassword" type="password" show-password>
                <template #prefix><el-icon><Key /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item label="确认密码">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password>
                <template #prefix><el-icon><Key /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="changePassword">修改密码</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped>
.user-profile {
  max-width: 680px;
  margin: 0 auto;
}

/* Hero */
.profile-hero {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 14px;
  padding: 36px 32px 24px;
  margin-bottom: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 40px;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
}

.avatar-wrapper {
  position: relative;
  border-radius: 50%;
  overflow: hidden;
  transition: transform 0.2s;
}

.avatar-wrapper:hover {
  transform: scale(1.04);
}

.avatar-overlay {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: rgba(0,0,0,0.45);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: #fff;
  font-size: 11px;
  opacity: 0;
  transition: opacity 0.2s;
}

.avatar-wrapper:hover .avatar-overlay {
  opacity: 1;
}

.hero-name {
  margin: 12px 0 4px;
  font-size: 18px;
  color: var(--text-primary);
  cursor: default;
}

.hero-role {
  margin: 0;
  font-size: 13px;
  color: var(--text-muted);
}

.hero-date {
  margin: 6px 0 0;
  font-size: 11px;
  color: var(--text-muted);
  opacity: 0.6;
}

.hero-stats {
  display: flex;
  gap: 32px;
  flex-shrink: 0;
}

.hero-stat {
  text-align: center;
  padding: 16px 24px;
  background: var(--bg-hover);
  border-radius: 12px;
  min-width: 90px;
}

.hero-stat-num {
  font-size: 26px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 4px 0;
}

.hero-stat-label {
  font-size: 12px;
  color: var(--text-muted);
}

/* Info card */
.info-card {
  border: 1px solid var(--border-color);
}

.profile-form {
  max-width: 480px;
  padding-top: 8px;
}
</style>
