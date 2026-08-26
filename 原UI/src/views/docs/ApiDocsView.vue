<script setup lang="ts">
import { ref, computed } from 'vue'
import { Upload, Download, Key, Promotion, Connection, SwitchButton, Document, Monitor } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

interface ParamDef { name: string; type: string; required?: boolean; desc: string }
interface ApiDef {
  method: string; path: string; title: string; desc: string; auth: string; id: string
  example: string; response: string
  pathParams?: { name: string; type: string; desc: string }[]
  queryParams?: ParamDef[]
  bodyParams?: ParamDef[]
  headers?: { name: string; desc: string; required: boolean }[]
}

const activeTab = ref('quick')
const copiedId = ref('')
const BASE_URL = window.location.origin
const activeSection = computed(() => apiSections.find(s => s.id === activeTab.value))
const apis = computed<ApiDef[]>(() => activeSection.value?.apis || [])

function copyCode(text: string, id?: string) {
  navigator.clipboard.writeText(text)
  if (id) {
    copiedId.value = id
    setTimeout(() => copiedId.value = '', 1500)
  }
  ElMessage.success({ message: '已复制', duration: 1200 })
}

const quickTests = [
  {
    title: '上传光照数据',
    icon: Upload,
    lang: 'curl',
    code: `curl -X POST "${BASE_URL}/api/data/dev_b087404c" \\
  -H "X-Api-Key: your_device_api_key" \\
  -H "Content-Type: application/json" \\
  -d '{"sensorId": "s_1781530543162", "value": 55.5}'`,
    id: 'quick-upload'
  },
  {
    title: '查询最新数据',
    icon: Download,
    lang: 'curl',
    code: `curl -H "X-Api-Key: your_device_api_key" \\
  "${BASE_URL}/api/data/dev_b087404c/latest?sensorId=s_1781530543162&limit=5"`,
    id: 'quick-query'
  },
  {
    title: '查询设备详情',
    icon: Document,
    lang: 'curl',
    code: `curl -H "X-Api-Key: your_device_api_key" \\
  "${BASE_URL}/api/devices/dev_b087404c"`,
    id: 'quick-device'
  },
  {
    title: '发送执行器指令',
    icon: SwitchButton,
    lang: 'curl',
    code: `curl -X POST "${BASE_URL}/api/data/dev_414305e8/command" \\
  -H "X-Api-Key: your_device_api_key" \\
  -H "Content-Type: application/json" \\
  -d '{"command": "1", "params": {"actuator": "电动开关"}}'`,
    id: 'quick-command'
  },
  {
    title: 'Python 上传示例',
    icon: Promotion,
    lang: 'python',
    code: `import requests

resp = requests.post(
    "${BASE_URL}/api/data/dev_b087404c",
    json={"sensorId": "s_1781530543162", "value": 55.5},
    headers={"X-Api-Key": "your_device_api_key"},
    timeout=5
)
print(resp.json())`,
    id: 'quick-python'
  }
]

