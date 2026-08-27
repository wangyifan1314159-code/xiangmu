<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { 
  Plus, 
  Setting, 
  Promotion, 
  CopyDocument, 
  Delete, 
  ChatDotRound, 
  Cpu, 
  DataAnalysis, 
  WarningFilled, 
  TrendCharts,
  Refresh,
  Check
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

defineOptions({ name: 'AiAssistant' })

interface Message {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  timestamp: string
}

interface ChatSession {
  id: string
  title: string
  messages: Message[]
  updatedAt: number
}

interface AiConfig {
  provider: 'gemini' | 'openai'
  baseUrl: string
  apiKey: string
  model: string
  temperature: number
  systemPrompt: string
}

// ── 默认预设模型列表 ────────────────────────────────────────────────────────
const defaultGeminiModels = [
  'gemini-1.5-flash',
  'gemini-1.5-pro',
  'gemini-2.0-flash-exp',
  'gemini-1.0-pro'
]

const defaultOpenAiModels = [
  'deepseek-chat',
  'deepseek-reasoner',
  'gpt-4o',
  'gpt-4o-mini',
  'qwen-max',
  'qwen-plus',
  'claude-3-5-sonnet'
]

const availableModels = ref<string[]>([...defaultGeminiModels])
const fetchModelsLoading = ref(false)

// ── 默认配置与本地持久化 ───────────────────────────────────────────────────
const defaultConfig: AiConfig = {
  provider: 'gemini',
  baseUrl: 'https://generativelanguage.googleapis.com/v1beta',
  apiKey: '',
  model: 'gemini-1.5-flash',
  temperature: 0.7,
  systemPrompt: '你是由 Google 技术赋能的工业物联网与大数据平台智能辅助专家。精通掘进机设备工况分析、瓦斯与环境监测、流计算数据质量清洗（Flink）、湖仓一体架构（Doris/Iceberg）以及设备预测性维护（PHM）。请用专业、条理分明且带有操作指引的格式回答用户问题。'
}

const config = ref<AiConfig>({ ...defaultConfig })
const isSettingsOpen = ref(false)
const testLoading = ref(false)

function loadConfig() {
  const saved = localStorage.getItem('iot_ai_config')
  if (saved) {
    try {
      config.value = { ...defaultConfig, ...JSON.parse(saved) }
      if (config.value.provider === 'gemini') {
        availableModels.value = Array.from(new Set([config.value.model, ...defaultGeminiModels]))
      } else {
        availableModels.value = Array.from(new Set([config.value.model, ...defaultOpenAiModels]))
      }
    } catch { /* ignore */ }
  }
}

function saveConfig() {
  localStorage.setItem('iot_ai_config', JSON.stringify(config.value))
  isSettingsOpen.value = false
  ElMessage.success('AI 模型配置已保存')
}

// 快速填入常用服务商地址
function applyPreset(type: 'gemini' | 'deepseek' | 'openai' | 'ollama') {
  if (type === 'gemini') {
    config.value.provider = 'gemini'
    config.value.baseUrl = 'https://generativelanguage.googleapis.com/v1beta'
    config.value.model = 'gemini-1.5-flash'
    availableModels.value = [...defaultGeminiModels]
  } else if (type === 'deepseek') {
    config.value.provider = 'openai'
    config.value.baseUrl = 'https://api.deepseek.com/v1'
    config.value.model = 'deepseek-chat'
    availableModels.value = [...defaultOpenAiModels]
  } else if (type === 'openai') {
    config.value.provider = 'openai'
    config.value.baseUrl = 'https://api.openai.com/v1'
    config.value.model = 'gpt-4o-mini'
    availableModels.value = [...defaultOpenAiModels]
  } else if (type === 'ollama') {
    config.value.provider = 'openai'
    config.value.baseUrl = 'http://localhost:11434/v1'
    config.value.model = 'llama3'
    availableModels.value = ['llama3', 'qwen2.5', 'deepseek-r1']
  }
}

function onProviderChange(newProvider: 'gemini' | 'openai') {
  if (newProvider === 'gemini') {
    if (!config.value.baseUrl || config.value.baseUrl.includes('deepseek') || config.value.baseUrl.includes('openai')) {
      config.value.baseUrl = 'https://generativelanguage.googleapis.com/v1beta'
    }
    availableModels.value = Array.from(new Set([config.value.model, ...defaultGeminiModels]))
    if (!config.value.model.includes('gemini')) config.value.model = 'gemini-1.5-flash'
  } else {
    if (!config.value.baseUrl || config.value.baseUrl.includes('generativelanguage.googleapis.com')) {
      config.value.baseUrl = 'https://api.deepseek.com/v1'
    }
    availableModels.value = Array.from(new Set([config.value.model, ...defaultOpenAiModels]))
    if (config.value.model.includes('gemini')) config.value.model = 'deepseek-chat'
  }
}

// ── 核心新功能：在线拉取可用模型列表 ─────────────────────────────────────────
async function fetchAvailableModels() {
  if (!config.value.apiKey || !config.value.apiKey.trim()) {
    ElMessage.warning('请先填写 API Key，以便向服务器查询模型列表')
    return
  }
  if (!config.value.baseUrl || !config.value.baseUrl.trim()) {
    ElMessage.warning('请先填写 API 基础地址')
    return
  }

  fetchModelsLoading.value = true
  try {
    const baseUrlClean = config.value.baseUrl.trim().replace(/\/+$/, '')

    if (config.value.provider === 'gemini') {
      // 请求 Google Gemini: /models?key=xxx
      const url = `${baseUrlClean}/models?key=${config.value.apiKey.trim()}`
      const resp = await fetch(url)
      if (!resp.ok) {
        const errJson = await resp.json().catch(() => ({}))
        throw new Error(errJson?.error?.message || `HTTP ${resp.status} ${resp.statusText}`)
      }
      const data = await resp.json()
      const list = data?.models || []
      const parsed = list
        .filter((m: any) => m.supportedGenerationMethods?.includes('generateContent') || m.name?.includes('gemini'))
        .map((m: any) => m.name.replace(/^models\//, ''))

      if (parsed.length > 0) {
        availableModels.value = Array.from(new Set([...parsed, ...defaultGeminiModels]))
        if (!availableModels.value.includes(config.value.model)) {
          config.value.model = parsed[0]
        }
        ElMessage.success(`成功获取 ${parsed.length} 个可用 Gemini 模型！`)
      } else {
        ElMessage.info('已连接成功，保留预设模型列表')
      }
    } else {
      // 请求 OpenAI / DeepSeek / 兼容接口: /models
      const url = `${baseUrlClean}/models`
      const resp = await fetch(url, {
        headers: {
          'Authorization': `Bearer ${config.value.apiKey.trim()}`
        }
      })
      if (!resp.ok) {
        const errJson = await resp.json().catch(() => ({}))
        throw new Error(errJson?.error?.message || `HTTP ${resp.status} ${resp.statusText}`)
      }
      const data = await resp.json()
      const list = data?.data || data?.models || []
      const parsed = list.map((m: any) => m.id || m.name).filter(Boolean)

      if (parsed.length > 0) {
        availableModels.value = Array.from(new Set([...parsed, ...defaultOpenAiModels]))
        if (!availableModels.value.includes(config.value.model)) {
          config.value.model = parsed[0]
        }
        ElMessage.success(`成功获取 ${parsed.length} 个可用模型！`)
      } else {
        ElMessage.info('已连接成功，保留预设模型列表')
      }
    }
  } catch (e: any) {
    ElMessage.error(`获取模型失败: ${e?.message || '网络连接超时或跨域限制'}`)
  } finally {
    fetchModelsLoading.value = false
  }
}

// ── 会话管理 ─────────────────────────────────────────────────────────────
const sessions = ref<ChatSession[]>([])
const currentSessionId = ref<string>('')
const inputPrompt = ref('')
const isGenerating = ref(false)
const chatScrollRef = ref<HTMLDivElement | null>(null)

const currentSession = computed(() => {
  return sessions.value.find(s => s.id === currentSessionId.value) || sessions.value[0]
})

function loadSessions() {
  const saved = localStorage.getItem('iot_ai_sessions')
  if (saved) {
    try {
      sessions.value = JSON.parse(saved)
    } catch { /* ignore */ }
  }
  if (!sessions.value || sessions.value.length === 0) {
    createNewSession()
  } else {
    currentSessionId.value = sessions.value[0].id
  }
}

function saveSessions() {
  localStorage.setItem('iot_ai_sessions', JSON.stringify(sessions.value))
}

function createNewSession() {
  const newSession: ChatSession = {
    id: `sess_${Date.now()}`,
    title: '新对话',
    messages: [],
    updatedAt: Date.now()
  }
  sessions.value.unshift(newSession)
  currentSessionId.value = newSession.id
  saveSessions()
}

function selectSession(id: string) {
  currentSessionId.value = id
  scrollToBottom()
}

function deleteSession(id: string, e: Event) {
  e.stopPropagation()
  sessions.value = sessions.value.filter(s => s.id !== id)
  if (sessions.value.length === 0) {
    createNewSession()
  } else if (currentSessionId.value === id) {
    currentSessionId.value = sessions.value[0].id
  }
  saveSessions()
  ElMessage.info('对话已清除')
}

function scrollToBottom() {
  nextTick(() => {
    if (chatScrollRef.value) {
      chatScrollRef.value.scrollTop = chatScrollRef.value.scrollHeight
    }
  })
}

// ── 快捷灵感推荐卡片 ─────────────────────────────────────────────────────
const quickPrompts = [
  { icon: TrendCharts, title: '掘进机温升分析', prompt: '当前截割电机温度为 74.8℃，请分析可能的原因与应急处置建议。' },
  { icon: WarningFilled, title: '瓦斯浓度预警排查', prompt: '工作面甲烷浓度出现 0.65% 异常上扬，如何进行风网自适应调节？' },
  { icon: DataAnalysis, title: 'Flink 流清洗规则', prompt: '介绍一下平台针对传感器死值（Flatline）与物理极值的清洗过滤算法。' },
  { icon: Cpu, title: 'PHM 预测寿命计算', prompt: '设备健康度评分（96分）与 RUL（剩余寿命天数）是如何结合振动与电流计算的？' }
]

function useQuickPrompt(text: string) {
  inputPrompt.value = text
  sendMessage()
}

// ── 发送消息与调用 AI ────────────────────────────────────────────────────
async function sendMessage() {
  const text = inputPrompt.value.trim()
  if (!text || isGenerating.value) return

  const session = currentSession.value
  if (!session) return

  const userMsg: Message = {
    id: `msg_${Date.now()}`,
    role: 'user',
    content: text,
    timestamp: new Date().toLocaleTimeString()
  }
  session.messages.push(userMsg)
  if (session.messages.length === 1) {
    session.title = text.slice(0, 16) + (text.length > 16 ? '...' : '')
  }
  session.updatedAt = Date.now()
  inputPrompt.value = ''
  scrollToBottom()

  const aiMsg: Message = {
    id: `msg_${Date.now() + 1}`,
    role: 'assistant',
    content: '',
    timestamp: new Date().toLocaleTimeString()
  }
  session.messages.push(aiMsg)
  isGenerating.value = true

  try {
    if (config.value.apiKey && config.value.apiKey.trim()) {
      await callRealAiApi(session, aiMsg)
    } else {
      await simulateIntelligentResponse(text, aiMsg)
    }
  } catch (err: any) {
    aiMsg.content = `[调用异常]: ${err?.message || '与 AI 模型通信失败，请检查设置中的 API Key 或网络连通性。'}`
  } finally {
    isGenerating.value = false
    saveSessions()
    scrollToBottom()
  }
}

// ── 真实 API 调用 ────────────────────────────────────────────────────────
async function callRealAiApi(session: ChatSession, aiMsg: Message) {
  if (config.value.provider === 'gemini') {
    const url = `${config.value.baseUrl.trim().replace(/\/+$/, '')}/models/${config.value.model}:generateContent?key=${config.value.apiKey.trim()}`
    const contents = session.messages.slice(0, -1).map(m => ({
      role: m.role === 'assistant' ? 'model' : 'user',
      parts: [{ text: m.content }]
    }))

    const resp = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        contents,
        systemInstruction: { parts: [{ text: config.value.systemPrompt }] },
        generationConfig: { temperature: config.value.temperature }
      })
    })

    if (!resp.ok) {
      const errJson = await resp.json().catch(() => ({}))
      throw new Error(errJson?.error?.message || `HTTP ${resp.status} ${resp.statusText}`)
    }

    const data = await resp.json()
    const reply = data?.candidates?.[0]?.content?.parts?.[0]?.text || '未能解析到有效回答。'
    await streamTypingText(reply, aiMsg)

  } else {
    const url = `${config.value.baseUrl.trim().replace(/\/+$/, '')}/chat/completions`
    const messages = [
      { role: 'system', content: config.value.systemPrompt },
      ...session.messages.slice(0, -1).map(m => ({
        role: m.role === 'assistant' ? 'assistant' : 'user',
        content: m.content
      }))
    ]

    const resp = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${config.value.apiKey.trim()}`
      },
      body: JSON.stringify({
        model: config.value.model,
        messages,
        temperature: config.value.temperature
      })
    })

    if (!resp.ok) {
      const errJson = await resp.json().catch(() => ({}))
      throw new Error(errJson?.error?.message || `HTTP ${resp.status} ${resp.statusText}`)
    }

    const data = await resp.json()
    const reply = data?.choices?.[0]?.message?.content || '未能解析到有效回答。'
    await streamTypingText(reply, aiMsg)
  }
}

async function streamTypingText(fullText: string, targetMsg: Message) {
  const chunkSize = Math.max(2, Math.floor(fullText.length / 30))
  let curIdx = 0
  while (curIdx < fullText.length) {
    curIdx = Math.min(fullText.length, curIdx + chunkSize)
    targetMsg.content = fullText.slice(0, curIdx)
    scrollToBottom()
    await new Promise(r => setTimeout(r, 20))
  }
}

// ── 内置专业工业知识库 ────────────────────────────────────────────────────
async function simulateIntelligentResponse(question: string, aiMsg: Message) {
  const q = question.toLowerCase()
  let reply = ''

  if (q.includes('温度') || q.includes('温升') || q.includes('截割')) {
    reply = `### 🔍 截割电机温度异常诊断分析报告\n\n**当前监控读数**：74.8℃（接近一阶预警阈值 75.0℃）\n\n#### 1. 核心诱因研判\n- **岩层硬度激增 (Hard Rock Penetration)**：进入 f>6 坚硬夹矸岩层，截割阻力导致主驱动电机过载，定子线圈发热。\n- **截割齿磨损与钝化**：3# 与 5# 截割齿出现局部崩刃，截割效率降低导致机械摩擦损耗转化为内能。\n- **冷却水压波动**：当前喷雾水压为 1.8 MPa（标准建议 ≥ 2.2 MPa），内冷却水路热交换效率下降 18%。\n\n#### 2. AI 智能闭环处置建议\n1. **推进降速**：调控推进油缸将截割进尺速度由 \`0.35m/min\` 调降至 \`0.18m/min\`；\n2. **风水联动自愈**：一键触发自适应增压泵，提升内喷雾回路水压至 \`2.5 MPa\`；\n3. **维保排查**：在下一个班次交接期间，重点检查截割头截齿磨损情况及齿轮箱润滑油油位。`
  } else if (q.includes('瓦斯') || q.includes('甲烷') || q.includes('ch4')) {
    reply = `### 🍃 工作面瓦斯 (CH4) 浓度动态调控方案\n\n**实时监测值**：0.65%（轻度上扬，国家煤矿安全规程警戒线 1.00%）\n\n#### 1. 动态风险评估\n- 工作面风流流速处于 12.5 m/s，回风隅角存在轻微微风涡流。\n- 瓦斯涌出速率：\`0.08 m³/min\`（较稳态上升 14%）。\n\n#### 2. 自适应联动措施\n- 联动开启局扇通风机二档调速，增加工作面有效供风量。\n- 瓦斯抽采管道抽放负压自动自适应由 \`-2.8 kPa\` 调整至 \`-3.6 kPa\`。\n- 若浓度持续超 0.8%，系统将自动闭锁掘进机截割电源，保障本质安全。`
  } else if (q.includes('flink') || q.includes('清洗') || q.includes('死值')) {
    reply = `### ⚡ Flink 1.18 实时流计算质量清洗机制\n\n平台在流计算层（\`iot-flink-jobs\`）部署了分布式实时质量清洗拓扑：\n\n1. **死值卡死检测 (Flatline Filter)**：\n   - 基于 10 分钟滑动窗口的实时方差分析 \`Var(X) < 1e-6\`，自动标记并过滤传感器硬件假死。\n2. **物理超限与野值拦截 (Out-of-Bound)**：\n   - 结合各设备物理量程极限（如温度范围 \`-20℃ ~ 150℃\`），异常极值自动分流进入死信队列。\n3. **迟到重传数据双流分道 (Side Output)**：\n   - Watermark 允许 5 秒延迟，超过 5 秒的断网重传数据旁路归档至 **Iceberg 历史冷存湖**，确保主流实时统计零偏差。`
  } else if (q.includes('phm') || q.includes('寿命') || q.includes('健康度')) {
    reply = `### 🩺 AI 预测性维护 (PHM) 与 RUL 计算模型\n\n本平台设备综合健康度与剩余使用寿命（RUL）采用多源特征融合推断：\n\n- **数据特征输入**：三轴振动加速度 RMS、主电机有效工作电流、各轴承温升梯度、累计截割转速循环数。\n- **健康指数公式**：\n  $$\\text{Health Score} = 100 - \\sum_{i} w_i \\cdot \\max(0, \\text{Feature}_i - \\text{Threshold}_i)$$\n- **RUL 预测天数**：基于历史退化曲线的自回归预测，当前 EBZ-260 预测剩余有效寿命约为 **450 天**。`
  } else {
    reply = `收到您关于「**${question}**」的咨询。\n\n作为工业物联网与大数据平台智能辅助中枢，我可以为您提供：\n1. **实时遥测异常诊断**：结合现场传感器数据与阈值进行因果溯源；\n2. **流计算与湖仓调优**：Flink 作业负载、Kafka 分区吞吐与 Doris 聚合查询性能分析；\n3. **设备预测性维护**：掘进机、通风机、主排水泵的健康评分与零部件换修建议。\n\n您可以点击左下角「**模型配置**」按钮配置 API Key 并一键获取在线模型，体验更广阔的通用大模型能力！`
  }

  await streamTypingText(reply, aiMsg)
}

