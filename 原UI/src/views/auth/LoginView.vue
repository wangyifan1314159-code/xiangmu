<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { User, Lock, Monitor, Cpu, Connection, TrendCharts, Promotion, ArrowRight, View, Hide, Iphone, Message } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()

// ---- Tab state ----
type LoginTab = 'password' | 'phone'
const activeTab = ref<LoginTab>('password')

// ---- Password login ----
const loginForm = ref({ username: '', password: '' })
const loading = ref(false)
const showPassword = ref(false)

const passwordRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '长度在 6 到 20 个字符', trigger: 'blur' }
  ]
}

// ---- Phone code login ----
const phoneForm = ref({ phone: '', code: '' })
const phoneLoading = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null

const phoneRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码为6位数字', trigger: 'blur' }
  ]
}

const countdownText = computed(() => {
  return countdown.value > 0 ? `${countdown.value}s 后重新获取` : '获取验证码'
})

// ---- Particles ----
const particles = ref<{ x: number; y: number; size: number; speed: number; opacity: number }[]>([])

const passwordFormRef = ref()
const phoneFormRef = ref()

function initParticles() {
  const arr = []
  for (let i = 0; i < 30; i++) {
    arr.push({
      x: Math.random() * 100,
      y: Math.random() * 100,
      size: 2 + Math.random() * 4,
      speed: 10 + Math.random() * 25,
      opacity: 0.3 + Math.random() * 0.7
    })
  }
  particles.value = arr
}

let animFrame = 0
function animateParticles() {
  particles.value.forEach(p => {
    p.y -= 0.05
    if (p.y < -5) { p.y = 105; p.x = Math.random() * 100 }
  })
  animFrame = requestAnimationFrame(animateParticles)
}

// ---- Password login handler ----
async function handleLogin() {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await authStore.login(loginForm.value.username, loginForm.value.password)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (error: any) {
    ElMessage.error(error.message || '登录失败')
  } finally {
    loading.value = false
  }
}

