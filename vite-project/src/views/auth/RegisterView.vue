<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { User, Lock, Message, Monitor, Connection, TrendCharts, Promotion, ArrowRight, View, Hide } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()

const registerForm = ref({
  username: '',
  email: '',
  password: '',
  confirmPassword: ''
})
const loading = ref(false)
const showPassword = ref(false)
const showConfirmPassword = ref(false)
const particles = ref<{ x: number; y: number; size: number; speed: number; opacity: number }[]>([])

const validatePass2 = (_rule: any, value: string, callback: Function) => {
  if (value === '') {
    callback(new Error('请再次输入密码'))
  } else if (value !== registerForm.value.password) {
    callback(new Error('两次输入密码不一致!'))
  } else {
    callback()
  }
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 64, message: '长度在 8 到 64 个字符', trigger: 'blur' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/, message: '密码须同时包含字母和数字', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validatePass2, trigger: 'blur' }
  ]
}

const formRef = ref()

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

async function handleRegister() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await authStore.register(
      registerForm.value.username,
      registerForm.value.email,
      registerForm.value.password
    )
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (error: any) {
    ElMessage.error(error.message || '注册失败')
  } finally {
    loading.value = false
  }
}

function goLogin() {
  router.push('/login')
}

onMounted(() => {
  initParticles()
  animateParticles()
})

onUnmounted(() => {
  cancelAnimationFrame(animFrame)
})
</script>

<template>
  <div class="register-page">
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

    <div class="register-container">
      <!-- Left: Brand Panel -->
      <div class="register-left">
        <div class="left-content">
          <!-- IoT visual with connecting nodes -->
          <div class="iot-visual">
            <div class="outer-ring">
              <div class="ring-node" v-for="i in 8" :key="i" :style="{ transform: `rotate(${i * 45}deg) translateY(-70px)` }" />
            </div>
            <div class="middle-ring">
              <div class="ring-node small" v-for="i in 6" :key="i" :style="{ transform: `rotate(${i * 60}deg) translateY(-50px)` }" />
            </div>
            <div class="core-icon">
              <el-icon :size="48"><Connection /></el-icon>
            </div>
          </div>

          <h1 class="brand-title">
            <span class="title-iot">IoT</span>
            <span class="title-cloud">加入物联网云平台</span>
          </h1>
          <p class="brand-subtitle">
            <span class="typing-text">创建账号 · 连接未来</span>
          </p>

          <div class="feature-list">
            <div class="feature-item">
              <div class="feature-icon"><el-icon><Monitor /></el-icon></div>
              <div class="feature-text">
                <span class="feature-title">设备管理</span>
                <span class="feature-desc">一站式纳管百万设备</span>
              </div>
            </div>
            <div class="feature-item">
              <div class="feature-icon"><el-icon><TrendCharts /></el-icon></div>
              <div class="feature-text">
                <span class="feature-title">数据可视化</span>
                <span class="feature-desc">丰富的图表与仪表盘</span>
              </div>
            </div>
            <div class="feature-item">
              <div class="feature-icon"><el-icon><Promotion /></el-icon></div>
              <div class="feature-text">
                <span class="feature-title">开放 API</span>
                <span class="feature-desc">灵活对接第三方系统</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Right: Register Form -->
      <div class="register-right">
        <div class="form-panel">
          <div class="form-header">
            <div class="form-avatar">
              <el-icon :size="28"><User /></el-icon>
            </div>
            <h2>创建账号</h2>
            <p>注册您的 IoT 控制台账号</p>
          </div>

          <el-form
            ref="formRef"
            :model="registerForm"
            :rules="rules"
            class="register-form"
            @keyup.enter="handleRegister"
          >
            <el-form-item prop="username">
              <el-input
                v-model="registerForm.username"
                placeholder="请输入用户名"
                :prefix-icon="User"
                size="large"
                class="custom-input"
              />
            </el-form-item>

            <el-form-item prop="email">
              <el-input
                v-model="registerForm.email"
                placeholder="请输入邮箱"
                :prefix-icon="Message"
                size="large"
                class="custom-input"
              />
            </el-form-item>

            <el-form-item prop="password">
              <el-input
                v-model="registerForm.password"
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

            <el-form-item prop="confirmPassword">
              <el-input
                v-model="registerForm.confirmPassword"
                :type="showConfirmPassword ? 'text' : 'password'"
                placeholder="请确认密码"
                :prefix-icon="Lock"
                size="large"
                class="custom-input"
              >
                <template #suffix>
                  <el-icon class="toggle-pwd" @click="showConfirmPassword = !showConfirmPassword">
                    <View v-if="showConfirmPassword" />
                    <Hide v-else />
                  </el-icon>
                </template>
              </el-input>
            </el-form-item>

            <div class="form-agreement">
              <el-checkbox label="我已阅读并同意">
                <template #default>
                  我已阅读并同意
                  <el-button link type="primary" class="agreement-link">《服务协议》</el-button>
                  和
                  <el-button link type="primary" class="agreement-link">《隐私政策》</el-button>
                </template>
              </el-checkbox>
            </div>

            <el-form-item>
              <el-button
                type="primary"
                size="large"
                class="register-btn"
                :loading="loading"
                @click="handleRegister"
              >
                <span v-if="!loading">注 册</span>
              </el-button>
            </el-form-item>
          </el-form>

          <div class="form-footer">
            <span>已有账号？</span>
            <el-button link type="primary" class="login-link" @click="goLogin">
              立即登录
              <el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.register-page {
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
  stroke: #00e5a0;
  stroke-width: 0.3;
  stroke-dasharray: 3 6;
  animation: lineGlow 4s ease-in-out infinite;
}

