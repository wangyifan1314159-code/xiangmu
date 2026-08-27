import{An as e,B as t,Bn as n,Bt as r,Ht as i,Jt as a,O as o,Pt as s,Sr as c,St as l,Ut as u,Vt as d,Wt as f,Xt as p,Y as m,Yn as h,Yt as g,bn as _,gn as v,it as y,k as b,mn as x,vn as S,vt as C,y as w}from"./dist-Cdv0aqCs.js";import{r as T}from"./index-CSv3OfzX.js";import{t as E}from"./_plugin-vue_export-helper-S3RvzygF.js";var ee={class:`api-docs`},te={class:`card-title`},ne={class:`quick-list`},re={class:`quick-item-header`},ie={class:`quick-item-title`},ae={class:`quick-code-wrapper`},oe={class:`quick-code`},D={class:`card-title`},O={class:`auth-box`},k={class:`auth-box-header`},A={class:`api-section`},j={class:`section-tabs`},M={class:`section-header`},N={class:`section-title`},P={class:`section-sub`},F={class:`api-card-top`},I={class:`api-method-row`},L={class:`api-path-display`},R={class:`api-card-title`},z={class:`api-card-desc`},B={key:0,class:`api-table-block`},V={class:`param-table`},H={key:1,class:`api-table-block`},se={class:`param-table`},U={key:2,class:`api-table-block`},W={class:`param-table`},G={class:`code-section`},K={class:`code-section-header`},ce={class:`code-block`},le={class:`code-section`},ue={class:`code-section-header`},de={class:`code-block`},fe={class:`card-title`},q={class:`param-table`},J=E(p({__name:`ApiDocsView`,setup(p){let E=n(`quick`),J=n(``),Y=window.location.origin,X=r(()=>Q.find(e=>e.id===E.value)),pe=r(()=>X.value?.apis||[]);function Z(e,t){navigator.clipboard.writeText(e),t&&(J.value=t,setTimeout(()=>J.value=``,1500)),T.success({message:`已复制`,duration:1200})}let me=[{title:`上传光照数据`,icon:l,lang:`curl`,code:`curl -X POST "${Y}/api/data/dev_b087404c" \\
  -H "X-Api-Key: your_device_api_key" \\
  -H "Content-Type: application/json" \\
  -d '{"sensorId": "s_1781530543162", "value": 55.5}'`,id:`quick-upload`},{title:`查询最新数据`,icon:b,lang:`curl`,code:`curl -H "X-Api-Key: your_device_api_key" \\
  "${Y}/api/data/dev_b087404c/latest?sensorId=s_1781530543162&limit=5"`,id:`quick-query`},{title:`查询设备详情`,icon:o,lang:`curl`,code:`curl -H "X-Api-Key: your_device_api_key" \\
  "${Y}/api/devices/dev_b087404c"`,id:`quick-device`},{title:`发送执行器指令`,icon:C,lang:`curl`,code:`curl -X POST "${Y}/api/data/dev_414305e8/command" \\
  -H "X-Api-Key: your_device_api_key" \\
  -H "Content-Type: application/json" \\
  -d '{"command": "1", "params": {"actuator": "电动开关"}}'`,id:`quick-command`},{title:`Python 上传示例`,icon:y,lang:`python`,code:`import requests

resp = requests.post(
    "${Y}/api/data/dev_b087404c",
    json={"sensorId": "s_1781530543162", "value": 55.5},
    headers={"X-Api-Key": "your_device_api_key"},
    timeout=5
)
print(resp.json())`,id:`quick-python`}],Q=[{id:`upload`,title:`数据上报`,icon:l,desc:`设备向平台发送传感器数据`,apis:[{method:`POST`,path:`/api/data/{deviceId}`,title:`上报传感器数据`,desc:`向指定设备上报传感器读数。后端自动写入时序数据库、更新实时缓存、触发告警评估。`,auth:`X-Api-Key`,pathParams:[{name:`deviceId`,type:`string`,desc:`设备 ID（如 dev_b087404c）`}],bodyParams:[{name:`sensorId`,type:`string`,required:!0,desc:`传感器 ID（如 s_1781530543162）`},{name:`value`,type:`number`,required:!0,desc:`传感器读数值`}],headers:[{name:`X-Api-Key`,desc:`设备 API Key（从设备详情页获取）`,required:!0},{name:`Content-Type`,desc:`application/json`,required:!0}],example:`curl -X POST "${Y}/api/data/dev_b087404c" \\
  -H "X-Api-Key: your_device_api_key" \\
  -H "Content-Type: application/json" \\
  -d '{"sensorId": "s_1781530543162", "value": 55.5}'`,response:`{
  "success": true,
  "data": {
    "deviceId": "dev_b087404c",
    "sensorId": "s_1781530543162",
    "value": 55.5,
    "timestamp": "2026-06-16T10:30:00"
  }
}`,id:`api-upload`},{method:`POST`,path:`/api/data/{deviceId}/command`,title:`下发控制指令`,desc:`向执行器发送控制命令（1=ON, 0=OFF, toggle=翻转）。后端自动更新执行器状态。`,auth:`X-Api-Key`,pathParams:[{name:`deviceId`,type:`string`,desc:`设备 ID`}],bodyParams:[{name:`command`,type:`string`,required:!0,desc:`指令: "1"/"0"/"on"/"off"/"toggle"`},{name:`params.actuator`,type:`string`,required:!1,desc:`执行器名称（与设备页一致）`}],example:`curl -X POST "${Y}/api/data/dev_414305e8/command" \\
  -H "X-Api-Key: your_device_api_key" \\
  -H "Content-Type: application/json" \\
  -d '{"command": "1", "params": {"actuator": "电动开关"}}'`,response:`{
  "success": true,
  "data": {
    "message": "命令 [1] → 电动开关 已ON",
    "deviceId": "dev_414305e8",
    "command": "1"
  }
}`,id:`api-command`}]},{id:`query`,title:`数据查询`,icon:b,desc:`从平台查询已上报的传感器数据`,apis:[{method:`GET`,path:`/api/data/{deviceId}/latest`,title:`获取最新数据`,desc:`返回指定传感器最新 N 条读数，按时间倒序。适合轮询获取实时值。`,auth:`X-Api-Key`,pathParams:[{name:`deviceId`,type:`string`,desc:`设备 ID`}],queryParams:[{name:`sensorId`,type:`string`,required:!1,desc:`传感器 ID（不填返回全部传感器）`},{name:`limit`,type:`int`,required:!1,desc:`返回条数（默认 10）`}],example:`curl -H "X-Api-Key: your_device_api_key" \\
  "${Y}/api/data/dev_b087404c/latest?sensorId=s_1781530543162&limit=5"`,response:`{
  "success": true,
  "data": [
    { "value": 55.5, "sensorId": "s_1781530543162", "timestamp": "2026-06-16T10:30:00" },
    { "value": 54.2, "sensorId": "s_1781530543162", "timestamp": "2026-06-16T10:29:57" }
  ]
}`,id:`api-latest`},{method:`GET`,path:`/api/data/{deviceId}`,title:`时间范围查询`,desc:`按起止时间查询历史数据，返回完整 DataPoint 对象（含 id、ownerId 等字段）。`,auth:`X-Api-Key`,pathParams:[{name:`deviceId`,type:`string`,desc:`设备 ID`}],queryParams:[{name:`sensorId`,type:`string`,required:!1,desc:`传感器 ID`},{name:`from`,type:`ISO datetime`,required:!1,desc:`起始时间（如 2026-06-16T00:00:00）`},{name:`to`,type:`ISO datetime`,required:!1,desc:`结束时间`},{name:`limit`,type:`int`,required:!1,desc:`返回条数（默认 200）`}],example:`curl -H "X-Api-Key: your_device_api_key" \\
  "${Y}/api/data/dev_b087404c?sensorId=s_1781530543162&from=2026-06-16T00:00:00&to=2026-06-16T12:00:00&limit=100"`,response:`{
  "success": true,
  "data": [
    { "id": 123, "deviceId": "dev_b087404c", "sensorId": "s_1781530543162",
      "value": 55.5, "timestamp": "2026-06-16T10:30:00" }
  ]
}`,id:`api-range`},{method:`GET`,path:`/api/data/{deviceId}/history`,title:`聚合查询（降采样）`,desc:`按时间间隔聚合，返回 avg/max/min。用于生成趋势图。`,auth:`X-Api-Key`,pathParams:[{name:`deviceId`,type:`string`,desc:`设备 ID`}],queryParams:[{name:`sensorId`,type:`string`,required:!1,desc:`传感器 ID`},{name:`from`,type:`ISO datetime`,required:!0,desc:`起始时间`},{name:`to`,type:`ISO datetime`,required:!0,desc:`结束时间`},{name:`interval`,type:`string`,required:!1,desc:`聚合间隔: 5m / 1h / 1d`}],example:`curl -H "X-Api-Key: your_device_api_key" \\
  "${Y}/api/data/dev_b087404c/history?from=2026-06-16T00:00:00&to=2026-06-16T12:00:00&interval=5m"`,response:`{
  "success": true,
  "data": [
    { "ts": "2026-06-16T10:00:00", "avg_val": 24.5, "max_val": 26.1, "min_val": 23.8 },
    { "ts": "2026-06-16T10:05:00", "avg_val": 25.1, "max_val": 26.3, "min_val": 24.2 }
  ]
}`,id:`api-history`}]},{id:`device`,title:`设备信息`,icon:o,desc:`设备管理相关的查询接口`,apis:[{method:`GET`,path:`/api/devices`,title:`获取所有设备`,desc:`返回当前用户的所有设备列表（含传感器实时值）。`,auth:`Bearer Token`,example:`curl -H "Authorization: Bearer {token}" \\
  "${Y}/api/devices"`,response:`{
  "success": true,
  "data": [{
    "deviceId": "dev_b087404c",
    "name": "光照传感器",
    "status": "ONLINE",
    "sensors": [{ "id": "s_1781530543162", "name": "光照传感器", "value": 55.5 }],
    "apiKey": "your_device_api_key..."
  }]
}`,id:`api-devices`},{method:`GET`,path:`/api/devices/{deviceId}`,title:`获取设备详情`,desc:`返回单个设备的完整信息，含 API Key、传感器列表和实时值。`,auth:`X-Api-Key 或 Bearer Token`,pathParams:[{name:`deviceId`,type:`string`,desc:`设备 ID`}],example:`curl -H "X-Api-Key: your_device_api_key" \\
  "${Y}/api/devices/dev_b087404c"`,response:`{
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
}`,id:`api-device-detail`}]},{id:`mqtt`,title:`MQTT 接入`,icon:w,desc:`通过 MQTT 协议上报数据 / 下发指令（低功耗设备推荐，可用 MQTTX 等客户端直接联调）`,apis:[{method:`CONN`,path:`tcp://{服务器IP}:1883`,title:`Broker 连接参数`,desc:`平台内置 EMQX Broker，允许匿名连接（无需用户名密码）。MQTTX / 程序客户端按以下参数建立连接。`,auth:`匿名连接`,example:`# MQTTX 连接配置
协议:     mqtt:// (TCP)
地址:     服务器 IP（本机部署填 127.0.0.1）
端口:     1883
用户名:   留空（匿名）
密码:     留空
MQTT 版本: 3.1.1
QoS:      1（推荐）

# EMQX 管理控制台（可选，查看连接数 / 消息吞吐）
http://{服务器IP}:18083   账号 admin / 密码 public`,response:`平台后端已订阅以下通配主题：
iot/+/telemetry    传感器数据上报
iot/+/status       设备状态上报
iot/+/command      执行器指令

调试技巧：客户端订阅 iot/# 可回显自己发布的全部消息`,id:`mqtt-conn`},{method:`PUB`,path:`iot/{deviceId}/telemetry`,title:`上报传感器数据`,desc:`QoS 1 发布。平台接收后写入存储、WebSocket 实时推送前端页面、触发告警评估。与 REST 上报等效，二选一即可。`,auth:`无需认证（按 deviceId 归属）`,bodyParams:[{name:`sensorId`,type:`string`,required:!0,desc:`传感器 ID（设备详情页可复制）`},{name:`value`,type:`number`,required:!0,desc:`传感器读数值`}],example:`# MQTTX：Topic 填 iot/dev_b087404c/telemetry，报文填 ↓
{"sensorId": "s_1781530543162", "value": 55.5}

# mosquitto_pub（装有 mosquitto-clients 时可直接运行）
mosquitto_pub -h 127.0.0.1 -p 1883 -q 1 \\
  -t "iot/dev_b087404c/telemetry" \\
  -m '{"sensorId": "s_1781530543162", "value": 55.5}'`,response:`平台处理链路：
1. 解析 sensorId / value，按 deviceId 匹配设备归属
2. 写入存储（TDengine，不可用时降级 PostgreSQL）
3. WebSocket 实时推送到前端页面
4. 触发告警规则评估`,id:`mqtt-telemetry`},{method:`PUB`,path:`iot/{deviceId}/command`,title:`下发执行器指令`,desc:`QoS 1 发布。command 大小写不敏感；actuator 必须填执行器「名称」（与设备详情页显示一致，不是 ID）。`,auth:`无需认证（按 deviceId 归属）`,bodyParams:[{name:`command`,type:`string`,required:!0,desc:`指令: "on" / "off" / "toggle"（也接受 "1" / "0"）`},{name:`actuator`,type:`string`,required:!0,desc:`执行器名称（与设备页一致，如 "电动开关"）`}],example:`# MQTTX：Topic 填 iot/dev_414305e8/command，报文填 ↓
{"command": "on", "actuator": "电动开关"}

# mosquitto_pub
mosquitto_pub -h 127.0.0.1 -p 1883 -q 1 \\
  -t "iot/dev_414305e8/command" \\
  -m '{"command": "on", "actuator": "电动开关"}'`,response:`平台处理链路：
1. 更新执行器状态（value → 1.0 开 / 0.0 关）
2. 写入指令历史记录（可历史查询）
3. WebSocket 推送前端，控制面板立即刷新`,id:`mqtt-command`},{method:`PUB`,path:`iot/{deviceId}/status`,title:`上报设备状态`,desc:`QoS 1 发布。更新设备在线 / 离线状态，设备列表实时刷新。`,auth:`无需认证（按 deviceId 归属）`,bodyParams:[{name:`status`,type:`string`,required:!0,desc:`设备状态: "ONLINE" / "OFFLINE"`}],example:`# MQTTX：Topic 填 iot/dev_b087404c/status，报文填 ↓
{"status": "OFFLINE"}

# mosquitto_pub
mosquitto_pub -h 127.0.0.1 -p 1883 -q 1 \\
  -t "iot/dev_b087404c/status" \\
  -m '{"status": "OFFLINE"}'`,response:`平台处理链路：
1. 更新数据库中设备 status 字段
2. WebSocket 推送前端，设备列表状态即时变化`,id:`mqtt-status`}]},{id:`tcp`,title:`TCP 接入`,icon:m,desc:`Netty TCP 长连接通道，默认端口 1884。协议采用 UTF-8 JSON Lines：每条消息必须是一行完整 JSON，并以 LF（\\n）结束。`,apis:[{method:`CONN`,path:`tcp://{服务器IP}:1884`,title:`协议约束与单设备认证`,desc:`建立连接后 30 秒内必须发送 auth，否则服务端关闭连接。单行最大 65536 字节；认证后一个设备只保留一个 TCP 连接，新连接会替换旧连接。deviceId 与 apiKey 通过创建设备或重新生成 API Key 获取。`,auth:`deviceId + apiKey`,bodyParams:[{name:`type`,type:`string`,required:!0,desc:`固定 "auth"`},{name:`deviceId`,type:`string`,required:!0,desc:`设备 ID（如 dev_b087404c）`},{name:`apiKey`,type:`string`,required:!0,desc:`设备 API Key（与 X-Api-Key 相同）`}],example:`# 每一行都必须以 LF（\\n）结束；不要发送 JSON 数组或多行 JSON。
{"type":"auth","deviceId":"dev_xxxxxxxx","apiKey":"<设备创建或重新生成时取得的完整 Key>"}

# PowerShell TCP 客户端（写入后必须 Flush）
$client = [Net.Sockets.TcpClient]::new('127.0.0.1', 1884)
$writer = [IO.StreamWriter]::new($client.GetStream(), [Text.UTF8Encoding]::new($false))
$writer.NewLine = "\`n"; $writer.AutoFlush = $true
$writer.WriteLine('{"type":"auth","deviceId":"dev_xxxxxxxx","apiKey":"<apiKey>"}')`,response:`{"type":"auth_result","success":true,"deviceId":"dev_b087404c","message":"认证成功"}

# 失败时服务端主动断开：
{"type":"auth_result","success":false,"message":"设备ID或API Key不正确"}`,id:`tcp-conn`},{method:`SEND`,path:`telemetry 帧（设备 → 平台）`,title:`上报传感器数据`,desc:`仅在认证成功后发送。value 必须为有限数字，不能是字符串、NaN 或 Infinity；服务端使用与其他接入通道相同的数据写入与告警处理管线。`,auth:`需先完成 auth`,bodyParams:[{name:`type`,type:`string`,required:!0,desc:`固定 "telemetry"`},{name:`sensorId`,type:`string`,required:!0,desc:`传感器 ID`},{name:`value`,type:`number`,required:!0,desc:`物理量数值（甲烷 ppm / 风速 m/s 等）`},{name:`sensorType`,type:`string`,required:!1,desc:`类型: temperature / humidity / methane / wind_speed 等`},{name:`unit`,type:`string`,required:!1,desc:`单位: °C、%RH、ppm、m/s`}],example:`# 温度传感器（认证成功后发送）
{"type":"telemetry","sensorId":"temp_01","value":26.5,"sensorType":"temperature","unit":"C"}

# 甲烷传感器
{"type":"telemetry","sensorId":"methane_01","value":1200,"sensorType":"methane","unit":"ppm"}`,response:`遥测帧没有逐条成功回执。非法帧会收到：
{"type":"error","message":"遥测帧缺少 sensorId 或 value"}

数据可通过实时监控和历史数据接口查询；满足规则时会创建告警记录。`,id:`tcp-telemetry`},{method:`SEND`,path:`status / command_result 帧（设备 → 平台）`,title:`状态同步与指令执行回执`,desc:`status 仅允许 ONLINE、OFFLINE、WARNING（大小写不敏感，服务端保存为大写）。command_result 为可选回执，当前会记录服务端日志。`,auth:`需先完成 auth`,bodyParams:[{name:`type`,type:`string`,required:!0,desc:`"status" 或 "command_result"`},{name:`status`,type:`string`,required:!1,desc:`"ONLINE" / "OFFLINE" / "WARNING"（type=status 时必填）`},{name:`command`,type:`string`,required:!1,desc:`被回执的指令名（type=command_result 时）`},{name:`success`,type:`boolean`,required:!1,desc:`执行是否成功（type=command_result 时）`}],example:`{"type":"status","status":"ONLINE"}\\n
{"type":"command_result","command":"on","success":true,"message":"executed"}\\n`,response:`非法状态会返回：
{"type":"error","message":"非法设备状态"}

有效 status 会更新设备状态；command_result 当前记录到平台日志。`,id:`tcp-status`},{method:`RECV`,path:`command 帧（平台 → 设备）`,title:`接收下行控制指令`,desc:`管理端通过 POST /api/data/{deviceId}/command 下发时，平台优先写入该设备的在线 TCP 通道；设备不在线或发送失败时由业务服务回退 MQTT。设备收到 command 后执行，并建议回发 command_result。`,auth:`—`,example:`# 平台 → 设备（JSON 行，UTF-8）
{"type":"command","deviceId":"dev_xxxxxxxx","command":"on","params":{"actuator":"风扇"},"timestamp":1730000000000}

# 设备 → 平台（执行结束后回发）
{"type":"command_result","command":"on","success":true,"message":"executed"}`,response:`设备应按 command 和 params 执行，不应假设 params 固定字段。
TCP 通道写入成功仅表示消息已写入连接，不代表执行器已完成动作；以 command_result 作为设备侧执行结果。`,id:`tcp-command`},{method:`GATEWAY`,path:`gateway_auth 帧（网关 → 平台，可选）`,title:`多设备网关认证`,desc:`仅在部署配置了 APP_TCP_ACCESS_TOKEN 时可用。一个网关连接可代理多个已存在设备，deviceIds 不可为空、不可重复，且数量不能超过 APP_TCP_MAX_DEVICES（默认 256）。后续 telemetry/status 帧必须带 deviceId，且只能是已认证列表中的设备。`,auth:`gatewayId + accessToken + deviceIds`,bodyParams:[{name:`type`,type:`string`,required:!0,desc:`固定 "gateway_auth"`},{name:`gatewayId`,type:`string`,required:!0,desc:`网关唯一标识`},{name:`accessToken`,type:`string`,required:!0,desc:`部署时配置的 TCP 网关 Token`},{name:`deviceIds`,type:`string[]`,required:!0,desc:`网关代理的已存在设备 ID 列表`}],example:`{"type":"gateway_auth","gatewayId":"gw-line-01","accessToken":"<APP_TCP_ACCESS_TOKEN>","deviceIds":["dev_a","dev_b"]}

# 网关模式后，上报必须指定已授权的 deviceId
{"type":"telemetry","deviceId":"dev_a","sensorId":"temp_01","value":26.5,"sensorType":"temperature","unit":"C"}`,response:`{
  "type": "gateway_auth_result",
  "success": true,
  "gatewayId": "gw-line-01",
  "deviceIds": ["dev_a", "dev_b"],
  "message": "网关认证成功"
}`,id:`tcp-gateway`},{method:`GET`,path:`/api/tcp/connections`,title:`查询通道状态、连接与强制断开`,desc:`GET /api/tcp/status 返回通道启用状态、在线设备数和在线连接数。GET /api/tcp/connections：普通用户仅见自己的设备，管理员可见全部。仅管理员可 DELETE 强制断开。`,auth:`Bearer Token（断开仅限 ADMIN）`,example:`# 通道摘要
curl -H "Authorization: Bearer {token}" "${Y}/api/tcp/status"

# 在线连接实例
curl -H "Authorization: Bearer {token}" "${Y}/api/tcp/connections"

# 强制断开（管理员）
curl -X DELETE -H "Authorization: Bearer {token}" \\
  "${Y}/api/tcp/connections/dev_xxxxxxxx"`,response:`{
  "success": true,
  "data": {
    "enabled": true,
    "onlineDevices": 3,
    "onlineConnections": 2
  }
}

连接列表字段包括 gatewayId、deviceId、deviceIds、connectionMode、remoteAddress、connectedAt、onlineSeconds。`,id:`tcp-rest-connections`}]}];return(n,r)=>{let p=S(`el-icon`),C=S(`el-tag`),T=S(`el-button`),Y=S(`el-card`),Q=S(`el-col`),he=S(`el-row`),$=S(`el-radio-button`),ge=S(`el-radio-group`);return x(),f(`div`,ee,[r[35]||=d(`div`,{class:`page-header`},[d(`div`,null,[d(`h2`,null,`数据接口文档`),d(`span`,{class:`header-desc`},`设备数据上报、查询与命令下发 REST API 参考`)])],-1),g(Y,{class:`quick-card`,shadow:`never`},{header:e(()=>[d(`div`,te,[g(p,{size:18,color:`var(--accent)`},{default:e(()=>[g(h(y))]),_:1}),r[2]||=d(`span`,null,`快速测试`,-1),g(C,{size:`small`,type:`success`,effect:`dark`,class:`quick-badge`},{default:e(()=>[...r[1]||=[a(`直接复制运行`,-1)]]),_:1})])]),default:e(()=>[d(`div`,ne,[(x(),f(s,null,v(me,t=>d(`div`,{key:t.id,class:`quick-item`},[d(`div`,re,[g(p,{size:14,color:`var(--accent)`},{default:e(()=>[(x(),i(_(t.icon)))]),_:2},1024),d(`span`,ie,c(t.title),1),g(C,{size:`small`,effect:`plain`,class:`quick-lang`},{default:e(()=>[a(c(t.lang),1)]),_:2},1024)]),d(`div`,ae,[d(`pre`,oe,[d(`code`,null,c(t.code),1)]),g(T,{size:`small`,type:J.value===t.id?`success`:`default`,class:`quick-copy-btn`,onClick:e=>Z(t.code,t.id)},{default:e(()=>[a(c(J.value===t.id?`已复制 ✓`:`复制`),1)]),_:2},1032,[`type`,`onClick`])])])),64))])]),_:1}),g(Y,{class:`auth-card`,shadow:`never`},{header:e(()=>[d(`div`,D,[g(p,{size:18,color:`var(--accent)`},{default:e(()=>[g(h(t))]),_:1}),r[3]||=d(`span`,null,`认证方式`,-1)])]),default:e(()=>[g(he,{gutter:24},{default:e(()=>[g(Q,{span:12},{default:e(()=>[d(`div`,O,[d(`div`,k,[g(C,{type:`success`,effect:`dark`,size:`small`},{default:e(()=>[...r[4]||=[a(`推荐`,-1)]]),_:1}),r[5]||=d(`span`,null,`X-Api-Key（设备 Key）`,-1)]),r[6]||=d(`code`,{class:`code-inline`},`-H "X-Api-Key: {apiKey}"`,-1),r[7]||=d(`p`,{class:`auth-box-desc`},`在设备详情页可查看复制。适用于设备/脚本端直接调用。`,-1)])]),_:1}),g(Q,{span:12},{default:e(()=>[...r[8]||=[d(`div`,{class:`auth-box`},[d(`div`,{class:`auth-box-header`},[d(`span`,null,`Bearer Token（用户登录）`)]),d(`code`,{class:`code-inline`},`-H "Authorization: Bearer {token}"`),d(`p`,{class:`auth-box-desc`},`通过 POST /api/auth/login 获取。适用于 Web 前端调用。`)],-1)]]),_:1})]),_:1})]),_:1}),d(`div`,A,[d(`div`,j,[g(ge,{modelValue:E.value,"onUpdate:modelValue":r[0]||=e=>E.value=e,size:`small`},{default:e(()=>[g($,{value:`quick`},{default:e(()=>[g(p,{size:13},{default:e(()=>[g(h(y))]),_:1}),r[9]||=a(` 快速测试`,-1)]),_:1}),g($,{value:`upload`},{default:e(()=>[g(p,{size:13},{default:e(()=>[g(h(l))]),_:1}),r[10]||=a(` 数据上报`,-1)]),_:1}),g($,{value:`query`},{default:e(()=>[g(p,{size:13},{default:e(()=>[g(h(b))]),_:1}),r[11]||=a(` 数据查询`,-1)]),_:1}),g($,{value:`device`},{default:e(()=>[g(p,{size:13},{default:e(()=>[g(h(o))]),_:1}),r[12]||=a(` 设备信息`,-1)]),_:1}),g($,{value:`mqtt`},{default:e(()=>[g(p,{size:13},{default:e(()=>[g(h(w))]),_:1}),r[13]||=a(` MQTT 接入`,-1)]),_:1}),g($,{value:`tcp`},{default:e(()=>[g(p,{size:13},{default:e(()=>[g(h(m))]),_:1}),r[14]||=a(` TCP 接入`,-1)]),_:1})]),_:1},8,[`modelValue`])]),X.value?(x(),f(`div`,{key:X.value.id,class:`section-body`},[d(`div`,M,[g(p,{size:20,color:`var(--accent)`},{default:e(()=>[(x(),i(_(X.value.icon)))]),_:1}),d(`div`,null,[d(`h3`,N,c(X.value.title),1),d(`p`,P,c(X.value.desc),1)])]),(x(!0),f(s,null,v(pe.value,t=>(x(),i(Y,{key:t.id,class:`api-card`,shadow:`never`},{default:e(()=>[d(`div`,F,[d(`div`,I,[g(C,{type:t.method===`POST`?`success`:t.method===`GET`?`primary`:`warning`,size:`small`,effect:`dark`},{default:e(()=>[a(c(t.method),1)]),_:2},1032,[`type`]),d(`code`,L,c(t.path),1),g(C,{size:`small`,effect:`plain`,type:`info`},{default:e(()=>[a(c(t.auth),1)]),_:2},1024)]),d(`h4`,R,c(t.title),1),d(`p`,z,c(t.desc),1)]),t.pathParams?(x(),f(`div`,B,[r[16]||=d(`span`,{class:`api-section-label`},`路径参数`,-1),d(`table`,V,[r[15]||=d(`thead`,null,[d(`tr`,null,[d(`th`,null,`参数`),d(`th`,null,`类型`),d(`th`,null,`说明`)])],-1),d(`tbody`,null,[(x(!0),f(s,null,v(t.pathParams,t=>(x(),f(`tr`,{key:t.name},[d(`td`,null,[d(`code`,null,c(t.name),1)]),d(`td`,null,[g(C,{size:`small`,effect:`plain`},{default:e(()=>[a(c(t.type),1)]),_:2},1024)]),d(`td`,null,c(t.desc),1)]))),128))])])])):u(``,!0),t.queryParams?(x(),f(`div`,H,[r[18]||=d(`span`,{class:`api-section-label`},`Query 参数`,-1),d(`table`,se,[r[17]||=d(`thead`,null,[d(`tr`,null,[d(`th`,null,`参数`),d(`th`,null,`类型`),d(`th`,null,`必填`),d(`th`,null,`说明`)])],-1),d(`tbody`,null,[(x(!0),f(s,null,v(t.queryParams,t=>(x(),f(`tr`,{key:t.name},[d(`td`,null,[d(`code`,null,c(t.name),1)]),d(`td`,null,[g(C,{size:`small`,effect:`plain`},{default:e(()=>[a(c(t.type),1)]),_:2},1024)]),d(`td`,null,[g(C,{type:t.required?`danger`:`info`,size:`small`},{default:e(()=>[a(c(t.required?`是`:`否`),1)]),_:2},1032,[`type`])]),d(`td`,null,c(t.desc),1)]))),128))])])])):u(``,!0),t.bodyParams?(x(),f(`div`,U,[r[20]||=d(`span`,{class:`api-section-label`},`Body 参数 (JSON)`,-1),d(`table`,W,[r[19]||=d(`thead`,null,[d(`tr`,null,[d(`th`,null,`参数`),d(`th`,null,`类型`),d(`th`,null,`必填`),d(`th`,null,`说明`)])],-1),d(`tbody`,null,[(x(!0),f(s,null,v(t.bodyParams,t=>(x(),f(`tr`,{key:t.name},[d(`td`,null,[d(`code`,null,c(t.name),1)]),d(`td`,null,[g(C,{size:`small`,effect:`plain`},{default:e(()=>[a(c(t.type),1)]),_:2},1024)]),d(`td`,null,[g(C,{type:t.required?`danger`:`info`,size:`small`},{default:e(()=>[a(c(t.required?`是`:`否`),1)]),_:2},1032,[`type`])]),d(`td`,null,c(t.desc),1)]))),128))])])])):u(``,!0),d(`div`,G,[d(`div`,K,[g(p,{size:14},{default:e(()=>[g(h(w))]),_:1}),r[22]||=d(`span`,null,`示例请求`,-1),g(T,{size:`small`,text:``,type:`primary`,onClick:e=>Z(t.example,t.id)},{default:e(()=>[...r[21]||=[a(`复制`,-1)]]),_:1},8,[`onClick`])]),d(`pre`,ce,[d(`code`,null,c(t.example),1)])]),d(`div`,le,[d(`div`,ue,[r[24]||=d(`span`,null,`响应示例`,-1),g(T,{size:`small`,text:``,type:`primary`,onClick:e=>Z(t.response,t.id+`-resp`)},{default:e(()=>[...r[23]||=[a(`复制`,-1)]]),_:1},8,[`onClick`])]),d(`pre`,de,[d(`code`,null,c(t.response),1)])])]),_:2},1024))),128))])):u(``,!0)]),g(Y,{class:`error-card`,shadow:`never`},{header:e(()=>[d(`div`,fe,[g(p,{size:18,color:`var(--warning)`},{default:e(()=>[g(h(t))]),_:1}),r[25]||=d(`span`,null,`通用错误码`,-1)])]),default:e(()=>[d(`table`,q,[r[34]||=d(`thead`,null,[d(`tr`,null,[d(`th`,null,`状态码`),d(`th`,null,`含义`)])],-1),d(`tbody`,null,[d(`tr`,null,[d(`td`,null,[g(C,{type:`success`,size:`small`},{default:e(()=>[...r[26]||=[a(`200`,-1)]]),_:1})]),r[27]||=d(`td`,null,`成功`,-1)]),d(`tr`,null,[d(`td`,null,[g(C,{type:`danger`,size:`small`},{default:e(()=>[...r[28]||=[a(`401`,-1)]]),_:1})]),r[29]||=d(`td`,null,`认证失败 — API Key 无效或 Token 过期`,-1)]),d(`tr`,null,[d(`td`,null,[g(C,{type:`danger`,size:`small`},{default:e(()=>[...r[30]||=[a(`404`,-1)]]),_:1})]),r[31]||=d(`td`,null,`设备/传感器不存在`,-1)]),d(`tr`,null,[d(`td`,null,[g(C,{type:`danger`,size:`small`},{default:e(()=>[...r[32]||=[a(`500`,-1)]]),_:1})]),r[33]||=d(`td`,null,`服务器内部错误`,-1)])])])]),_:1})])}}}),[[`__scopeId`,`data-v-973a3906`]]);export{J as default};