// ---- Phone code login handlers ----
function startCountdown() {
  countdown.value = 60
  countdownTimer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      if (countdownTimer) clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

async function handleSendCode() {
  try {
    await phoneFormRef.value?.validateField('phone')
  } catch {
    return
  }
  sendingCode.value = true
  try {
    await authStore.sendCode(phoneForm.value.phone)
    ElMessage.success('验证码已发送')
    startCountdown()
  } catch (error: any) {
    ElMessage.error(error.message || '发送失败')
  } finally {
    sendingCode.value = false
  }
}

async function handlePhoneLogin() {
  const valid = await phoneFormRef.value?.validate().catch(() => false)
  if (!valid) return
  phoneLoading.value = true
  try {
    await authStore.loginByPhone(phoneForm.value.phone, phoneForm.value.code)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (error: any) {
    ElMessage.error(error.message || '登录失败')
  } finally {
    phoneLoading.value = false
  }
}

function goRegister() {
  router.push('/register')
}

function switchTab(tab: LoginTab) {
  activeTab.value = tab
}

onMounted(() => {
  initParticles()
  animateParticles()
})

onUnmounted(() => {
  cancelAnimationFrame(animFrame)
  if (countdownTimer) clearInterval(countdownTimer)
})
</script>

<template>
  <div class="login-page">
    <!-- Animated background -->
    <div class="bg-canvas">
      <svg class="network-lines" viewBox="0 0 100 100" preserveAspectRatio="none">
        <line v-for="i in 12" :key="'l'+i"
          :x1="(i * 8) % 100" :y1="(i * 15) % 100"
          :x2="((i * 8) + 20) % 100" :y2="((i * 15) + 25) % 100"
          class="net-line" />
      </svg>

      <div
        v-for="(p, i) in particles" :key="i"
        class="particle"
        :style="{
          left: p.x + '%', top: p.y + '%',
          width: p.size + 'px', height: p.size + 'px',
          opacity: p.opacity,
          animationDuration: p.speed + 's'
        }"
      />

      <div class="glow-orb orb-1" />
      <div class="glow-orb orb-2" />
      <div class="glow-orb orb-3" />
    </div>

    <div class="login-container">
      <!-- Left: Brand Panel -->
      <div class="login-left">
        <div class="left-content">
          <div class="iot-visual">
            <div class="outer-ring">
              <div class="ring-node" v-for="i in 8" :key="i" :style="{ transform: `rotate(${i * 45}deg) translateY(-70px)` }" />
            </div>
            <div class="middle-ring">
              <div class="ring-node small" v-for="i in 6" :key="i" :style="{ transform: `rotate(${i * 60}deg) translateY(-50px)` }" />
            </div>
            <div class="core-icon">
              <el-icon :size="48"><Cpu /></el-icon>
            </div>
          </div>

          <h1 class="brand-title">
            <span class="title-iot">IoT</span>
            <span class="title-cloud">物联网云平台</span>
          </h1>
          <p class="brand-subtitle">
            <span class="typing-text">智能连接 · 万物互联</span>
          </p>

          <div class="feature-list">
            <div class="feature-item">
              <div class="feature-icon"><el-icon><Connection /></el-icon></div>
              <div class="feature-text">
                <span class="feature-title">设备互联</span>
                <span class="feature-desc">海量设备安全接入</span>
              </div>
            </div>
            <div class="feature-item">
              <div class="feature-icon"><el-icon><TrendCharts /></el-icon></div>
              <div class="feature-text">
                <span class="feature-title">实时监控</span>
                <span class="feature-desc">毫秒级数据采集</span>
              </div>
            </div>
            <div class="feature-item">
              <div class="feature-icon"><el-icon><Promotion /></el-icon></div>
              <div class="feature-text">
                <span class="feature-title">智能告警</span>
                <span class="feature-desc">AI 驱动预警分析</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Right: Login Form -->
      <div class="login-right">
        <div class="form-panel">
          <div class="form-header">
            <div class="form-avatar">
              <el-icon :size="28"><Monitor /></el-icon>
            </div>
            <h2>欢迎登录</h2>
            <p>登录您的 IoT 控制台</p>
          </div>

          <!-- Tab switch -->
          <div class="login-tabs">
            <button
              class="tab-btn"
              :class="{ active: activeTab === 'password' }"
              @click="switchTab('password')"
            >
              <el-icon :size="14"><Lock /></el-icon>
              密码登录
            </button>
            <button
              class="tab-btn"
              :class="{ active: activeTab === 'phone' }"
              @click="switchTab('phone')"
            >
              <el-icon :size="14"><Iphone /></el-icon>
              验证码登录
            </button>
          </div>

          <!-- Tab: Password Login -->
          <div v-show="activeTab === 'password'" class="tab-content">
            <el-form
              ref="passwordFormRef"
              :model="loginForm"
              :rules="passwordRules"
              class="login-form"
              @keyup.enter="handleLogin"
            >
              <el-form-item prop="username">
                <el-input
                  v-model="loginForm.username"
                  placeholder="请输入用户名"
                  :prefix-icon="User"
                  size="large"
                  class="custom-input"
                />
              </el-form-item>

              <el-form-item prop="password">
                <el-input
                  v-model="loginForm.password"
                  :type="showPassword ? 'text' : 'password'"
                  placeholder="请输入密码"
                  :prefix-icon="Lock"
                  size="large"
                  class="custom-input"
                >
                  <template #suffix>
                    <el-icon class="toggle-pwd" @click="showPassword = !showPassword">
                      <View v-if="showPassword" />
                      <Hide v-else />
                    </el-icon>
                  </template>
                </el-input>
              </el-form-item>

              <div class="form-options">
                <el-checkbox label="记住密码" />
                <el-button link type="primary">忘记密码？</el-button>
              </div>

              <el-form-item>
                <el-button
                  type="primary"
                  size="large"
                  class="login-btn"
                  :loading="loading"
                  @click="handleLogin"
                >
                  <span v-if="!loading">登 录</span>
                </el-button>
              </el-form-item>
            </el-form>
          </div>

          <!-- Tab: Phone Code Login -->
          <div v-show="activeTab === 'phone'" class="tab-content">
            <el-form
              ref="phoneFormRef"
              :model="phoneForm"
              :rules="phoneRules"
              class="login-form"
              @keyup.enter="handlePhoneLogin"
            >
              <el-form-item prop="phone">
                <el-input
                  v-model="phoneForm.phone"
                  placeholder="请输入手机号"
                  :prefix-icon="Iphone"
                  size="large"
                  class="custom-input"
                  maxlength="11"
                />
              </el-form-item>

              <el-form-item prop="code">
                <el-input
                  v-model="phoneForm.code"
                  placeholder="请输入验证码"
                  :prefix-icon="Message"
                  size="large"
                  class="custom-input"
                  maxlength="6"
                >
                  <template #append>
                    <el-button
                      class="send-code-btn"
                      :loading="sendingCode"
                      :disabled="countdown > 0"
                      @click="handleSendCode"
                    >
                      {{ countdownText }}
                    </el-button>
                  </template>
                </el-input>
              </el-form-item>

              <el-form-item>
                <el-button
                  type="primary"
                  size="large"
                  class="login-btn phone-login-btn"
                  :loading="phoneLoading"
                  @click="handlePhoneLogin"
                >
                  <span v-if="!phoneLoading">登 录</span>
                </el-button>
              </el-form-item>
            </el-form>
          </div>

          <div class="form-footer">
            <span>还没有账号？</span>
            <el-button link type="primary" class="register-link" @click="goRegister">
              立即注册
              <el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #060e1a;
  position: relative;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', sans-serif;
}