async function testConnection() {
  testLoading.value = true
  try {
    if (!config.value.apiKey) {
      ElMessage.warning('请先填写 API Key')
      return
    }
    const baseUrlClean = config.value.baseUrl.trim().replace(/\/+$/, '')
    if (config.value.provider === 'gemini') {
      const url = `${baseUrlClean}/models?key=${config.value.apiKey.trim()}`
      const resp = await fetch(url)
      if (resp.ok) {
        ElMessage.success('Google Gemini API 连通性测试成功！')
      } else {
        const err = await resp.json().catch(() => ({}))
        ElMessage.error(`连接失败: ${err?.error?.message || resp.statusText}`)
      }
    } else {
      const url = `${baseUrlClean}/models`
      const resp = await fetch(url, {
        headers: { 'Authorization': `Bearer ${config.value.apiKey.trim()}` }
      })
      if (resp.ok) {
        ElMessage.success('OpenAI 兼容 API 连通性测试成功！')
      } else {
        ElMessage.error(`连接失败: HTTP ${resp.status}`)
      }
    }
  } catch (e: any) {
    ElMessage.error(`测试失败: ${e?.message || '网络无法连接'}`)
  } finally {
    testLoading.value = false
  }
}

const copiedId = ref('')
function copyMessage(text: string, id: string) {
  navigator.clipboard.writeText(text).then(() => {
    copiedId.value = id
    ElMessage.success('已复制到剪贴板')
    setTimeout(() => { copiedId.value = '' }, 2000)
  })
}

