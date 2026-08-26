# IoT Platform API 接口文档

**Base URL:** `http://localhost:8080`  
**Version:** 1.0.0  
**认证方式:** Bearer Token (JWT)，除登录/注册外均需在 Header 中携带 `Authorization: Bearer <token>`

---

## 通用说明

### 响应格式

所有接口统一返回：

```json
{
  "success": true,
  "message": "操作成功",
  "data": { ... }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| success | boolean | 请求是否成功 |
| message | string | 提示信息 |
| data | object/array/null | 响应数据 |

### 错误处理

- **400** — 请求参数错误（含 `@Valid` 校验失败的字段详情）
- **401** — 未登录或 Token 过期

---

## 1. 认证模块 `/api/auth`

### 1.1 账号密码登录

```
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "123456"
}
```

**响应示例：**

```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOi...",
    "id": 1,
    "username": "admin",
    "email": "admin@example.com",
    "phone": null,
    "role": "ADMIN",
    "createdAt": "2026-06-01T10:00:00"
  }
}
```

### 1.2 用户注册

```
POST /api/auth/register
Content-Type: application/json

{
  "username": "newuser",       // 必填，3~20字符
  "email": "user@example.com", // 必填，合法邮箱
  "password": "123456"         // 必填，6~20字符
}
```

### 1.3 发送短信验证码

```
POST /api/auth/send-code
Content-Type: application/json

{
  "phone": "13800138000"       // 必填，1 开头 11 位手机号
}
```

### 1.4 手机号 + 验证码登录

```
POST /api/auth/login-by-phone
Content-Type: application/json

{
  "phone": "13800138000",
  "code": "123456"             // 6位验证码
}
```

### 1.5 获取当前用户信息

```
GET /api/auth/me
Authorization: Bearer <token>
```

---

## 2. 设备管理 `/api/devices`

### 2.1 获取设备列表

```
GET /api/devices
```

返回所有设备的列表，每个设备包含 `sensors` 和 `actuators` 数组。

### 2.2 获取单个设备

```
GET /api/devices/{deviceId}
```

### 2.3 创建设备

```
POST /api/devices
Content-Type: application/json

{
  "name": "温控设备-01",        // 必填
  "type": "temperature_controller", // 必填
  "status": "ONLINE",           // 默认 OFFLINE
  "location": "A栋-3楼-实验室",
  "description": "三楼实验室温度监测",
  "sensors": [
    {
      "id": "sensor_temp_01",
      "name": "温度传感器",
      "type": "temperature",
      "unit": "°C",
      "minVal": -20,
      "maxVal": 80
    }
  ]
}
```

### 2.4 更新设备（全量）

```
PUT /api/devices/{deviceId}
Content-Type: application/json

{
  "name": "温控设备-01-改",
  "type": "temperature_controller",
  "status": "ONLINE",
  "location": "A栋-3楼-实验室-改",
  "description": "更新后的描述",
  "sensors": [ ... ]
}
```

### 2.5 删除设备

```
DELETE /api/devices/{deviceId}
```

### 2.6 更新设备状态（部分）

```
PATCH /api/devices/{deviceId}/status
Content-Type: application/json

{
  "status": "OFFLINE"
}
```

### 2.7 设备统计

```
GET /api/devices/stats
```

**响应示例：**

```json
{
  "data": {
    "total": 10,
    "online": 7,
    "offline": 2,
    "warning": 1
  }
}
```

---

## 3. 数据查询与写入 `/api/data`

### 3.1 查询设备数据（时间范围）

```
GET /api/data/{deviceId}?sensorId=sensor_temp_01&from=2026-06-22T00:00:00&to=2026-06-29T23:59:59&limit=200
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sensorId | string | 否 | 传感器 ID 过滤 |
| from | ISO DateTime | 否 | 开始时间，不传则查最新数据 |
| to | ISO DateTime | 否 | 结束时间 |
| limit | int | 否 | 最大返回条数，默认 200 |

**响应示例：**

```json
{
  "data": [
    {
      "id": null,
      "deviceId": "dev_8291adf5",
      "sensorId": "sensor_temp_01",
      "value": 25.5,
      "ownerId": 1,
      "timestamp": "2026-06-29T14:30:00"
    }
  ]
}
```

### 3.2 查询历史聚合数据（降采样）