/* ========== Animated Background ========== */
.bg-canvas {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.network-lines {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 0.08;
}

.net-line {
  stroke: #00d4ff;
  stroke-width: 0.3;
  stroke-dasharray: 3 6;
  animation: lineGlow 4s ease-in-out infinite;
}

.net-line:nth-child(2n) { animation-delay: 1s; stroke: #7b61ff; }
.net-line:nth-child(3n) { animation-delay: 2s; stroke: #00d4ff; }
.net-line:nth-child(4n) { animation-delay: 3s; }

@keyframes lineGlow {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 1; stroke-width: 0.6; }
}

.particle {
  position: absolute;
  background: #00d4ff;
  border-radius: 50%;
  animation: floatUp linear infinite;
  box-shadow: 0 0 8px #00d4ff, 0 0 20px rgba(0, 212, 255, 0.4);
}

.particle:nth-child(odd) { background: #7b61ff; box-shadow: 0 0 8px #7b61ff, 0 0 20px rgba(123, 97, 255, 0.4); }
.particle:nth-child(3n) { background: #00e5a0; box-shadow: 0 0 8px #00e5a0, 0 0 20px rgba(0, 229, 160, 0.4); }

@keyframes floatUp {
  0% { transform: translateY(0) scale(1); opacity: 0; }
  10% { opacity: 1; }
  90% { opacity: 0.5; }
  100% { transform: translateY(-100vh) scale(0.3); opacity: 0; }
}

.glow-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  animation: orbPulse 8s ease-in-out infinite;
}

.orb-1 {
  width: 400px; height: 400px;
  background: rgba(0, 180, 255, 0.12);
  top: -100px; right: -100px;
  animation-delay: 0s;
}

.orb-2 {
  width: 300px; height: 300px;
  background: rgba(123, 97, 255, 0.10);
  bottom: -80px; left: -80px;
  animation-delay: 3s;
}

.orb-3 {
  width: 250px; height: 250px;
  background: rgba(0, 212, 255, 0.08);
  top: 50%; left: 40%;
  animation-delay: 5s;
}

@keyframes orbPulse {
  0%, 100% { transform: scale(1); opacity: 0.6; }
  50% { transform: scale(1.3); opacity: 1; }
}

/* ========== Container ========== */
.login-container {
  display: flex;
  width: 1000px;
  min-height: 580px;
  border-radius: 20px;
  overflow: hidden;
  box-shadow:
    0 0 0 1px rgba(255,255,255,0.05),
    0 30px 80px rgba(0,0,0,0.5),
    0 0 120px rgba(0, 180, 255, 0.08);
  z-index: 1;
  backdrop-filter: blur(10px);
}

/* ========== Left Panel ========== */
.login-left {
  flex: 1;
  background: linear-gradient(160deg, #0a1628 0%, #0f2440 40%, #132a4a 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 50px 40px;
  position: relative;
  overflow: hidden;
}

.login-left::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 30% 20%, rgba(0, 180, 255, 0.08) 0%, transparent 50%),
    radial-gradient(circle at 70% 80%, rgba(123, 97, 255, 0.06) 0%, transparent 50%);
}

.left-content {
  position: relative;
  z-index: 1;
  text-align: center;
  color: #fff;
}

/* IoT Visual */
.iot-visual {
  width: 180px;
  height: 180px;
  margin: 0 auto 30px;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.outer-ring {
  position: absolute;
  width: 100%;
  height: 100%;
  border: 1.5px solid rgba(0, 212, 255, 0.25);
  border-radius: 50%;
  animation: ringRotate 20s linear infinite;
}

.middle-ring {
  position: absolute;
  width: 75%;
  height: 75%;
  border: 1px solid rgba(0, 180, 255, 0.2);
  border-radius: 50%;
  animation: ringRotate 12s linear infinite reverse;
}

.ring-node {
  position: absolute;
  width: 8px; height: 8px;
  background: #00d4ff;
  border-radius: 50%;
  top: 50%; left: 50%;
  box-shadow: 0 0 8px #00d4ff, 0 0 16px rgba(0, 212, 255, 0.6);
  animation: nodeBlink 2s ease-in-out infinite;
}

.ring-node.small {
  width: 5px; height: 5px;
  box-shadow: 0 0 6px #7b61ff;
  background: #7b61ff;
}

.ring-node:nth-child(odd) { animation-delay: 0.5s; }
.ring-node:nth-child(2n) { animation-delay: 1s; }
.ring-node:nth-child(3n) { animation-delay: 1.5s; }

@keyframes ringRotate {
  to { transform: rotate(360deg); }
}

@keyframes nodeBlink {
  0%, 100% { opacity: 0.7; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.5); }
}

.core-icon {
  width: 70px; height: 70px;
  background: linear-gradient(135deg, rgba(0, 212, 255, 0.2), rgba(0, 136, 255, 0.3));
  border: 1.5px solid rgba(0, 212, 255, 0.4);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #00d4ff;
  animation: coreGlow 3s ease-in-out infinite;
  position: relative;
  z-index: 2;
}

@keyframes coreGlow {
  0%, 100% { box-shadow: 0 0 20px rgba(0, 212, 255, 0.3); }
  50% { box-shadow: 0 0 40px rgba(0, 212, 255, 0.6), 0 0 80px rgba(0, 180, 255, 0.2); }
}

/* Brand text */
.brand-title {
  margin-bottom: 12px;
  font-size: 0;
}

.title-iot {
  font-size: 40px;
  font-weight: 800;
  background: linear-gradient(135deg, #00d4ff 0%, #00a0ff 50%, #7b61ff 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: 2px;
}

.title-cloud {
  font-size: 22px;
  font-weight: 300;
  color: #94a3b8;
  margin-left: 8px;
  letter-spacing: 4px;
}

.brand-subtitle {
  font-size: 15px;
  color: #64748b;
  margin-bottom: 40px;
  letter-spacing: 6px;
}

.typing-text {
  display: inline-block;
  border-right: 2px solid #00d4ff;
  animation: typingAnim 4s steps(10) infinite;
  overflow: hidden;
  white-space: nowrap;
}

@keyframes typingAnim {
  0%, 20% { max-width: 10em; }
  50%, 70% { max-width: 10em; }
  80%, 100% { max-width: 0; }
}

/* Features */
.feature-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 0 10px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.05);
  border-radius: 12px;
  transition: all 0.3s ease;
  text-align: left;
}

.feature-item:hover {
  background: rgba(0, 212, 255, 0.06);
  border-color: rgba(0, 212, 255, 0.2);
  transform: translateX(4px);
}

.feature-icon {
  width: 40px; height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 212, 255, 0.1);
  border-radius: 10px;
  color: #00d4ff;
  font-size: 18px;
  flex-shrink: 0;
}

