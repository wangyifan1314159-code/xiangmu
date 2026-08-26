
export interface User {
  id: number
  username: string
  email: string
  phone?: string
  password: string
  avatar?: string
  role: string
  createdAt: string
}

export interface Device {
  id: string
  name: string
  type: string
  status: 'online' | 'offline' | 'warning'
  location: string
  lastActive: string
  sensors: Sensor[]
  actuators?: any[]
  description?: string
  createdAt: string
  ownerId: number
}

export interface Sensor {
  id: string
  name: string
  type: string
  unit: string
  value: number
  min: number
  max: number
}

export interface DataPoint {
  id: string
  deviceId: string
  sensorId: string
  value: number
  timestamp: string
  ownerId: number
}

const STORAGE_KEYS = {
  USERS: 'iot_users',
  CURRENT_USER: 'iot_current_user',
  TOKEN: 'iot_token'
}

function getStorage<T>(key: string, defaultValue: T): T {
  const data = localStorage.getItem(key)
  return data ? JSON.parse(data) : defaultValue
}

function setStorage<T>(key: string, value: T): void {
  localStorage.setItem(key, JSON.stringify(value))
}

function generateId(): string {
  return Date.now().toString(36) + Math.random().toString(36).substr(2)
}

function delay(ms: number = 500): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms))
}

// ---- User-scoped storage helpers ----

function getCurrentUserId(): number | null {
  const u = getStorage<any>(STORAGE_KEYS.CURRENT_USER, null)
  return u?.id ?? null
}

function devicesKey(userId?: number): string {
  const id = userId ?? getCurrentUserId()
  if (!id) throw new Error('用户未登录')
  return `iot_devices_${id}`
}

function dataKey(userId?: number): string {
  const id = userId ?? getCurrentUserId()
  if (!id) throw new Error('用户未登录')
  return `iot_data_${id}`
}

// ---- Per-user default devices ----

const DEFAULT_DEVICE_TEMPLATES: Omit<Device, 'ownerId' | 'createdAt' | 'lastActive'>[] = [
  {
    id: 'dev_001',
    name: '温湿度传感器-A1',
    type: '温湿度传感器',
    status: 'online',
    location: '实验室1区',
    description: '用于监测实验室温度和湿度',
    sensors: [
      { id: 's_001', name: '温度', type: 'temperature', unit: '°C', value: 24.5, min: -10, max: 60 },
      { id: 's_002', name: '湿度', type: 'humidity', unit: '%RH', value: 65, min: 0, max: 100 }
    ]
  },
  {
    id: 'dev_002',
    name: '光照传感器-B2',
    type: '光照传感器',
    status: 'online',
    location: '大棚区A',
    description: '农业大棚光照监测',
    sensors: [
      { id: 's_003', name: '光照强度', type: 'light', unit: 'lux', value: 12500, min: 0, max: 100000 }
    ]
  },
  {
    id: 'dev_003',
    name: '空气质量监测-C1',
    type: '空气质量传感器',
    status: 'warning',
    location: '办公室',
    description: '室内空气质量监测设备',
    sensors: [
      { id: 's_004', name: 'PM2.5', type: 'pm25', unit: 'μg/m³', value: 35, min: 0, max: 500 },
      { id: 's_005', name: 'CO2', type: 'co2', unit: 'ppm', value: 800, min: 0, max: 5000 },
      { id: 's_006', name: 'TVOC', type: 'tvoc', unit: 'mg/m³', value: 0.5, min: 0, max: 10 }
    ]
  },
  {
    id: 'dev_004',
    name: '智能电表-D1',
    type: '电能监测',
    status: 'offline',
    location: '配电房',
    description: '配电房电能消耗监测',
    sensors: [
      { id: 's_007', name: '电压', type: 'voltage', unit: 'V', value: 220, min: 0, max: 380 },
      { id: 's_008', name: '电流', type: 'current', unit: 'A', value: 5.2, min: 0, max: 50 },
      { id: 's_009', name: '功率', type: 'power', unit: 'W', value: 1144, min: 0, max: 10000 }
    ]
  }
]

function ensureUserDevices(userId: number): void {
  const key = devicesKey(userId)
  if (!localStorage.getItem(key)) {
    const now = new Date().toISOString()
    const devices: Device[] = DEFAULT_DEVICE_TEMPLATES.map(t => ({
      ...t,
      ownerId: userId,
      createdAt: now,
      lastActive: now
    }))
    setStorage(key, devices)
  }
}