.net-line:nth-child(2n) { animation-delay: 1s; stroke: #7b61ff; }
.net-line:nth-child(3n) { animation-delay: 2s; stroke: #00e5a0; }
.net-line:nth-child(4n) { animation-delay: 3s; }

@keyframes lineGlow {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 1; stroke-width: 0.6; }
}

.particle {
  position: absolute;
  background: #00e5a0;
  border-radius: 50%;
  animation: floatUp linear infinite;
  box-shadow: 0 0 8px #00e5a0, 0 0 20px rgba(0, 229, 160, 0.4);
}

.particle:nth-child(odd) { background: #7b61ff; box-shadow: 0 0 8px #7b61ff, 0 0 20px rgba(123, 97, 255, 0.4); }
.particle:nth-child(3n) { background: #00d4ff; box-shadow: 0 0 8px #00d4ff, 0 0 20px rgba(0, 212, 255, 0.4); }

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
  background: rgba(0, 229, 160, 0.10);
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
  background: rgba(0, 229, 160, 0.07);
  top: 50%; left: 40%;
  animation-delay: 5s;
}

@keyframes orbPulse {
  0%, 100% { transform: scale(1); opacity: 0.6; }
  50% { transform: scale(1.3); opacity: 1; }
}

/* ========== Container ========== */
.register-container {
  display: flex;
  width: 1000px;
  min-height: 620px;
  border-radius: 20px;
  overflow: hidden;
  box-shadow:
    0 0 0 1px rgba(255,255,255,0.05),
    0 30px 80px rgba(0,0,0,0.5),
    0 0 120px rgba(0, 229, 160, 0.06);
  z-index: 1;
  backdrop-filter: blur(10px);
}

/* ========== Left Panel ========== */
.register-left {
  flex: 1;
  background: linear-gradient(160deg, #0a1a1a 0%, #0f2e2e 40%, #133a3a 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 50px 40px;
  position: relative;
  overflow: hidden;
}

.register-left::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 30% 20%, rgba(0, 229, 160, 0.08) 0%, transparent 50%),
    radial-gradient(circle at 70% 80%, rgba(0, 180, 255, 0.06) 0%, transparent 50%);
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
  border: 1.5px solid rgba(0, 229, 160, 0.25);
  border-radius: 50%;
  animation: ringRotate 20s linear infinite;
}

.middle-ring {
  position: absolute;
  width: 75%;
  height: 75%;
  border: 1px solid rgba(0, 200, 150, 0.2);
  border-radius: 50%;
  animation: ringRotate 12s linear infinite reverse;
}

.ring-node {
  position: absolute;
  width: 8px; height: 8px;
  background: #00e5a0;
  border-radius: 50%;
  top: 50%; left: 50%;
  box-shadow: 0 0 8px #00e5a0, 0 0 16px rgba(0, 229, 160, 0.6);
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
  background: linear-gradient(135deg, rgba(0, 229, 160, 0.2), rgba(0, 180, 130, 0.3));
  border: 1.5px solid rgba(0, 229, 160, 0.4);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #00e5a0;
  animation: coreGlow 3s ease-in-out infinite;
  position: relative;
  z-index: 2;
}

@keyframes coreGlow {
  0%, 100% { box-shadow: 0 0 20px rgba(0, 229, 160, 0.3); }
  50% { box-shadow: 0 0 40px rgba(0, 229, 160, 0.6), 0 0 80px rgba(0, 200, 150, 0.2); }
}

/* Brand text */
.brand-title {
  margin-bottom: 12px;
  font-size: 0;
}

.title-iot {
  font-size: 40px;
  font-weight: 800;
  background: linear-gradient(135deg, #00e5a0 0%, #00c896 50%, #7b61ff 100%);
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
  border-right: 2px solid #00e5a0;
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
  background: rgba(0, 229, 160, 0.06);
  border-color: rgba(0, 229, 160, 0.2);
  transform: translateX(4px);
}

.feature-icon {
  width: 40px; height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 229, 160, 0.1);
  border-radius: 10px;
  color: #00e5a0;
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
.register-right {
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
  margin-bottom: 30px;
}

.form-avatar {
  width: 64px; height: 64px;
  margin: 0 auto 16px;
  background: linear-gradient(135deg, rgba(0, 229, 160, 0.15), rgba(123, 97, 255, 0.15));
  border: 1px solid rgba(0, 229, 160, 0.25);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #00e5a0;
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

.register-form {
  margin-top: 8px;
}

.register-form :deep(.el-form-item) {
  margin-bottom: 18px;
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
  border-color: rgba(0, 229, 160, 0.3);
}

.custom-input :deep(.el-input__wrapper.is-focus) {
  border-color: #00e5a0;
  box-shadow: 0 0 0 2px rgba(0, 229, 160, 0.15);
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
  color: #00e5a0;
}

.form-agreement {
  margin-bottom: 18px;
  font-size: 13px;
}

.form-agreement :deep(.el-checkbox__label) {
  color: #64748b;
  font-size: 13px;
}

.agreement-link {
  font-size: 13px;
  color: #00e5a0 !important;
  padding: 0 2px;
}

.register-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 4px;
  border-radius: 10px;
  background: linear-gradient(135deg, #00c896 0%, #009688 50%, #6c63ff 100%);
  border: none;
  position: relative;
  overflow: hidden;
  transition: all 0.3s;
}

.register-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(0, 200, 150, 0.4);
}

.register-btn::before {
  content: '';
  position: absolute;
  top: 0; left: -100%;
  width: 100%; height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.15), transparent);
  transition: left 0.5s;
}