.feature-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.feature-title {
  font-size: 14px;
  font-weight: 600;
  color: #e2e8f0;
}

.feature-desc {
  font-size: 12px;
  color: #64748b;
}

/* ========== Right Panel ========== */
.login-right {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 25, 48, 0.95);
  backdrop-filter: blur(20px);
  padding: 40px;
  border-left: 1px solid rgba(255,255,255,0.05);
}

.form-panel {
  width: 100%;
  max-width: 380px;
}

.form-header {
  text-align: center;
  margin-bottom: 24px;
}

.form-avatar {
  width: 64px; height: 64px;
  margin: 0 auto 16px;
  background: linear-gradient(135deg, rgba(0, 212, 255, 0.15), rgba(123, 97, 255, 0.15));
  border: 1px solid rgba(0, 212, 255, 0.25);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #00d4ff;
}

.form-header h2 {
  font-size: 24px;
  font-weight: 700;
  color: #e2e8f0;
  margin: 0 0 6px;
}

.form-header p {
  font-size: 14px;
  color: #64748b;
  margin: 0;
}

/* ========== Login Tabs ========== */
.login-tabs {
  display: flex;
  gap: 0;
  margin-bottom: 24px;
  background: rgba(255,255,255,0.03);
  border-radius: 10px;
  padding: 4px;
}

