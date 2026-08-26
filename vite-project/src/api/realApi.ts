import axios from 'axios'
import router from '../router'

const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: { 
    'Content-Type': 'application/json' 
  }
})

http.interceptors.request.use(config => {
  const token = localStorage.getItem('iot_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('iot_token')
      localStorage.removeItem('iot_current_user')
      router.push('/login')
    }
    const msg = err.response?.data?.message || err.message || '请求失败'
    return Promise.reject(new Error(msg))
  }
)

const actuatorTypeSet = new Set(['switch', 'relay', 'motor', 'valve', 'buzzer', 'led', 'fan', 'pump'])

function mapDevice(d: any) {
  const allSensors = (d.sensors || []).map(mapSensor)
  const sensors = allSensors.filter((s: any) => !actuatorTypeSet.has(s.type?.toLowerCase()))
  const actuators = allSensors
    .filter((s: any) => actuatorTypeSet.has(s.type?.toLowerCase()))
    .map((s: any) => ({
      id: s.id,
      name: s.name,
      type: s.type,
      value: s.value || 0,
      minVal: s.minVal != null ? s.minVal : 0,
      maxVal: s.maxVal != null ? s.maxVal : 1,
      isActuator: true,
      commandType: s.commandType || 'toggle',
      defaultValue: s.defaultValue || 'off',
      parameters: s.parameters || ''
    }))
  return {
    id: d.deviceId,
    name: d.name,
    type: d.type,
    status: (d.status || 'OFFLINE').toLowerCase(),
    location: d.location || '',
    lastActive: d.lastActive || d.createdAt,
    description: d.description || '',
    createdAt: d.createdAt,
    ownerId: d.ownerId ?? d.owner?.id ?? 0,
    apiKey: d.apiKey || '',
    sensors,
    actuators
  }
}

function mapSensor(s: any) {
  return {
    id: s.id,
    name: s.name,
    type: s.type,
    unit: s.unit || '',
    value: s.value || 0,
    min: s.minVal != null ? s.minVal : (s.min != null ? s.min : 0),
    max: s.maxVal != null ? s.maxVal : (s.max != null ? s.max : 100)
  }
}

function mapDeviceForBackend(d: any) {
  return {
    name: d.name,
    type: d.type,
    status: (d.status || 'offline').toUpperCase(),
    location: d.location,
    description: d.description,
    sensors: (d.sensors || []).map(mapSensorForBackend)
  }
}

function mapSensorForBackend(s: any) {
  return {
    id: s.id,
    name: s.name,
    type: s.type,
    unit: s.unit || '',
    value: s.value || 0,
    minVal: s.minVal != null ? s.minVal : (s.min != null ? s.min : 0),
    maxVal: s.maxVal != null ? s.maxVal : (s.max != null ? s.max : 100),
    ...(s.isActuator ? { isActuator: s.isActuator, commandType: s.commandType, defaultValue: s.defaultValue, parameters: s.parameters } : {})
  }
}