function ensureUserData(userId: number): void {
  const key = dataKey(userId)
  if (!localStorage.getItem(key)) {
    const now = Date.now()
    const mockData: DataPoint[] = []
    for (let i = 0; i < 100; i++) {
      mockData.push({
        id: generateId(),
        deviceId: 'dev_001',
        sensorId: 's_001',
        value: 20 + Math.random() * 10,
        timestamp: new Date(now - i * 60000).toISOString(),
        ownerId: userId
      })
    }
    setStorage(key, mockData)
  }
}

// ---- Global init (users table only) ----

function initUsers(): void {
  if (!localStorage.getItem(STORAGE_KEYS.USERS)) {
    const defaultUsers: User[] = [
      {
        id: 1,
        username: 'admin',
        email: 'admin@iot.com',
        password: 'admin123',
        role: 'admin',
        avatar: '',
        createdAt: new Date().toISOString()
      }
    ]
    setStorage(STORAGE_KEYS.USERS, defaultUsers)
  }
}

initUsers()

export const mockApi = {
  // ---- Auth ----

  async login(username: string, password: string) {
    await delay()
    const users = getStorage<User[]>(STORAGE_KEYS.USERS, [])
    const user = users.find(u => u.username === username && u.password === password)
    if (!user) {
      throw new Error('用户名或密码错误')
    }
    const token = 'token_' + generateId()
    const { password: _, ...userWithoutPassword } = user
    setStorage(STORAGE_KEYS.CURRENT_USER, userWithoutPassword)
    setStorage(STORAGE_KEYS.TOKEN, token)
    ensureUserDevices(user.id)
    ensureUserData(user.id)
    return { user: userWithoutPassword, token }
  },

  async register(username: string, email: string, password: string) {
    await delay()
    const users = getStorage<User[]>(STORAGE_KEYS.USERS, [])
    if (users.some(u => u.username === username)) {
      throw new Error('用户名已存在')
    }
    if (users.some(u => u.email === email)) {
      throw new Error('邮箱已被注册')
    }
    const newUser: User = {
      id: Date.now(),
      username,
      email,
      password,
      role: 'user',
      createdAt: new Date().toISOString()
    }
    users.push(newUser)
    setStorage(STORAGE_KEYS.USERS, users)
    // Pre-create device pool for the new user
    ensureUserDevices(newUser.id)
    ensureUserData(newUser.id)
    const { password: _, ...userWithoutPassword } = newUser
    return userWithoutPassword
  },

  async sendVerificationCode(phone: string) {
    await delay(300)
    const code = String(Math.floor(100000 + Math.random() * 900000))
    localStorage.setItem('iot_sms_code_' + phone, code)
    localStorage.setItem('iot_sms_code_time_' + phone, String(Date.now()))
    return { success: true, message: '验证码已发送' }
  },

  async loginByPhone(phone: string, code: string) {
    await delay()
    const savedCode = localStorage.getItem('iot_sms_code_' + phone)
    const savedTime = localStorage.getItem('iot_sms_code_time_' + phone)
    if (!savedCode || !savedTime) {
      throw new Error('请先获取验证码')
    }
    if (Date.now() - Number(savedTime) > 5 * 60 * 1000) {
      localStorage.removeItem('iot_sms_code_' + phone)
      localStorage.removeItem('iot_sms_code_time_' + phone)
      throw new Error('验证码已过期，请重新获取')
    }
    if (code !== savedCode) {
      throw new Error('验证码错误')
    }
    localStorage.removeItem('iot_sms_code_' + phone)
    localStorage.removeItem('iot_sms_code_time_' + phone)

    const users = getStorage<User[]>(STORAGE_KEYS.USERS, [])
    let user = users.find(u => u.phone === phone)
    if (!user) {
      user = {
        id: Date.now(),
        username: '用户' + phone.slice(-4),
        email: phone + '@iot.com',
        phone,
        password: '',
        role: 'user',
        createdAt: new Date().toISOString()
      }
      users.push(user)
      setStorage(STORAGE_KEYS.USERS, users)
    }
    const token = 'token_' + generateId()
    const { password: _, ...userWithoutPassword } = user
    setStorage(STORAGE_KEYS.CURRENT_USER, userWithoutPassword)
    setStorage(STORAGE_KEYS.TOKEN, token)
    ensureUserDevices(user.id)
    ensureUserData(user.id)
    return { user: userWithoutPassword, token }
  },

  async logout() {
    await delay(200)
    localStorage.removeItem(STORAGE_KEYS.CURRENT_USER)
    localStorage.removeItem(STORAGE_KEYS.TOKEN)
  },

  async getCurrentUser() {
    await delay(200)
    return getStorage(STORAGE_KEYS.CURRENT_USER, null)
  },

  async getToken() {
    return localStorage.getItem(STORAGE_KEYS.TOKEN)
  },

  // ---- Devices (user-scoped) ----

  async getDevices() {
    await delay(300)
    const userId = getCurrentUserId()
    if (!userId) return []
    return getStorage<Device[]>(devicesKey(userId), [])
  },

  async getDeviceById(id: string) {
    await delay(200)
    const userId = getCurrentUserId()
    if (!userId) return null
    const devices = getStorage<Device[]>(devicesKey(userId), [])
    return devices.find(d => d.id === id) || null
  },

  async createDevice(deviceData: Omit<Device, 'id' | 'createdAt' | 'lastActive' | 'ownerId'>) {
    await delay(500)
    const userId = getCurrentUserId()
    if (!userId) throw new Error('用户未登录')
    const devices = getStorage<Device[]>(devicesKey(userId), [])
    const newDevice: Device = {
      ...deviceData,
      id: 'dev_' + generateId(),
      ownerId: userId,
      createdAt: new Date().toISOString(),
      lastActive: new Date().toISOString(),
      sensors: deviceData.sensors || []
    }
    devices.push(newDevice)
    setStorage(devicesKey(userId), devices)
    return newDevice
  },

  async updateDevice(id: string, updates: Partial<Device>) {
    await delay(500)
    const userId = getCurrentUserId()
    if (!userId) throw new Error('用户未登录')
    const devices = getStorage<Device[]>(devicesKey(userId), [])
    const index = devices.findIndex(d => d.id === id)
    if (index === -1) throw new Error('设备不存在')
    devices[index] = { ...devices[index], ...updates, ownerId: userId }
    setStorage(devicesKey(userId), devices)
    return devices[index]
  },

  async deleteDevice(id: string) {
    await delay(500)
    const userId = getCurrentUserId()
    if (!userId) throw new Error('用户未登录')
    const devices = getStorage<Device[]>(devicesKey(userId), [])
    const filtered = devices.filter(d => d.id !== id)
    setStorage(devicesKey(userId), filtered)
  },

  async sendCommand(deviceId: string, command: string, params?: Record<string, any>) {
    await delay(800)
    console.log(`[模拟] 向设备 ${deviceId} 发送命令: ${command}`, params)
    return { success: true, message: '命令已发送', deviceId, command }
  },

  async updateDeviceStatus(id: string, status: string) {
    await delay(300)
    const userId = getCurrentUserId()
    if (!userId) throw new Error('用户未登录')
    const devices = getStorage<Device[]>(devicesKey(userId), [])
    const index = devices.findIndex(d => d.id === id)
    if (index === -1) throw new Error('设备不存在')
    devices[index].status = status.toLowerCase() as 'online' | 'offline' | 'warning'
    devices[index].lastActive = new Date().toISOString()
    setStorage(devicesKey(userId), devices)
    return devices[index]
  },

  async getCommandLogs(deviceId?: string) {
    await delay(200)
    if (deviceId) {
      return [{ id: Date.now(), deviceId, command: 'getStatus', status: 'EXECUTED', response: '{"status":"online"}', sentAt: new Date().toISOString() }]
    }
    return [
      { id: 1, deviceId: 'dev_001', command: 'getStatus', params: '', status: 'EXECUTED', response: '{"status":"online","uptime":43200}', sentAt: new Date(Date.now() - 300000).toISOString(), respondedAt: new Date().toISOString() },
      { id: 2, deviceId: 'dev_001', command: 'getVersion', params: '', status: 'EXECUTED', response: '{"firmware":"v2.1.3"}', sentAt: new Date(Date.now() - 600000).toISOString(), respondedAt: new Date(Date.now() - 590000).toISOString() },
      { id: 3, deviceId: 'dev_002', command: 'checkHealth', params: '', status: 'EXECUTED', response: '{"cpu":35%,"healthy":true}', sentAt: new Date(Date.now() - 120000).toISOString(), respondedAt: new Date(Date.now() - 110000).toISOString() }
    ]
  },

  // ---- Data (user-scoped) ----

  async getDeviceData(deviceId?: string, sensorId?: string, limit: number = 200, _from?: string, _to?: string) {
    await delay(300)
    const userId = getCurrentUserId()
    if (!userId) return []
    let data = getStorage<DataPoint[]>(dataKey(userId), [])
    if (deviceId) {
      data = data.filter(d => d.deviceId === deviceId)
    }
    if (sensorId) {
      data = data.filter(d => d.sensorId === sensorId)
    }
    if (_from) {
      const fromDate = new Date(_from)
      data = data.filter(d => new Date(d.timestamp) >= fromDate)
    }
    if (_to) {
      const toDate = new Date(_to)
      data = data.filter(d => new Date(d.timestamp) <= toDate)
    }
    return data.slice(0, limit)
  },

  async addDataPoint(deviceId: string, sensorId: string, value: number) {
    await delay(200)
    const userId = getCurrentUserId()
    if (!userId) throw new Error('用户未登录')
    const data = getStorage<DataPoint[]>(dataKey(userId), [])
    const newPoint: DataPoint = {
      id: generateId(),
      deviceId,
      sensorId,
      value,
      timestamp: new Date().toISOString(),
      ownerId: userId
    }
    data.unshift(newPoint)
    if (data.length > 1000) data.pop()
    setStorage(dataKey(userId), data)
    return newPoint
  },

  simulateRealtimeUpdate(callback: (device: Device) => void, interval: number = 3000) {
    const timer = setInterval(() => {
      const userId = getCurrentUserId()
      if (!userId) return
      const key = devicesKey(userId)
      const devices = getStorage<Device[]>(key, [])
      const onlineDevices = devices.filter(d => d.status === 'online')
      if (onlineDevices.length === 0) return

      const randomDevice = onlineDevices[Math.floor(Math.random() * onlineDevices.length)]
      randomDevice.sensors.forEach(sensor => {
        const range = sensor.max - sensor.min
        const variation = (Math.random() - 0.5) * range * 0.05
        sensor.value = Math.max(sensor.min, Math.min(sensor.max, sensor.value + variation))
      })
      randomDevice.lastActive = new Date().toISOString()
      setStorage(key, devices)
      callback(randomDevice)
    }, interval)

    return () => clearInterval(timer)
  },

  // ---- User management (admin) ----

  async getAllUsers() {
    await delay(300)
    return getStorage<User[]>(STORAGE_KEYS.USERS, [])
  },

  // Simulation methods
  _simState: { dataUploadRunning: false, commandDeliveryRunning: false },

  async triggerUploadOnce() {
    await delay(200)
    const userId = getCurrentUserId()
    if (!userId) return { success: false }
    const key = devicesKey(userId)
    const devices = getStorage<Device[]>(key, [])
    for (const device of devices) {
      for (const sensor of device.sensors) {
        const range = sensor.max - sensor.min
        const variation = (Math.random() - 0.5) * range * 0.1
        sensor.value = Math.max(sensor.min, Math.min(sensor.max, sensor.value + variation))
      }
      device.lastActive = new Date().toISOString()
    }
    setStorage(key, devices)
    return { success: true }
  },

  async triggerDeliveryOnce() {
    await delay(200)
    return { success: true, message: '模拟命令下发成功' }
  },

  async startDataUpload(interval: number) {
    this._simState.dataUploadRunning = true
    return { success: true, interval }
  },

  async stopDataUpload() {
    this._simState.dataUploadRunning = false
    return { success: true }
  },

  async startCommandDelivery(interval: number) {
    this._simState.commandDeliveryRunning = true
    return { success: true, interval }
  },

  async stopCommandDelivery() {
    this._simState.commandDeliveryRunning = false
    return { success: true }
  },

  async getSimulationStatus() {
    return { ...this._simState }
  },

  // ========== TCP 连接实例 ==========
  async getTcpStatus() {
    return { enabled: true, onlineDevices: 3, onlineConnections: 3 }
  },

  async getTcpConnections() {
    await delay(300)
    const userId = getCurrentUserId()
    const devices = userId ? getStorage<Device[]>(devicesKey(userId), []) : []
    return devices.slice(0, 3).map((d, i) => ({
      gatewayId: null,
      deviceIds: [d.id],
      deviceCount: 1,
      connectionMode: 'DEVICE',
      deviceId: d.id,
      channelId: `tcp-${(1000 + i).toString(16)}`,
      remoteAddress: `192.168.1.${100 + i}:5${200 + i}`,
      connectedAt: Date.now() - (i + 1) * 60000,
      onlineSeconds: (i + 1) * 60
    }))
  },

  async disconnectTcpConnection(deviceId: string) {
    return { deviceId, success: true }
  }
}

export default mockApi