const apiSections = [
  {
    id: 'upload',
    title: '数据上报',
    icon: Upload,
    desc: '设备向平台发送传感器数据',
    apis: [
      {
        method: 'POST', path: '/api/data/{deviceId}',
        title: '上报传感器数据',
        desc: '向指定设备上报传感器读数。后端自动写入时序数据库、更新实时缓存、触发告警评估。',
        auth: 'X-Api-Key',
        pathParams: [{ name: 'deviceId', type: 'string', desc: '设备 ID（如 dev_b087404c）' }],
        bodyParams: [
          { name: 'sensorId', type: 'string', required: true, desc: '传感器 ID（如 s_1781530543162）' },
          { name: 'value', type: 'number', required: true, desc: '传感器读数值' }
        ] as ParamDef[],
        headers: [
          { name: 'X-Api-Key', desc: '设备 API Key（从设备详情页获取）', required: true },
          { name: 'Content-Type', desc: 'application/json', required: true }
        ],
        example: `curl -X POST "${BASE_URL}/api/data/dev_b087404c" \\
  -H "X-Api-Key: your_device_api_key" \\
  -H "Content-Type: application/json" \\
  -d '{"sensorId": "s_1781530543162", "value": 55.5}'`,
        response: `{
  "success": true,
  "data": {
    "deviceId": "dev_b087404c",
    "sensorId": "s_1781530543162",
    "value": 55.5,
    "timestamp": "2026-06-16T10:30:00"
  }
}`,
        id: 'api-upload'
      },
      {
        method: 'POST', path: '/api/data/{deviceId}/command',
        title: '下发控制指令',
        desc: '向执行器发送控制命令（1=ON, 0=OFF, toggle=翻转）。后端自动更新执行器状态。',
        auth: 'X-Api-Key',
        pathParams: [{ name: 'deviceId', type: 'string', desc: '设备 ID' }],
        bodyParams: [
          { name: 'command', type: 'string', required: true, desc: '指令: "1"/"0"/"on"/"off"/"toggle"' },
          { name: 'params.actuator', type: 'string', required: false, desc: '执行器名称（与设备页一致）' }
        ] as ParamDef[],
        example: `curl -X POST "${BASE_URL}/api/data/dev_414305e8/command" \\
  -H "X-Api-Key: your_device_api_key" \\
  -H "Content-Type: application/json" \\
  -d '{"command": "1", "params": {"actuator": "电动开关"}}'`,
        response: `{
  "success": true,
  "data": {
    "message": "命令 [1] → 电动开关 已ON",
    "deviceId": "dev_414305e8",
    "command": "1"
  }
}`,
        id: 'api-command'
      }
    ]
  },
  {
    id: 'query',
    title: '数据查询',
    icon: Download,
    desc: '从平台查询已上报的传感器数据',
    apis: [
      {
        method: 'GET', path: '/api/data/{deviceId}/latest',
        title: '获取最新数据',
        desc: '返回指定传感器最新 N 条读数，按时间倒序。适合轮询获取实时值。',
        auth: 'X-Api-Key',
        pathParams: [{ name: 'deviceId', type: 'string', desc: '设备 ID' }],
        queryParams: [
          { name: 'sensorId', type: 'string', required: false, desc: '传感器 ID（不填返回全部传感器）' },
          { name: 'limit', type: 'int', required: false, desc: '返回条数（默认 10）' }
        ] as ParamDef[],
        example: `curl -H "X-Api-Key: your_device_api_key" \\
  "${BASE_URL}/api/data/dev_b087404c/latest?sensorId=s_1781530543162&limit=5"`,
        response: `{
  "success": true,
  "data": [
    { "value": 55.5, "sensorId": "s_1781530543162", "timestamp": "2026-06-16T10:30:00" },
    { "value": 54.2, "sensorId": "s_1781530543162", "timestamp": "2026-06-16T10:29:57" }
  ]
}`,
        id: 'api-latest'
      },
      {
        method: 'GET', path: '/api/data/{deviceId}',
        title: '时间范围查询',
        desc: '按起止时间查询历史数据，返回完整 DataPoint 对象（含 id、ownerId 等字段）。',
        auth: 'X-Api-Key',
        pathParams: [{ name: 'deviceId', type: 'string', desc: '设备 ID' }],
        queryParams: [
          { name: 'sensorId', type: 'string', required: false, desc: '传感器 ID' },
          { name: 'from', type: 'ISO datetime', required: false, desc: '起始时间（如 2026-06-16T00:00:00）' },
          { name: 'to', type: 'ISO datetime', required: false, desc: '结束时间' },
          { name: 'limit', type: 'int', required: false, desc: '返回条数（默认 200）' }
        ] as ParamDef[],
        example: `curl -H "X-Api-Key: your_device_api_key" \\
  "${BASE_URL}/api/data/dev_b087404c?sensorId=s_1781530543162&from=2026-06-16T00:00:00&to=2026-06-16T12:00:00&limit=100"`,
        response: `{
  "success": true,
  "data": [
    { "id": 123, "deviceId": "dev_b087404c", "sensorId": "s_1781530543162",
      "value": 55.5, "timestamp": "2026-06-16T10:30:00" }
  ]
}`,
        id: 'api-range'
      },
      {
        method: 'GET', path: '/api/data/{deviceId}/history',
        title: '聚合查询（降采样）',
        desc: '按时间间隔聚合，返回 avg/max/min。用于生成趋势图。',
        auth: 'X-Api-Key',
        pathParams: [{ name: 'deviceId', type: 'string', desc: '设备 ID' }],
        queryParams: [
          { name: 'sensorId', type: 'string', required: false, desc: '传感器 ID' },
          { name: 'from', type: 'ISO datetime', required: true, desc: '起始时间' },
          { name: 'to', type: 'ISO datetime', required: true, desc: '结束时间' },
          { name: 'interval', type: 'string', required: false, desc: '聚合间隔: 5m / 1h / 1d' }
        ] as ParamDef[],
        example: `curl -H "X-Api-Key: your_device_api_key" \\
  "${BASE_URL}/api/data/dev_b087404c/history?from=2026-06-16T00:00:00&to=2026-06-16T12:00:00&interval=5m"`,
        response: `{
  "success": true,
  "data": [
    { "ts": "2026-06-16T10:00:00", "avg_val": 24.5, "max_val": 26.1, "min_val": 23.8 },
    { "ts": "2026-06-16T10:05:00", "avg_val": 25.1, "max_val": 26.3, "min_val": 24.2 }
  ]
}`,
        id: 'api-history'
      }
    ]
  },
  {
    id: 'device',
    title: '设备信息',
    icon: Document,
    desc: '设备管理相关的查询接口',
    apis: [
      {
        method: 'GET', path: '/api/devices',
        title: '获取所有设备',
        desc: '返回当前用户的所有设备列表（含传感器实时值）。',
        auth: 'Bearer Token',
        example: `curl -H "Authorization: Bearer {token}" \\
  "${BASE_URL}/api/devices"`,
        response: `{
  "success": true,
  "data": [{
    "deviceId": "dev_b087404c",
    "name": "光照传感器",
    "status": "ONLINE",
    "sensors": [{ "id": "s_1781530543162", "name": "光照传感器", "value": 55.5 }],
    "apiKey": "your_device_api_key..."
  }]
}`,
        id: 'api-devices'
      },
      {
        method: 'GET', path: '/api/devices/{deviceId}',
        title: '获取设备详情',
        desc: '返回单个设备的完整信息，含 API Key、传感器列表和实时值。',
        auth: 'X-Api-Key 或 Bearer Token',
        pathParams: [{ name: 'deviceId', type: 'string', desc: '设备 ID' }],
        example: `curl -H "X-Api-Key: your_device_api_key" \\
  "${BASE_URL}/api/devices/dev_b087404c"`,
        response: `{
  "success": true,
  "data": {
    "deviceId": "dev_b087404c",
    "name": "光照传感器", "type": "光照 (Light)",
    "status": "ONLINE", "apiKey": "your_device_api_key...",
    "sensors": [{
      "id": "s_1781530543162", "name": "光照传感器",
      "type": "light", "value": 55.5, "minVal": 0, "maxVal": 100
    }],
    "lastActive": "2026-06-16T10:30:00"
  }
}`,
        id: 'api-device-detail'
      }
    ]
  },
  {
    id: 'mqtt',
    title: 'MQTT 接入',
    icon: Connection,
    desc: '通过 MQTT 协议上报数据 / 下发指令（低功耗设备推荐，可用 MQTTX 等客户端直接联调）',
    apis: [
      {
        method: 'CONN', path: 'tcp://{服务器IP}:1883',
        title: 'Broker 连接参数',
        desc: '平台内置 EMQX Broker，允许匿名连接（无需用户名密码）。MQTTX / 程序客户端按以下参数建立连接。',
        auth: '匿名连接',
        example: `# MQTTX 连接配置
协议:     mqtt:// (TCP)
地址:     服务器 IP（本机部署填 127.0.0.1）
端口:     1883
用户名:   留空（匿名）
密码:     留空
MQTT 版本: 3.1.1
QoS:      1（推荐）

# EMQX 管理控制台（可选，查看连接数 / 消息吞吐）
http://{服务器IP}:18083   账号 admin / 密码 public`,
        response: `平台后端已订阅以下通配主题：
iot/+/telemetry    传感器数据上报
iot/+/status       设备状态上报
iot/+/command      执行器指令

调试技巧：客户端订阅 iot/# 可回显自己发布的全部消息`,
        id: 'mqtt-conn'
      },
      {
        method: 'PUB', path: 'iot/{deviceId}/telemetry',
        title: '上报传感器数据',
        desc: 'QoS 1 发布。平台接收后写入存储、WebSocket 实时推送前端页面、触发告警评估。与 REST 上报等效，二选一即可。',
        auth: '无需认证（按 deviceId 归属）',
        bodyParams: [
          { name: 'sensorId', type: 'string', required: true, desc: '传感器 ID（设备详情页可复制）' },
          { name: 'value', type: 'number', required: true, desc: '传感器读数值' }
        ] as ParamDef[],
        example: `# MQTTX：Topic 填 iot/dev_b087404c/telemetry，报文填 ↓
{"sensorId": "s_1781530543162", "value": 55.5}

# mosquitto_pub（装有 mosquitto-clients 时可直接运行）
mosquitto_pub -h 127.0.0.1 -p 1883 -q 1 \\
  -t "iot/dev_b087404c/telemetry" \\
  -m '{"sensorId": "s_1781530543162", "value": 55.5}'`,
        response: `平台处理链路：
1. 解析 sensorId / value，按 deviceId 匹配设备归属
2. 写入存储（TDengine，不可用时降级 PostgreSQL）
3. WebSocket 实时推送到前端页面
4. 触发告警规则评估`,
        id: 'mqtt-telemetry'
      },
      {
        method: 'PUB', path: 'iot/{deviceId}/command',
        title: '下发执行器指令',
        desc: 'QoS 1 发布。command 大小写不敏感；actuator 必须填执行器「名称」（与设备详情页显示一致，不是 ID）。',
        auth: '无需认证（按 deviceId 归属）',
        bodyParams: [
          { name: 'command', type: 'string', required: true, desc: '指令: "on" / "off" / "toggle"（也接受 "1" / "0"）' },
          { name: 'actuator', type: 'string', required: true, desc: '执行器名称（与设备页一致，如 "电动开关"）' }
        ] as ParamDef[],
        example: `# MQTTX：Topic 填 iot/dev_414305e8/command，报文填 ↓
{"command": "on", "actuator": "电动开关"}

# mosquitto_pub
mosquitto_pub -h 127.0.0.1 -p 1883 -q 1 \\
  -t "iot/dev_414305e8/command" \\
  -m '{"command": "on", "actuator": "电动开关"}'`,
        response: `平台处理链路：
1. 更新执行器状态（value → 1.0 开 / 0.0 关）
2. 写入指令历史记录（可历史查询）
3. WebSocket 推送前端，控制面板立即刷新`,
        id: 'mqtt-command'
      },
      {
        method: 'PUB', path: 'iot/{deviceId}/status',
        title: '上报设备状态',
        desc: 'QoS 1 发布。更新设备在线 / 离线状态，设备列表实时刷新。',
        auth: '无需认证（按 deviceId 归属）',
        bodyParams: [
          { name: 'status', type: 'string', required: true, desc: '设备状态: "ONLINE" / "OFFLINE"' }
        ] as ParamDef[],
        example: `# MQTTX：Topic 填 iot/dev_b087404c/status，报文填 ↓
{"status": "OFFLINE"}

# mosquitto_pub
mosquitto_pub -h 127.0.0.1 -p 1883 -q 1 \\
  -t "iot/dev_b087404c/status" \\
  -m '{"status": "OFFLINE"}'`,
        response: `平台处理链路：
1. 更新数据库中设备 status 字段
2. WebSocket 推送前端，设备列表状态即时变化`,
        id: 'mqtt-status'
      }
    ]
  },
  {
    id: 'tcp',
    title: 'TCP 接入',
    icon: Monitor,
    desc: 'Netty TCP 长连接接入通道（端口 1884），适合 485 串口采集程序 / 单片机上位机直连，JSON 行协议（每帧一个 JSON，以 \\n 结尾）',
    apis: [
      {
        method: 'CONN', path: 'tcp://{服务器IP}:1884',
        title: 'TCP 连接参数与鉴权',
        desc: '建立 TCP 连接后必须在 30 秒内发送 auth 帧，否则连接被断开。deviceId 与 apiKey 在设备详情页获取。',
        auth: 'deviceId + apiKey',
        bodyParams: [
          { name: 'type', type: 'string', required: true, desc: '固定 "auth"' },
          { name: 'deviceId', type: 'string', required: true, desc: '设备 ID（如 dev_b087404c）' },
          { name: 'apiKey', type: 'string', required: true, desc: '设备 API Key（与 X-Api-Key 相同）' }
        ] as ParamDef[],
        example: `# 连接：nc {服务器IP} 1884 或任意 TCP 客户端
# 发送（注意结尾必须带换行 \\n）：
{"type":"auth","deviceId":"dev_b087404c","apiKey":"your_device_api_key"}\\n

# Java Socket 示例
Socket socket = new Socket("127.0.0.1", 1884);
Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
out.write("{\\"type\\":\\"auth\\",\\"deviceId\\":\\"dev_b087404c\\",\\"apiKey\\":\\"your_device_api_key\\"}\\n");
out.flush();`,
        response: `{"type":"auth_result","success":true,"deviceId":"dev_b087404c","message":"认证成功"}

# 失败时服务端主动断开：
{"type":"auth_result","success":false,"message":"设备ID或API Key不正确"}`,
        id: 'tcp-conn'
      },
      {
        method: 'SEND', path: 'telemetry 帧（设备 → 平台）',
        title: '上报传感器数据',
        desc: '鉴权通过后发送。处理链路与 MQTT/REST 上报完全一致：TDengine 时序落库 → Redis 缓存 → 告警评估 → WebSocket 推送前端。485 采集程序将协议帧拆解出的风速/温湿度/甲烷等物理量封装为本帧上送。',
        auth: '需先完成 auth',
        bodyParams: [
          { name: 'type', type: 'string', required: true, desc: '固定 "telemetry"' },
          { name: 'sensorId', type: 'string', required: true, desc: '传感器 ID' },
          { name: 'value', type: 'number', required: true, desc: '物理量数值（甲烷 ppm / 风速 m/s 等）' },
          { name: 'sensorType', type: 'string', required: false, desc: '类型: temperature / humidity / methane / wind_speed 等' },
          { name: 'unit', type: 'string', required: false, desc: '单位: °C、%RH、ppm、m/s' }
        ] as ParamDef[],
        example: `{"type":"telemetry","sensorId":"s_001","value":25.5,"sensorType":"temperature","unit":"°C"}\\n
{"type":"telemetry","sensorId":"s_002","value":1200,"sensorType":"methane","unit":"ppm"}\\n`,
        response: `无逐条回执。数据可在前端实时页面与历史曲线中查看；
告警规则（如 甲烷 > 1000 ppm）命中时自动产生告警记录并推送。`,
        id: 'tcp-telemetry'
      },
      {
        method: 'SEND', path: 'status / command_result 帧（设备 → 平台）',
        title: '上报设备状态 / 指令回执',
        desc: 'status 更新设备在线状态；command_result 用于回复平台下发的指令执行结果（可选）。',
        auth: '需先完成 auth',
        bodyParams: [
          { name: 'type', type: 'string', required: true, desc: '"status" 或 "command_result"' },
          { name: 'status', type: 'string', required: false, desc: '"ONLINE" / "OFFLINE"（type=status 时必填）' },
          { name: 'command', type: 'string', required: false, desc: '被回执的指令名（type=command_result 时）' },
          { name: 'success', type: 'boolean', required: false, desc: '执行是否成功（type=command_result 时）' }
        ] as ParamDef[],
        example: `{"type":"status","status":"ONLINE"}\\n
{"type":"command_result","command":"on","success":true,"message":"executed"}\\n`,
        response: `status 帧处理后设备列表状态即时刷新；
command_result 当前记录到平台日志，可扩展写入指令历史。`,
        id: 'tcp-status'
      },
      {
        method: 'RECV', path: 'command 帧（平台 → 设备）',
        title: '接收下行控制指令',
        desc: '通过 REST POST /api/data/{deviceId}/command 或 MQTT 下发指令时，若设备存在在线 TCP 连接，平台自动透传本帧。设备执行后建议回复 command_result。',
        auth: '—',
        example: `// 平台下发（JSON 行，UTF-8）：
{"type":"command","command":"on","params":{"actuator":"风扇"},"timestamp":1730000000000}\\n`,
        response: `设备侧解析 command 与 params 后驱动执行机构，
再回发 {"type":"command_result","command":"on","success":true}\\n`,
        id: 'tcp-command'
      },
      {
        method: 'GET', path: '/api/tcp/status',
        title: '查询 TCP 通道状态',
        desc: '返回 TCP 通道是否启用及当前在线设备数。app.tcp.enabled=false 时 enabled 返回 false。',
        auth: 'Bearer Token',
        example: `curl -H "Authorization: Bearer {token}" \\
  "${BASE_URL}/api/tcp/status"`,
        response: `{
  "success": true,
  "data": { "enabled": true, "onlineDevices": 3 }
}`,
        id: 'tcp-rest-status'
      },
      {
        method: 'GET', path: '/api/tcp/connections',
        title: '在线连接列表 / 强制断开',
        desc: '列出当前 TCP 在线连接实例（普通用户仅见自己设备，管理员见全部）。管理员可 DELETE /api/tcp/connections/{deviceId} 强制断开指定设备。',
        auth: 'Bearer Token（断开仅限 ADMIN）',
        example: `curl -H "Authorization: Bearer {token}" \\
  "${BASE_URL}/api/tcp/connections"

# 强制断开（管理员）
curl -X DELETE -H "Authorization: Bearer {token}" \\
  "${BASE_URL}/api/tcp/connections/dev_b087404c"`,
        response: `{
  "success": true,
  "data": [{ "deviceId": "dev_b087404c", "remoteAddr": "192.168.1.50:52333", "authAt": "..." }]
}`,
        id: 'tcp-rest-connections'
      }
    ]
  }
]
</script>