export const realApi = {
  async login(username: string, password: string) {
    const res = await http.post('/auth/login', { username, password })
    const { token, user } = res.data.data
    localStorage.setItem('iot_token', token)
    // 同步写入 mockApi 用户态，避免降级到 mock 时因"未登录"返回空设备列表
    localStorage.setItem('iot_current_user', JSON.stringify(user))
    return { user, token }
  },

  async register(username: string, email: string, password: string) {
    const res = await http.post('/auth/register', { username, email, password })
    return res.data.data
  },

  async sendVerificationCode(phone: string) {
    const res = await http.post('/auth/send-code', { phone })
    return res.data.data
  },

  async loginByPhone(phone: string, code: string) {
    const res = await http.post('/auth/login-by-phone', { phone, code })
    const { token, user } = res.data.data
    localStorage.setItem('iot_token', token)
    // 同步写入 mockApi 用户态，避免降级到 mock 时因"未登录"返回空设备列表
    localStorage.setItem('iot_current_user', JSON.stringify(user))
    return { user, token }
  },

  async getCurrentUser() {
    const res = await http.get('/auth/me')
    return res.data.data
  },

  async getDevices() {
    const res = await http.get('/devices')
    return res.data.data.map(mapDevice)
  },

  async getDeviceById(id: string) {
    const res = await http.get(`/devices/${id}`)
    return mapDevice(res.data.data)
  },

  async createDevice(data: any) {
    const res = await http.post('/devices', mapDeviceForBackend(data))
    return mapDevice(res.data.data)
  },

  async updateDevice(id: string, data: any) {
    const res = await http.put(`/devices/${id}`, mapDeviceForBackend(data))
    return mapDevice(res.data.data)
  },

  async regenerateDeviceApiKey(id: string) {
    const res = await http.post(`/devices/${id}/api-key/regenerate`)
    return res.data.data.apiKey as string
  },

  async deleteDevice(id: string) {
    await http.delete(`/devices/${id}`)
  },

  async updateDeviceStatus(id: string, status: string) {
    const res = await http.patch(`/devices/${id}/status`, {
      status: status.toUpperCase()
    })
    return mapDevice(res.data.data)
  },

  async sendCommand(deviceId: string, command: string, params?: Record<string, any>) {
    const res = await http.post(`/data/${deviceId}/command`, { command, params })
    return res.data.data
  },

  async getDeviceData(deviceId?: string, sensorId?: string, limit: number = 200,
                       from?: string, to?: string) {
    if (!deviceId) {
      const devRes = await http.get('/devices')
      const devices = devRes.data.data
      if (devices.length === 0) return []
      deviceId = devices[0].deviceId
    }
    const params: any = { limit }
    if (sensorId) params.sensorId = sensorId
    if (from) params.from = from
    if (to) params.to = to
    const res = await http.get(`/data/${deviceId}`, { params })
    return res.data.data
  },

  async getDeviceStats() {
    const res = await http.get('/devices/stats')
    const stats = res.data.data
    return {
      total: stats.total || 0,
      online: stats.online || 0,
      offline: stats.offline || 0,
      warning: stats.warning || 0
    }
  },

  async getSimulationStatus() {
    const res = await http.get('/simulation/status')
    return res.data.data
  },

  async startDataUpload(interval: number = 5) {
    const res = await http.post(`/simulation/upload/start?interval=${interval}`)
    return res.data
  },

  async stopDataUpload() {
    const res = await http.post('/simulation/upload/stop')
    return res.data
  },

  async startCommandDelivery(interval: number = 10) {
    const res = await http.post(`/simulation/delivery/start?interval=${interval}`)
    return res.data
  },

  async stopCommandDelivery() {
    const res = await http.post('/simulation/delivery/stop')
    return res.data
  },

  async stopAllSimulation() {
    const res = await http.post('/simulation/stop')
    return res.data
  },

  async triggerUploadOnce() {
    const res = await http.post('/simulation/upload/once')
    return res.data
  },

  async triggerDeliveryOnce() {
    const res = await http.post('/simulation/delivery/once')
    return res.data
  },

  async getCommandLogs(deviceId?: string) {
    const params = deviceId ? { deviceId } : {}
    const res = await http.get('/simulation/commands', { params })
    return res.data.data
  },

  // ========== 告警 ==========
  async getAlertRecords(params?: { deviceId?: string; status?: string; level?: string; page?: number; size?: number }) {
    const res = await http.get('/alerts/records', { params })
    return res.data.data
  },

  async acknowledgeAlert(id: number) {
    const res = await http.patch(`/alerts/records/${id}/acknowledge`)
    return res.data.data
  },

  async resolveAlert(id: number) {
    const res = await http.patch(`/alerts/records/${id}/resolve`)
    return res.data.data
  },

  async getAlertStats() {
    const res = await http.get('/alerts/stats')
    return res.data.data
  },

  // ========== TCP 连接实例 ==========
  /** 获取 TCP 在线连接实例列表（普通用户仅自己设备，管理员全部） */
  async getTcpConnections() {
    const res = await http.get('/tcp/connections')
    return res.data.data
  },

  /** 获取 TCP 通道状态（启用状态 + 在线设备数） */
  async getTcpStatus() {
    const res = await http.get('/tcp/status')
    return res.data.data
  },

  /** 强制断开设备 TCP 连接（仅管理员） */
  async disconnectTcpConnection(deviceId: string) {
    const res = await http.delete(`/tcp/connections/${deviceId}`)
    return res.data.data
  },

  async getBinaryTcpConnections() {
    const res = await http.get('/tcp/binary-connections')
    return res.data.data
  },

  async connectBinaryTcpClient(host: string, port: number, deviceId: string) {
    const res = await http.post('/tcp/binary-connections', { host, port, deviceId })
    return res.data.data
  },

  async disconnectBinaryTcpClient(id: string) {
    const res = await http.delete(`/tcp/binary-connections/${id}`)
    return res.data.data
  },

  async getTcpListeners() {
    const res = await http.get('/tcp/listeners')
    return res.data.data
  },

  async startTcpListener(port: number) {
    const res = await http.post('/tcp/listeners', { port })
    return res.data.data
  },

  async stopTcpListener(port: number) {
    const res = await http.delete(`/tcp/listeners/${port}`)
    return res.data.data
  }
}

export default realApi
