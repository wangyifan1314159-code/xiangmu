import { ref } from 'vue'

export interface WsDeviceData {
  type: string; deviceId: string; sensorId: string
  value: number; unit: string; timestamp: string
}
export interface WsAlert {
  deviceId: string; level: string; title: string; timestamp: string
}

let Client: any = null
let SockJS: any = null

async function loadSockJS() {
  try {
    // @ts-ignore sockjs-client 没有可用的类型声明
    const mod = await import('sockjs-client')
    SockJS = mod.default || mod
  } catch {
    /* ignore */
  }
}
async function loadStomp() {
  try { 
    const mod = await import('@stomp/stompjs'); Client = mod.Client 
  } catch {
     /* ignore */ 
    }
}

// 预加载
loadSockJS()
loadStomp()

const connected = ref(false)
let stompClient: any = null
const deviceCallbacks = new Map<string, Set<(data: WsDeviceData) => void>>()
const alertCallbacks = new Set<(data: WsAlert) => void>()

// 已注册需要订阅的设备（连接建立/重连后统一订阅；服务端会按归属校验每个设备）
const subscribedDevices = new Set<string>()
const deviceSubscriptions = new Map<string, any>()

function handleDeviceMessage(msg: any) {
  try {
    const data: WsDeviceData = JSON.parse(msg.body)
    deviceCallbacks.get(data.deviceId)?.forEach(fn => fn(data))
    deviceCallbacks.get('*')?.forEach(fn => fn(data))
  } catch { /* ignore */ }
}

/** 订阅某个设备的实时数据（幂等；未连接时登记，连接后自动补订阅） */
function subscribeDevice(deviceId: string) {
  subscribedDevices.add(deviceId)
  if (stompClient?.active && !deviceSubscriptions.has(deviceId)) {
    try {
      const sub = stompClient.subscribe(`/topic/device/${deviceId}`, handleDeviceMessage)
      deviceSubscriptions.set(deviceId, sub)
    } catch { /* ignore */ }
  }
}

/** 批量订阅（设备列表刷新后调用） */
function subscribeDevices(deviceIds: string[]) {
  deviceIds.forEach(subscribeDevice)
}

/** 取消订阅某个设备 */
function unsubscribeDevice(deviceId: string) {
  subscribedDevices.delete(deviceId)
  const sub = deviceSubscriptions.get(deviceId)
  if (sub) {
    try { sub.unsubscribe() } catch { /* ignore */ }
    deviceSubscriptions.delete(deviceId)
  }
}

/** 连接后：订阅用户定向告警队列 + 所有已登记设备 */
function subscribeAll() {
  if (!stompClient?.active) return
  try {
    // 告警：用户定向队列（/user/queue/alert），只收到自己的告警
    stompClient.subscribe('/user/queue/alert', (msg: any) => {
      try {
        const alert: WsAlert = JSON.parse(msg.body)
        alertCallbacks.forEach(fn => fn(alert))
      } catch { /* ignore */ }
    })
  } catch { /* ignore */ }
  for (const id of subscribedDevices) {
    if (!deviceSubscriptions.has(id)) {
      try {
        const sub = stompClient.subscribe(`/topic/device/${id}`, handleDeviceMessage)
        deviceSubscriptions.set(id, sub)
      } catch { /* ignore */ }
    }
  }
}

export function useWebSocket() {
  function connect() {
    if (!SockJS || !Client) return  // 库未加载，静默降级
    if (stompClient?.active) return

    // 服务端要求 STOMP CONNECT 帧携带有效 JWT，未登录不建立连接
    const token = localStorage.getItem('iot_token')
    if (!token) return

    const baseUrl = window.location.protocol + '//' + window.location.host
    try {
      stompClient = new Client({
        webSocketFactory: () => new SockJS(baseUrl + '/ws'),
        connectHeaders: { Authorization: `Bearer ${token}` },
        reconnectDelay: 3000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        onConnect: () => {
          connected.value = true
          console.log('[WS] Connected')
          subscribeAll()
        },
        onDisconnect: () => {
          connected.value = false
          deviceSubscriptions.clear()
        },
        onStompError: () => { /* ignore */ }
      })
      stompClient.activate()
    } catch { /* WebSocket 不可用时静默降级到轮询 */ }
  }

  function disconnect() {
    try { stompClient?.deactivate() } catch { /* ignore */ }
    connected.value = false
    deviceSubscriptions.clear()
  }

  function onDeviceData(deviceId: string, fn: (data: WsDeviceData) => void) {
    if (!deviceCallbacks.has(deviceId)) deviceCallbacks.set(deviceId, new Set())
    deviceCallbacks.get(deviceId)!.add(fn)
    return () => deviceCallbacks.get(deviceId)?.delete(fn)
  }

  function onAllDeviceData(fn: (data: WsDeviceData) => void) {
    return onDeviceData('*', fn)
  }

  function onAlert(fn: (data: WsAlert) => void) {
    alertCallbacks.add(fn)
    return () => { alertCallbacks.delete(fn) }
  }

  return {
    connected, connect, disconnect,
    subscribeDevice, subscribeDevices, unsubscribeDevice,
    onDeviceData, onAllDeviceData, onAlert
  }
}