<template>
  <div class="api-docs">
    <!-- Header -->
    <div class="page-header">
      <div>
        <h2>数据接口文档</h2>
        <span class="header-desc">设备数据上报、查询与命令下发 REST API 参考</span>
      </div>
    </div>

    <!-- 快速测试 -->
    <el-card class="quick-card" shadow="never">
      <template #header>
        <div class="card-title">
          <el-icon :size="18" color="var(--accent)"><Promotion /></el-icon>
          <span>快速测试</span>
          <el-tag size="small" type="success" effect="dark" class="quick-badge">直接复制运行</el-tag>
        </div>
      </template>
      <div class="quick-list">
        <div v-for="test in quickTests" :key="test.id" class="quick-item">
          <div class="quick-item-header">
            <el-icon :size="14" color="var(--accent)"><component :is="test.icon" /></el-icon>
            <span class="quick-item-title">{{ test.title }}</span>
            <el-tag size="small" effect="plain" class="quick-lang">{{ test.lang }}</el-tag>
          </div>
          <div class="quick-code-wrapper">
            <pre class="quick-code"><code>{{ test.code }}</code></pre>
            <el-button
              size="small"
              :type="copiedId === test.id ? 'success' : 'default'"
              class="quick-copy-btn"
              @click="copyCode(test.code, test.id)"
            >
              {{ copiedId === test.id ? '已复制 ✓' : '复制' }}
            </el-button>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 认证说明 -->
    <el-card class="auth-card" shadow="never">
      <template #header>
        <div class="card-title">
          <el-icon :size="18" color="var(--accent)"><Key /></el-icon>
          <span>认证方式</span>
        </div>
      </template>
      <el-row :gutter="24">
        <el-col :span="12">
          <div class="auth-box">
            <div class="auth-box-header">
              <el-tag type="success" effect="dark" size="small">推荐</el-tag>
              <span>X-Api-Key（设备 Key）</span>
            </div>
            <code class="code-inline">-H "X-Api-Key: {apiKey}"</code>
            <p class="auth-box-desc">在设备详情页可查看复制。适用于设备/脚本端直接调用。</p>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="auth-box">
            <div class="auth-box-header">
              <span>Bearer Token（用户登录）</span>
            </div>
            <code class="code-inline">-H "Authorization: Bearer {token}"</code>
            <p class="auth-box-desc">通过 POST /api/auth/login 获取。适用于 Web 前端调用。</p>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 接口详情 -->
    <div class="api-section">
      <div class="section-tabs">
        <el-radio-group v-model="activeTab" size="small">
          <el-radio-button value="quick"><el-icon :size="13"><Promotion /></el-icon> 快速测试</el-radio-button>
          <el-radio-button value="upload"><el-icon :size="13"><Upload /></el-icon> 数据上报</el-radio-button>
          <el-radio-button value="query"><el-icon :size="13"><Download /></el-icon> 数据查询</el-radio-button>
          <el-radio-button value="device"><el-icon :size="13"><Document /></el-icon> 设备信息</el-radio-button>
          <el-radio-button value="mqtt"><el-icon :size="13"><Connection /></el-icon> MQTT 接入</el-radio-button>
          <el-radio-button value="tcp"><el-icon :size="13"><Monitor /></el-icon> TCP 接入</el-radio-button>
        </el-radio-group>
      </div>

      <div v-if="activeSection" :key="activeSection.id" class="section-body">
        <div class="section-header">
          <el-icon :size="20" color="var(--accent)"><component :is="activeSection.icon" /></el-icon>
          <div>
            <h3 class="section-title">{{ activeSection.title }}</h3>
            <p class="section-sub">{{ activeSection.desc }}</p>
          </div>
        </div>

        <el-card
          v-for="api in apis"
          :key="api.id"
          class="api-card"
          shadow="never"
        >
            <!-- 接口概要 -->
            <div class="api-card-top">
              <div class="api-method-row">
                <el-tag :type="api.method === 'POST' ? 'success' : api.method === 'GET' ? 'primary' : 'warning'" size="small" effect="dark">
                  {{ api.method }}
                </el-tag>
                <code class="api-path-display">{{ api.path }}</code>
                <el-tag size="small" effect="plain" type="info">{{ api.auth }}</el-tag>
              </div>
              <h4 class="api-card-title">{{ api.title }}</h4>
              <p class="api-card-desc">{{ api.desc }}</p>
            </div>

            <!-- 路径参数 -->
            <div v-if="api.pathParams" class="api-table-block">
              <span class="api-section-label">路径参数</span>
              <table class="param-table">
                <thead>
                  <tr><th>参数</th><th>类型</th><th>说明</th></tr>
                </thead>
                <tbody>
                  <tr v-for="p in api.pathParams" :key="p.name">
                    <td><code>{{ p.name }}</code></td>
                    <td><el-tag size="small" effect="plain">{{ p.type }}</el-tag></td>
                    <td>{{ p.desc }}</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <!-- Query 参数 -->
            <div v-if="api.queryParams" class="api-table-block">
              <span class="api-section-label">Query 参数</span>
              <table class="param-table">
                <thead>
                  <tr><th>参数</th><th>类型</th><th>必填</th><th>说明</th></tr>
                </thead>
                <tbody>
                  <tr v-for="p in api.queryParams" :key="p.name">
                    <td><code>{{ p.name }}</code></td>
                    <td><el-tag size="small" effect="plain">{{ p.type }}</el-tag></td>
                    <td><el-tag :type="p.required ? 'danger' : 'info'" size="small">{{ p.required ? '是' : '否' }}</el-tag></td>
                    <td>{{ p.desc }}</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <!-- Body 参数 -->
            <div v-if="api.bodyParams" class="api-table-block">
              <span class="api-section-label">Body 参数 (JSON)</span>
              <table class="param-table">
                <thead>
                  <tr><th>参数</th><th>类型</th><th>必填</th><th>说明</th></tr>
                </thead>
                <tbody>
                  <tr v-for="p in api.bodyParams" :key="p.name">
                    <td><code>{{ p.name }}</code></td>
                    <td><el-tag size="small" effect="plain">{{ p.type }}</el-tag></td>
                    <td><el-tag :type="p.required ? 'danger' : 'info'" size="small">{{ p.required ? '是' : '否' }}</el-tag></td>
                    <td>{{ p.desc }}</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <!-- 示例请求 -->
            <div class="code-section">
              <div class="code-section-header">
                <el-icon :size="14"><Connection /></el-icon>
                <span>示例请求</span>
                <el-button size="small" text type="primary" @click="copyCode(api.example, api.id)">复制</el-button>
              </div>
              <pre class="code-block"><code>{{ api.example }}</code></pre>
            </div>

            <!-- 响应示例 -->
            <div class="code-section">
              <div class="code-section-header">
                <span>响应示例</span>
                <el-button size="small" text type="primary" @click="copyCode(api.response, api.id + '-resp')">复制</el-button>
              </div>
              <pre class="code-block"><code>{{ api.response }}</code></pre>
            </div>
          </el-card>
        </div>
      </div>

    <!-- 错误码 -->
    <el-card class="error-card" shadow="never">
      <template #header>
        <div class="card-title">
          <el-icon :size="18" color="var(--warning)"><Key /></el-icon>
          <span>通用错误码</span>
        </div>
      </template>
      <table class="param-table">
        <thead>
          <tr><th>状态码</th><th>含义</th></tr>
        </thead>
        <tbody>
          <tr><td><el-tag type="success" size="small">200</el-tag></td><td>成功</td></tr>
          <tr><td><el-tag type="danger" size="small">401</el-tag></td><td>认证失败 — API Key 无效或 Token 过期</td></tr>
          <tr><td><el-tag type="danger" size="small">404</el-tag></td><td>设备/传感器不存在</td></tr>
          <tr><td><el-tag type="danger" size="small">500</el-tag></td><td>服务器内部错误</td></tr>
        </tbody>
      </table>
    </el-card>
  </div>