function formatMarkdown(text: string): string {
  if (!text) return ''
  let html = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  html = html.replace(/^### (.*$)/gim, '<h3 class="md-h3">$1</h3>')
  html = html.replace(/^## (.*$)/gim, '<h2 class="md-h2">$1</h2>')
  html = html.replace(/^# (.*$)/gim, '<h1 class="md-h1">$1</h1>')

  html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/`([^`]+)`/g, '<code class="md-code">$1</code>')

  html = html.replace(/^\- (.*$)/gim, '<li class="md-li">$1</li>')
  html = html.replace(/^\d+\. (.*$)/gim, '<li class="md-oli">$1</li>')

  html = html.replace(/\n/g, '<br/>')
  return html
}

onMounted(() => {
  loadConfig()
  loadSessions()
})
</script>

<template>
  <div class="ai-assistant-page">
    
    <!-- ════════════ 左侧会话侧边栏 ════════════ -->
    <aside class="ai-sidebar">
      
      <!-- 顶部：新建对话按钮 -->
      <div class="sidebar-top">
        <button class="new-chat-btn" @click="createNewSession">
          <el-icon><Plus /></el-icon>
          <span>新建对话</span>
        </button>
      </div>

      <!-- 会话历史列表 -->
      <div class="session-list">
        <div class="list-label">历史会话</div>
        <div 
          v-for="s in sessions" 
          :key="s.id"
          class="session-item"
          :class="{ active: currentSessionId === s.id }"
          @click="selectSession(s.id)"
        >
          <el-icon class="s-icon"><ChatDotRound /></el-icon>
          <span class="s-title text-ellipsis">{{ s.title }}</span>
          <button class="del-btn" title="删除对话" @click="deleteSession(s.id, $event)">
            <el-icon><Delete /></el-icon>
          </button>
        </div>
      </div>

      <!-- 左下角：模型设置按钮 (核心需求) -->
      <div class="sidebar-bottom">
        <button class="settings-trigger-btn" @click="isSettingsOpen = true">
          <el-icon><Setting /></el-icon>
          <div class="btn-txt">
            <strong>模型配置 (API)</strong>
            <small class="text-ellipsis">{{ config.apiKey ? config.model : '内置工业知识库' }}</small>
          </div>
        </button>
      </div>
    </aside>

    <!-- ════════════ 右侧主对话视窗 ════════════ -->
    <main class="ai-chat-main">
      
      <!-- 顶部轻量状态指示 -->
      <div class="chat-top-header">
        <div class="top-info">
          <span class="top-kicker">AI ASSISTANT</span>
          <h2>智能辅助中枢</h2>
        </div>
        <div class="top-tag">
          <span class="status-indicator"></span>
          <span>{{ config.apiKey ? config.model : '内置工业知识库' }}</span>
        </div>
      </div>

      <!-- 对话主滚动区 -->
      <div ref="chatScrollRef" class="chat-viewport">
        
        <!-- 空状态：Gemini 风格欢迎卡片 + 灵感提示 -->
        <div v-if="!currentSession || currentSession.messages.length === 0" class="welcome-screen">
          <div class="gemini-sparkle-logo">
            <span class="sparkle-star">✦</span>
          </div>
          <h1 class="welcome-title">
            <span class="gradient-text">你好，工程师</span>
          </h1>
          <p class="welcome-subtitle">我是工业物联网与大数据平台智能辅助系统，今天有什么我可以协助你的？</p>

          <!-- 推荐灵感网格 -->
          <div class="prompts-grid">
            <div 
              v-for="(p, idx) in quickPrompts" 
              :key="idx" 
              class="prompt-card"
              @click="useQuickPrompt(p.prompt)"
            >
              <div class="prompt-icon"><el-icon><component :is="p.icon" /></el-icon></div>
              <div class="prompt-body">
                <strong>{{ p.title }}</strong>
                <p>{{ p.prompt }}</p>
              </div>
            </div>
          </div>
        </div>

        <!-- 消息列表 -->
        <div v-else class="messages-container">
          <div 
            v-for="msg in currentSession.messages" 
            :key="msg.id"
            class="message-row"
            :class="msg.role"
          >
            <!-- 头像 -->
            <div class="avatar">
              <span v-if="msg.role === 'user'">👤</span>
              <span v-else class="ai-star">✦</span>
            </div>

            <!-- 消息气泡 -->
            <div class="bubble-content">
              <div class="bubble-header">
                <span class="author">{{ msg.role === 'user' ? '你' : 'AI 辅助专家' }}</span>
                <span class="time">{{ msg.timestamp }}</span>
              </div>
              
              <div class="bubble-body markdown-body" v-html="formatMarkdown(msg.content)"></div>

              <!-- 气泡操作栏 -->
              <div v-if="msg.role === 'assistant' && msg.content" class="bubble-actions">
                <button class="action-btn" title="复制回答" @click="copyMessage(msg.content, msg.id)">
                  <el-icon><component :is="copiedId === msg.id ? Check : CopyDocument" /></el-icon>
                  <span>{{ copiedId === msg.id ? '已复制' : '复制' }}</span>
                </button>
              </div>
            </div>
          </div>

          <!-- 生成中呼吸动效 -->
          <div v-if="isGenerating" class="generating-indicator">
            <span class="dot"></span><span class="dot"></span><span class="dot"></span>
            <em>AI 正在分析推断中...</em>
          </div>
        </div>

      </div>

      <!-- 底部输入框 (居中胶囊质感) -->
      <div class="chat-input-area">
        <div class="input-capsule">
          <textarea 
            v-model="inputPrompt"
            class="prompt-textarea"
            placeholder="询问工业设备诊断、流计算指标或输入任何技术问题..."
            rows="1"
            @keydown.enter.prevent="sendMessage"
          ></textarea>
          
          <button 
            class="send-btn" 
            :disabled="!inputPrompt.trim() || isGenerating"
            @click="sendMessage"
          >
            <el-icon><Promotion /></el-icon>
          </button>
        </div>
        <div class="input-footnote">
          <span>模型回答由工业物联网大模型生成，关键工业操作请遵照安全规程。</span>
        </div>
      </div>

    </main>

    <!-- ════════════ 简化且支持一键拉取模型的配置弹窗 ════════════ -->
    <el-dialog 
      v-model="isSettingsOpen" 
      title="⚙️ AI 模型服务与 API 配置" 
      width="520px"
      append-to-body
      class="simple-settings-dialog"
    >
      <el-form label-position="top" class="settings-form">
        
        <!-- 提供商选择 -->
        <el-form-item label="服务商类型">
          <el-radio-group v-model="config.provider" size="small" @change="onProviderChange">
            <el-radio-button label="gemini">Google Gemini</el-radio-button>
            <el-radio-button label="openai">OpenAI / DeepSeek / 兼容接口</el-radio-button>
          </el-radio-group>
          
          <!-- 常用预设快捷按钮 -->
          <div class="preset-links">
            <span class="preset-label">快捷填入：</span>
            <el-tag size="small" effect="plain" class="preset-tag" @click="applyPreset('gemini')">Gemini 官方</el-tag>
            <el-tag size="small" effect="plain" class="preset-tag" @click="applyPreset('deepseek')">DeepSeek 官方</el-tag>
            <el-tag size="small" effect="plain" class="preset-tag" @click="applyPreset('openai')">OpenAI</el-tag>
            <el-tag size="small" effect="plain" class="preset-tag" @click="applyPreset('ollama')">Ollama 本地</el-tag>
          </div>
        </el-form-item>

        <!-- API Base URL -->
        <el-form-item label="API 接口地址 (Base URL)">
          <el-input 
            v-model="config.baseUrl" 
            placeholder="例如: https://generativelanguage.googleapis.com/v1beta" 
          />
        </el-form-item>

        <!-- API Key -->
        <el-form-item label="API Key">
          <el-input 
            v-model="config.apiKey" 
            type="password" 
            show-password 
            placeholder="输入您的 API Key（未填写时自动启用内置知识库）" 
          />
        </el-form-item>

        <!-- 选择或获取模型 -->
        <el-form-item label="模型选择 (Model)">
          <div class="model-select-row">
            <el-select 
              v-model="config.model" 
              filterable 
              allow-create 
              default-first-option
              placeholder="选择或直接输入模型名称"
              style="flex: 1"
            >
              <el-option 
                v-for="m in availableModels" 
                :key="m" 
                :label="m" 
                :value="m" 
              />
            </el-select>

            <!-- 核心功能：点击获取模型 -->
            <el-button 
              type="primary" 
              plain
              :icon="Refresh" 
              :loading="fetchModelsLoading"
              title="根据当前地址和 Key 联网拉取支持的模型列表"
              @click="fetchAvailableModels"
            >
              获取模型
            </el-button>
          </div>
          <span class="form-tip">配置好地址和 Key 后点击「获取模型」即可下拉选择，也可直接输入自定义模型名。</span>
        </el-form-item>

        <!-- 折叠高级设置（使界面简洁不臃肿） -->
        <el-collapse class="advanced-collapse">
          <el-collapse-item title="高级参数选项 (Temperature & System Prompt)" name="1">
            <el-form-item label="随机性 (Temperature)">
              <div class="slider-row">
                <el-slider v-model="config.temperature" :min="0" :max="1" :step="0.1" style="flex:1" />
                <span class="slider-val">{{ config.temperature }}</span>
              </div>
            </el-form-item>

            <el-form-item label="系统预设人设 (System Prompt)">
              <el-input 
                v-model="config.systemPrompt" 
                type="textarea" 
                :rows="2" 
                placeholder="定义 AI 的人设与工业分析专长..." 
              />
            </el-form-item>
          </el-collapse-item>
        </el-collapse>

      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button :loading="testLoading" @click="testConnection">测试连通性</el-button>
          <el-button type="primary" @click="saveConfig">保存配置</el-button>
        </div>
      </template>
    </el-dialog>

  </div>
</template>

<style scoped>
/* ════════════ 采用系统主题变量 (--bg-card, --border-color 等)，与全站完美融合 ════════════ */
.ai-assistant-page {
  display: flex;
  height: calc(100vh - 100px);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-panel, 8px);
  color: var(--text-primary);
  overflow: hidden;
}

/* ════════════ 左侧会话侧边栏 ════════════ */
.ai-sidebar {
  width: 250px;
  background: var(--bg-secondary);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-top {
  padding: 14px;
}

.new-chat-btn {
  width: 100%;
  height: 38px;
  border-radius: 6px;
  background: var(--color-primary);
  border: none;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: opacity 0.2s, transform 0.1s;
}

.new-chat-btn:hover {
  opacity: 0.9;
  transform: translateY(-1px);
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.list-label {
  font-size: 11px;
  color: var(--text-muted);
  padding: 6px 10px 4px;
  font-weight: 600;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-secondary);
  transition: all 0.2s;
}

.session-item:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.session-item.active {
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-weight: 600;
}

.s-icon { font-size: 14px; flex-shrink: 0; }
.s-title { flex: 1; min-width: 0; }

.del-btn {
  background: transparent;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 2px;
  display: none;
  border-radius: 4px;
}

.session-item:hover .del-btn {
  display: block;
}

.del-btn:hover {
  color: var(--color-danger, #ef4444);
}

/* 左下角模型设置按钮 */
.sidebar-bottom {
  padding: 12px;
  border-top: 1px solid var(--border-color);
}

.settings-trigger-btn {
  width: 100%;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 8px 10px;
  color: var(--text-primary);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 10px;
  text-align: left;
  transition: all 0.2s;
}

.settings-trigger-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.settings-trigger-btn .el-icon {
  font-size: 16px;
  color: var(--color-primary);
}

.btn-txt strong {
  display: block;
  font-size: 12px;
  line-height: 1.2;
}

.btn-txt small {
  font-size: 10px;
  color: var(--text-muted);
}

/* ════════════ 右侧主对话区 ════════════ */
.ai-chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
  position: relative;
  min-width: 0;
}

.chat-top-header {
  height: 48px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
  border-bottom: 1px solid var(--border-color);
}

.top-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.top-kicker {
  font-size: 10px;
  color: var(--color-cyan, #22d3ee);
  font-family: 'Roboto Mono', monospace;
  letter-spacing: .1em;
}

.top-info h2 {
  font-size: 14px;
  margin: 0;
  color: var(--text-primary);
}

.top-tag {
  font-size: 11px;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--bg-secondary);
  padding: 3px 8px;
  border-radius: 4px;
  border: 1px solid var(--border-color);
}

.status-indicator {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-success, #22c55e);
}

.chat-viewport {
  flex: 1;
  overflow-y: auto;
  padding: 24px 18% 16px;
  display: flex;
  flex-direction: column;
}

@media (max-width: 1200px) {
  .chat-viewport { padding: 20px 10% 16px; }
}
@media (max-width: 800px) {
  .chat-viewport { padding: 14px; }
}

/* 欢迎卡片 (Gemini 风格) */
.welcome-screen {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  text-align: center;
  max-width: 760px;
  margin: 0 auto;
}

.gemini-sparkle-logo {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-cyan) 100%);
  display: grid;
  place-items: center;
  margin-bottom: 16px;
  box-shadow: 0 4px 16px var(--color-primary-soft);
}

.sparkle-star {
  font-size: 22px;
  color: #fff;
}

.welcome-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 8px;
  color: var(--text-primary);
}

.gradient-text {
  background: linear-gradient(135deg, var(--color-primary), var(--color-cyan));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.welcome-subtitle {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 28px;
}

/* 灵感网格 */
.prompts-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  width: 100%;
}

.prompt-card {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 12px 14px;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  cursor: pointer;
  text-align: left;
  transition: all 0.2s;
}

.prompt-card:hover {
  background: var(--bg-hover);
  border-color: var(--color-primary);
  transform: translateY(-2px);
}

.prompt-icon {
  width: 30px;
  height: 30px;
  border-radius: 6px;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  display: grid;
  place-items: center;
  font-size: 15px;
  flex-shrink: 0;
}

.prompt-body strong {
  display: block;
  font-size: 13px;
  color: var(--text-primary);
  margin-bottom: 3px;
}

.prompt-body p {
  margin: 0;
  font-size: 11px;
  color: var(--text-secondary);
  line-height: 1.4;
}

/* ════════════ 消息流 ════════════ */
.messages-container {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.message-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.message-row.user {
  flex-direction: row-reverse;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  font-size: 15px;
}

.message-row.assistant .avatar {
  background: linear-gradient(135deg, var(--color-primary), var(--color-cyan));
  color: #fff;
  border: none;
}

.ai-star {
  color: #fff;
  font-size: 15px;
}

.bubble-content {
  max-width: 80%;
  display: flex;
  flex-direction: column;
}

.bubble-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
  font-size: 11px;
  color: var(--text-muted);
}

.message-row.user .bubble-header {
  justify-content: flex-end;
}

.bubble-body {
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.6;
}

.message-row.user .bubble-body {
  background: var(--color-primary);
  color: #fff;
  border-bottom-right-radius: 2px;
}

.message-row.assistant .bubble-body {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  color: var(--text-primary);
  border-bottom-left-radius: 2px;
}

.bubble-actions {
  display: flex;
  gap: 6px;
  margin-top: 4px;
}

.action-btn {
  background: transparent;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  padding: 2px 6px;
  color: var(--text-secondary);
  font-size: 11px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  transition: all 0.2s;
}

.action-btn:hover {
  background: var(--bg-hover);
  color: var(--color-primary);
  border-color: var(--color-primary);
}

/* 生成中指示点 */
.generating-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  color: var(--text-secondary);
  font-size: 12px;
}

.generating-indicator .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-primary);
  animation: pulse-dot 1.2s infinite ease-in-out;
}
.generating-indicator .dot:nth-child(2) { animation-delay: 0.2s; }
.generating-indicator .dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes pulse-dot {
  0%, 100% { opacity: 0.2; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.2); }
}

/* ════════════ 底部输入胶囊 ════════════ */
.chat-input-area {
  padding: 12px 18% 18px;
  background: var(--bg-card);
}

@media (max-width: 1200px) {
  .chat-input-area { padding: 12px 10% 18px; }
}
@media (max-width: 800px) {
  .chat-input-area { padding: 8px 12px 14px; }
}

.input-capsule {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 24px;
  padding: 8px 14px;
  display: flex;
  align-items: center;
  gap: 10px;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.input-capsule:focus-within {
  border-color: var(--color-primary);
  box-shadow: 0 0 12px var(--color-primary-soft);
}

.prompt-textarea {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  color: var(--text-primary);
  font-size: 13px;
  resize: none;
  line-height: 1.5;
  font-family: inherit;
}

.send-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--color-primary);
  border: none;
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 14px;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.2s;
}

.send-btn:disabled {
  background: var(--border-color);
  color: var(--text-muted);
  cursor: not-allowed;
}

.send-btn:not(:disabled):hover {
  opacity: 0.9;
  transform: scale(1.05);
}

.input-footnote {
  text-align: center;
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 5px;
}

/* ════════════ 简化版设置弹窗样式 ════════════ */
.preset-links {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.preset-label {
  font-size: 11px;
  color: var(--text-muted);
}

.preset-tag {
  cursor: pointer;
  transition: all 0.2s;
}
.preset-tag:hover {
  color: var(--color-primary);
  border-color: var(--color-primary);
}

.model-select-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.advanced-collapse {
  margin-top: 14px;
  border-radius: 6px;
  border: 1px dashed var(--border-color);
  padding: 0 10px;
}

.slider-row {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
}

.slider-val {
  font-family: 'Roboto Mono', monospace;
  color: var(--color-primary);
  width: 28px;
}

.form-tip {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
  display: block;
  line-height: 1.4;
}

.text-ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Markdown 辅助 */
:deep(.md-h1), :deep(.md-h2), :deep(.md-h3) { margin: 8px 0 4px; color: var(--color-primary); }
:deep(.md-code) { background: var(--color-primary-soft); padding: 2px 6px; border-radius: 3px; color: var(--color-cyan); }
:deep(.md-li), :deep(.md-oli) { margin-left: 16px; }

::-webkit-scrollbar { width: 4px; height: 4px; }
::-webkit-scrollbar-thumb { background: var(--border-color); border-radius: 2px; }
::-webkit-scrollbar-track { background: transparent; }
</style>