.tab-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 0;
  font-size: 13px;
  font-weight: 500;
  color: #64748b;
  background: transparent;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.25s ease;
  font-family: inherit;
}

.tab-btn:hover {
  color: #94a3b8;
}

.tab-btn.active {
  background: rgba(0, 212, 255, 0.12);
  color: #00d4ff;
  box-shadow: 0 0 12px rgba(0, 212, 255, 0.1);
}

/* ========== Tab Content ========== */
.tab-content {
  animation: fadeSlideIn 0.3s ease;
}

@keyframes fadeSlideIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ========== Form ========== */
.login-form {
  margin-top: 8px;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.custom-input :deep(.el-input__wrapper) {
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 10px;
  box-shadow: none;
  transition: all 0.3s;
  padding: 4px 12px;
}

.custom-input :deep(.el-input__wrapper:hover) {
  border-color: rgba(0, 212, 255, 0.3);
}

.custom-input :deep(.el-input__wrapper.is-focus) {
  border-color: #00d4ff;
  box-shadow: 0 0 0 2px rgba(0, 212, 255, 0.15);
  background: rgba(255,255,255,0.06);
}

.custom-input :deep(.el-input__inner) {
  height: 44px;
  color: #e2e8f0;
  font-size: 14px;
}

.custom-input :deep(.el-input__inner::placeholder) {
  color: #475569;
}

.custom-input :deep(.el-input__prefix) {
  color: #475569;
}

.toggle-pwd {
  color: #475569;
  cursor: pointer;
  font-size: 16px;
}

.toggle-pwd:hover {
  color: #00d4ff;
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  font-size: 13px;
}

.form-options :deep(.el-checkbox__label) {
  color: #64748b;
  font-size: 13px;
}

.form-options :deep(.el-button) {
  font-size: 13px;
  color: #00d4ff;
}

/* ========== Send Code Button ========== */
.send-code-btn {
  height: 44px;
  font-size: 13px;
  font-weight: 500;
  color: #00d4ff;
  background: transparent;
  border: none;
  border-left: 1px solid rgba(255,255,255,0.08);
  border-radius: 0;
  padding: 0 16px;
  white-space: nowrap;
}

.send-code-btn:hover:not(:disabled) {
  color: #00e5ff;
  background: rgba(0, 212, 255, 0.06);
}

.send-code-btn.is-disabled,
.send-code-btn:disabled {
  color: #475569;
  cursor: not-allowed;
}

/* ========== Login Button ========== */
.login-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 4px;
  border-radius: 10px;
  background: linear-gradient(135deg, #00b4d8 0%, #0077b6 50%, #6c63ff 100%);
  border: none;
  position: relative;
  overflow: hidden;
  transition: all 0.3s;
}

.login-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(0, 180, 216, 0.4);
}

.login-btn::before {
  content: '';
  position: absolute;
  top: 0; left: -100%;
  width: 100%; height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.15), transparent);
  transition: left 0.5s;
}

.login-btn:hover::before {
  left: 100%;
}

.phone-login-btn {
  background: linear-gradient(135deg, #00c896 0%, #009688 50%, #6c63ff 100%);
}

.phone-login-btn:hover {
  box-shadow: 0 8px 25px rgba(0, 200, 150, 0.4);
}

/* ========== Footer ========== */
.form-footer {
  text-align: center;
  color: #64748b;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin-top: 4px;
}

.register-link {
  font-size: 14px;
  color: #00d4ff !important;
  font-weight: 500;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.register-link:hover {
  color: #00e5ff !important;
}

/* ========== Responsive ========== */
@media (max-width: 768px) {
  .login-container {
    flex-direction: column;
    width: 90%;
    min-height: auto;
    margin: 20px;
  }
  .login-left { padding: 30px; }
  .login-right { padding: 30px 20px; }
  .iot-visual { transform: scale(0.7); margin-bottom: 10px; }
  .feature-list { display: none; }
}
</style>