</template>

<style scoped>
.api-docs { max-width: 960px; margin: 0 auto; }

.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { margin: 0 0 4px; font-size: 22px; color: var(--text-primary); }
.header-desc { font-size: 13px; color: var(--text-muted); }

/* Card title */
.card-title { display: flex; align-items: center; gap: 8px; font-weight: 600; font-size: 15px; color: var(--text-primary); }
.quick-badge { margin-left: 8px; }

/* Quick test */
.quick-card, .auth-card, .error-card { margin-bottom: 20px; }

.quick-list { display: flex; flex-direction: column; gap: 14px; }

.quick-item { border: 1px solid var(--border-color); border-radius: 8px; overflow: hidden; }
.quick-item-header { display: flex; align-items: center; gap: 8px; padding: 10px 14px; background: var(--bg-hover); border-bottom: 1px solid var(--border-color); }
.quick-item-title { font-size: 14px; font-weight: 500; color: var(--text-primary); }
.quick-lang { margin-left: auto; }

.quick-code-wrapper { position: relative; }
.quick-code { padding: 14px 16px; margin: 0; font-family: 'Cascadia Code', 'Fira Code', monospace; font-size: 12.5px; line-height: 1.6; color: var(--text-primary); background: var(--bg-card); overflow-x: auto; white-space: pre; }
.quick-copy-btn { position: absolute; top: 8px; right: 8px; }