.register-btn:hover::before {
  left: 100%;
}

.form-footer {
  text-align: center;
  color: #64748b;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.login-link {
  font-size: 14px;
  color: #00e5a0 !important;
  font-weight: 500;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.login-link:hover {
  color: #00ffb0 !important;
}

/* ========== Responsive ========== */
@media (max-width: 768px) {
  .register-container {
    flex-direction: column;
    width: 90%;
    min-height: auto;
    margin: 20px;
  }
  .register-left { padding: 30px; }
  .register-right { padding: 30px 20px; }
  .iot-visual { transform: scale(0.7); margin-bottom: 10px; }
  .feature-list { display: none; }
}

/* Industrial access surface: retain the existing registration flow, remove decorative motion. */
.bg-canvas { display: none; }
.register-page { padding: 24px; background: var(--bg-primary); }
.register-container { width: min(960px, 100%); min-height: 560px; border: 1px solid var(--border-color); border-radius: 8px; box-shadow: 0 20px 48px rgba(0,0,0,.25); backdrop-filter: none; }.register-left { background: var(--bg-secondary); border-right: 1px solid var(--border-color); }.register-left::before { display: none; }.register-right { background: var(--bg-card); border: 0; backdrop-filter: none; }.title-iot, .title-cloud { color: var(--text-primary); -webkit-text-fill-color: var(--text-primary); background: none; }.brand-subtitle, .feature-desc, .form-header p, .form-footer { color: var(--text-secondary); }.feature-item { border-color: var(--border-color); border-radius: 6px; background: var(--bg-card); }.feature-item:hover { transform: none; background: var(--bg-hover); border-color: var(--color-primary); }.feature-icon, .form-avatar { border-radius: 6px; color: var(--color-primary); background: var(--color-primary-soft); border-color: rgba(22,119,255,.35); }.iot-visual { transform: scale(.82); }.outer-ring, .middle-ring { animation: none; border-color: var(--border-color); }.ring-node { display: none; }.core-icon { border-radius: 6px; animation: none; color: var(--color-primary); background: var(--color-primary-soft); border-color: rgba(22,119,255,.35); box-shadow: none; }.custom-input :deep(.el-input__wrapper) { border-radius: 6px; background: var(--bg-secondary); border-color: var(--border-color); }.custom-input :deep(.el-input__wrapper.is-focus) { border-color: var(--color-primary); background: var(--bg-secondary); box-shadow: 0 0 0 2px rgba(22,119,255,.14); }.register-btn { border-radius: 6px; background: var(--color-primary); }.register-btn:hover { transform: none; box-shadow: none; }.register-btn::before { display: none; }.login-link, .login-link:hover, .agreement-link { color: var(--color-primary) !important; }
</style>