```
GET /api/data/{deviceId}/history?from=2026-06-22T00:00:00&to=2026-06-29T23:59:59&sensorId=sensor_temp_01&interval=1h
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| from | ISO DateTime | **是** | 开始时间 |
| to | ISO DateTime | **是** | 结束时间 |
| sensorId | string | 否 | 传感器 ID 过滤 |
| interval | string | 否 | 聚合窗口，如 `5m`、`1h`、`1d`（不传则返回原始数据） |

**返回 Map 数组，包含 ts / avg_val / max_val / min_val / sensor_id**

### 3.3 写入数据点

```
POST /api/data/{deviceId}
Content-Type: application/json

{
  "sensorId": "sensor_temp_01",
  "value": 26.8
}
```

### 3.4 查询最新数据

```
GET /api/data/{deviceId}/latest?sensorId=sensor_temp_01&limit=10
```

轻量接口，同时支持 `X-Api-Key` Header 认证（设备直连上报场景）。

### 3.5 下发执行器指令

```
POST /api/data/{deviceId}/command
Content-Type: application/json

{
  "command": "on",
  "params": {
    "actuator": "风扇"
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| command | string | `on` / `off` / `toggle` / `1` / `0`（大小写不敏感） |
| params.actuator | string | 执行器名称（需和数据库 `sensor.name` 一致） |

---

## 4. 告警管理 `/api/alerts`

### 4.1 告警规则 CRUD

```
GET    /api/alerts/rules                  # 获取告警规则列表
GET    /api/alerts/rules/{id}             # 获取单条规则
POST   /api/alerts/rules                  # 创建规则
PUT    /api/alerts/rules/{id}             # 更新规则（全量）
DELETE /api/alerts/rules/{id}             # 删除规则
PATCH  /api/alerts/rules/{id}/toggle?enabled=true   # 启用/禁用规则
```

**AlertRule 结构示例：**

```json
{
  "id": 1,
  "name": "温度过高告警",
  "deviceId": "dev_8291adf5",
  "sensorType": "temperature",
  "condition": ">",
  "threshold": 40.0,
  "level": "WARNING",
  "enabled": true,
  "ownerId": 1
}
```

`level` 可选值: `INFO` / `WARNING` / `CRITICAL`  
`condition` 可选值: `>` / `<` / `>=` / `<=` / `==`

### 4.2 告警记录

```
GET    /api/alerts/records?deviceId=dev_8291adf5&status=ACTIVE&level=WARNING&page=0&size=20
GET    /api/alerts/records/{id}
DELETE /api/alerts/records/{id}
PATCH  /api/alerts/records/{id}/acknowledge    # 确认告警
PATCH  /api/alerts/records/{id}/resolve         # 解决告警
```

### 4.3 告警统计

```
GET /api/alerts/stats
```

---

## 5. 系统设置 `/api/settings`

```
GET /api/settings                              # 获取当前用户设置
PUT /api/settings                              # 更新当前用户设置（部分更新）

Content-Type: application/json
{
  "dataRetentionDays": 90,
  "alertEmailEnabled": true
}
```

---

## 6. 模拟控制 `/api/simulation`

### 6.1 数据上报模拟

```
POST /api/simulation/upload/start?interval=5    # 启动（间隔秒）
POST /api/simulation/upload/stop                # 停止
POST /api/simulation/upload/once                # 手动触发一轮
```

### 6.2 指令下发模拟

```
POST /api/simulation/delivery/start?interval=10  # 启动（间隔秒）
POST /api/simulation/delivery/stop               # 停止
POST /api/simulation/delivery/once               # 手动触发一轮
```

### 6.3 全部控制

```
POST /api/simulation/stop       # 停止所有模拟
GET  /api/simulation/status     # 查询模拟状态
```

### 6.4 指令日志

```
GET /api/simulation/commands?deviceId=dev_8291adf5   # 查询指令下发历史
```

---

## 7. 健康检查 `/api/health`

```
GET /api/health              # { "status": "UP", "service": "iot-platform", "version": "1.0.0" }
GET /api/health/ready        # { "status": "READY" }
```

---

## 8. 执行器控制说明

### REST API 方式

```json
POST /api/data/{deviceId}/command
{
  "command": "on",
  "params": { "actuator": "风扇" }
}
```

### MQTT 方式

通过 EMQX 向设备 topic 下发指令：

```
Topic: iot/{deviceId}/command
QoS:   1
Payload:
{
  "command": "off",
  "actuator": "风扇"
}
```

平台接收到 MQTT 指令后，会自动：
1. 更新 PostgreSQL 中传感器的当前值
2. 写入 TDengine 时序记录（支持历史查询）
3. 通过 WebSocket 推送实时状态更新到前端

---

## 9. MQTT 主题一览

| 主题 | 方向 | 说明 |
|------|------|------|
| `iot/+/telemetry` | 设备 → 平台 | 传感器数据上报 |
| `iot/+/status` | 设备 → 平台 | 设备状态上报 |
| `iot/+/command` | 设备/外部 → 平台 | 执行器指令下发 |

**telemetry 上报格式：**

```json
{
  "sensorId": "sensor_temp_01",
  "value": 25.5
}
```

**status 上报格式：**

```json
{
  "status": "online"
}
```

---

## 10. TCP 设备接入通道

平台内置 **Netty TCP 服务器**，为不具备 MQTT/HTTP 能力的设备（如 485 串口采集网关、单片机上位机）提供直连接入通道。

### 10.1 连接参数

| 参数 | 值 | 说明 |
|------|------|------|
| 地址 | `tcp://{服务器IP}:1884` | 端口由 `app.tcp.port` 配置 |
| 协议 | UTF-8 JSON 行协议 | 每帧一个完整 JSON，以 `\n` 结尾（帧最大 65536 字节） |
| 鉴权超时 | 30 秒 | 连接后必须在 30 秒内完成鉴权，否则被断开 |
| 网关 Token | `APP_TCP_ACCESS_TOKEN` | 网关模式一次认证使用；留空时不启用网关认证 |
| 网关设备数 | `APP_TCP_MAX_DEVICES` | 单连接最大设备数，默认 256 |

> **与 485 采集程序的配合方式：** 485 侧收到原始二进制帧后按协议拆帧（帧头 → 功能码 → 数据长度 → Data[0..7] → CRC16 校验），解析出风速/温湿度/甲烷等物理量，再封装成下方 JSON 帧经 TCP 上送平台。

### 10.2 鉴权（连接后必须首先发送）

#### 推荐：网关一次认证

一个 TCP 网关可承载多台已登记设备，只需认证一次。服务端通过环境变量
`APP_TCP_ACCESS_TOKEN` 配置当前有效 Token。设备清单不能重复、不能为空，且每个设备必须已在平台登记。

```
设备 → 平台：
{"type":"gateway_auth","accessToken":"<APP_TCP_ACCESS_TOKEN>","gatewayId":"gw-001","deviceIds":["dev_001","dev_002"]}

平台 → 设备（成功）：
{"type":"gateway_auth_result","success":true,"gatewayId":"gw-001","deviceIds":["dev_001","dev_002"],"message":"网关认证成功"}

平台 → 设备（失败并断开）：
{"type":"gateway_auth_result","success":false,"message":"网关 Token 不正确"}
```

网关认证后，遥测和状态帧必须带 `deviceId`，且只能使用认证时提交的设备 ID：

```
{"type":"telemetry","deviceId":"dev_001","sensorId":"s_001","value":25.5,"sensorType":"temperature","unit":"C"}
{"type":"status","deviceId":"dev_002","status":"ONLINE"}
```

网关连接下发命令时，平台会在命令帧中带上目标 `deviceId`。网关断开后，该连接承载的全部设备均注销 TCP 在线状态。

#### 兼容：设备级认证

```
设备 → 平台：
{"type":"auth","deviceId":"dev_xxx","apiKey":"<设备API Key>"}

平台回执（成功）：
{"type":"auth_result","success":true,"deviceId":"dev_xxx","message":"认证成功"}

平台回执（失败后主动断开连接）：
{"type":"auth_result","success":false,"message":"设备ID或API Key不正确"}
```

`deviceId` 在设备详情页获取，`apiKey` 与 HTTP 接入的 `X-Api-Key` 使用同一 Key。

完整 `apiKey` 仅在创建设备或重新生成时返回。若 Key 已丢失，在设备详情页重新生成，或调用：

```
POST /api/devices/{deviceId}/api-key/regenerate
```

### 10.3 遥测上报（设备 → 平台）

```
{"type":"telemetry","deviceId":"dev_001","sensorId":"s_001","value":25.5,"sensorType":"temperature","unit":"C"}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | string | 是 | 固定 `telemetry` |
| deviceId | string | 是 | 网关模式下必须是认证时提交的设备 ID；设备级模式使用已认证设备 |
| sensorId | string | 是 | 传感器 ID |
| value | number | 是 | 物理量数值（如甲烷 ppm、风速 m/s） |
| sensorType | string | 否 | 传感器类型（temperature / humidity / methane 等） |
| unit | string | 否 | 单位（°C、%RH、ppm、m/s） |

处理链路与 MQTT 通道完全一致：TDengine 时序落库 → Redis 实时缓存 → PostgreSQL 更新 → 告警规则评估 → Kafka → WebSocket 推送前端。

### 10.4 设备状态上报（设备 → 平台）

```
{"type":"status","deviceId":"dev_001","status":"ONLINE"}
```

`status` 可选 `ONLINE` / `OFFLINE`（不区分大小写）。

### 10.5 指令执行结果回执（设备 → 平台，可选）

```
{"type":"command_result","command":"on","success":true,"message":"executed"}
```

### 10.6 指令下发（平台 → 设备）

通过 REST `POST /api/data/{deviceId}/command` 或 MQTT 下发指令时，若该设备存在在线 TCP 连接，平台会自动透传：

```
{"type":"command","command":"on","params":{"actuator":"风扇"},"timestamp":1730000000000}
```

设备执行后可回复 10.5 的 `command_result` 帧。

### 10.7 其他错误帧

JSON 解析失败或消息类型未知时，平台返回：

```
{"type":"error","message":"未知消息类型: xxx"}
```

### 10.8 连接管理接口（登录用户）

```
GET    /api/tcp/status                      # TCP 通道开关与在线设备数
GET    /api/tcp/connections                 # 在线连接列表（普通用户仅见自己设备，管理员见全部）
DELETE /api/tcp/connections/{deviceId}      # 强制断开某设备 TCP 连接（仅管理员）
```

**`/api/tcp/status` 响应示例：**

```json
{
  "success": true,
  "data": { "enabled": true, "onlineDevices": 3 }
}
```

---

## 11. 接口汇总

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 账号密码登录 |
| POST | `/api/auth/register` | 用户注册 |
| POST | `/api/auth/send-code` | 发送短信验证码 |
| POST | `/api/auth/login-by-phone` | 手机验证码登录 |
| GET | `/api/auth/me` | 获取当前用户信息 |
| GET | `/api/devices` | 获取设备列表 |
| GET | `/api/devices/stats` | 设备统计 |
| POST | `/api/devices` | 创建设备 |
| GET | `/api/devices/{deviceId}` | 获取单个设备 |
| PUT | `/api/devices/{deviceId}` | 更新设备 |
| POST | `/api/devices/{deviceId}/api-key/regenerate` | 重新生成设备 API Key |
| DELETE | `/api/devices/{deviceId}` | 删除设备 |
| PATCH | `/api/devices/{deviceId}/status` | 更新设备状态 |
| GET | `/api/data/{deviceId}` | 查询设备数据 |
| GET | `/api/data/{deviceId}/history` | 查询历史聚合数据 |
| POST | `/api/data/{deviceId}` | 写入数据点 |
| GET | `/api/data/{deviceId}/latest` | 查询最新数据 |
| POST | `/api/data/{deviceId}/command` | 下发执行器指令 |
| GET | `/api/alerts/rules` | 告警规则列表 |
| POST | `/api/alerts/rules` | 创建告警规则 |
| GET | `/api/alerts/rules/{id}` | 获取单条规则 |
| PUT | `/api/alerts/rules/{id}` | 更新告警规则 |
| DELETE | `/api/alerts/rules/{id}` | 删除告警规则 |
| PATCH | `/api/alerts/rules/{id}/toggle` | 启用/禁用规则 |
| GET | `/api/alerts/records` | 告警记录列表（分页） |
| GET | `/api/alerts/records/{id}` | 获取单条告警记录 |
| DELETE | `/api/alerts/records/{id}` | 删除告警记录 |
| PATCH | `/api/alerts/records/{id}/acknowledge` | 确认告警 |
| PATCH | `/api/alerts/records/{id}/resolve` | 解决告警 |
| GET | `/api/alerts/stats` | 告警统计 |
| GET | `/api/settings` | 获取系统设置 |
| PUT | `/api/settings` | 更新系统设置 |
| POST | `/api/simulation/upload/start` | 启动数据上报模拟 |
| POST | `/api/simulation/upload/stop` | 停止数据上报模拟 |
| POST | `/api/simulation/upload/once` | 手动触发一轮上报 |
| POST | `/api/simulation/delivery/start` | 启动指令下发模拟 |
| POST | `/api/simulation/delivery/stop` | 停止指令下发模拟 |
| POST | `/api/simulation/delivery/once` | 手动触发一轮下发 |
| POST | `/api/simulation/stop` | 停止所有模拟 |
| GET | `/api/simulation/status` | 查询模拟状态 |
| GET | `/api/simulation/commands` | 查询指令日志 |
| GET | `/api/health` | 健康检查 |
| GET | `/api/health/ready` | 就绪检查 |
| GET | `/api/tcp/status` | TCP 通道状态 |
| GET | `/api/tcp/connections` | TCP 在线连接列表 |
| DELETE | `/api/tcp/connections/{deviceId}` | 强制断开设备 TCP 连接（管理员） |