/* Auth */
.auth-box { border: 1px solid var(--border-color); border-radius: 8px; padding: 14px; }
.auth-box-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; font-size: 14px; font-weight: 500; color: var(--text-primary); }
.auth-box-desc { margin: 8px 0 0; font-size: 12px; color: var(--text-muted); }

.code-inline { font-family: 'Cascadia Code', 'Fira Code', monospace; font-size: 13px; color: var(--accent); background: var(--bg-hover); padding: 3px 10px; border-radius: 4px; word-break: break-all; }

/* API sections */
.api-section { margin-bottom: 20px; }

.section-tabs { margin-bottom: 16px; }

.section-header { display: flex; align-items: flex-start; gap: 12px; margin-bottom: 16px; padding-bottom: 14px; border-bottom: 1px solid var(--border-color); }
.section-title { margin: 0; font-size: 18px; color: var(--text-primary); }
.section-sub { margin: 4px 0 0; font-size: 13px; color: var(--text-muted); }

.api-cards, .section-body { display: flex; flex-direction: column; gap: 14px; }

.api-card { border: 1px solid var(--border-color); transition: border-color 0.2s; }
.api-card:hover { border-color: var(--accent); }

.api-card-top { margin-bottom: 14px; padding-bottom: 12px; border-bottom: 1px solid var(--border-light); }
.api-method-row { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.api-path-display { font-family: 'Cascadia Code', 'Fira Code', monospace; font-size: 14px; font-weight: 600; color: var(--text-primary); }
.api-card-title { margin: 0 0 4px; font-size: 15px; color: var(--text-primary); }
.api-card-desc { margin: 0; font-size: 13px; color: var(--text-muted); line-height: 1.5; }

/* Param tables */
.api-table-block { margin-bottom: 14px; }
.api-section-label { display: block; margin-bottom: 6px; font-size: 12px; font-weight: 600; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.5px; }

.param-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.param-table th { text-align: left; padding: 7px 10px; background: var(--bg-hover); color: var(--text-secondary); font-weight: 600; font-size: 12px; border-bottom: 1px solid var(--border-color); }
.param-table td { padding: 7px 10px; color: var(--text-primary); border-bottom: 1px solid var(--border-light); }
.param-table code { font-family: 'Cascadia Code', 'Fira Code', monospace; font-size: 12.5px; color: var(--accent); }

/* Code blocks */
.code-section { margin-bottom: 12px; }
.code-section-header { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; font-size: 12px; font-weight: 600; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.5px; }
.code-section-header .el-button { margin-left: auto; }

.code-block { background: var(--bg-hover); border: 1px solid var(--border-color); border-radius: 6px; padding: 14px 16px; margin: 0; font-family: 'Cascadia Code', 'Fira Code', monospace; font-size: 12.5px; line-height: 1.6; color: var(--text-primary); overflow-x: auto; white-space: pre; }
</style>
