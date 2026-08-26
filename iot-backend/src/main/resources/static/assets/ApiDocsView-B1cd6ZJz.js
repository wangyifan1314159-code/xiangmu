import{B as e,H as t,Kt as n,R as r,W as i,b as ee,d as a,et as o,f as s,i as c,m as l,p as u,u as d,ut as f,v as p,y as m,yt as h}from"./runtime-core.esm-bundler-CTpWx_R8.js";import{C as g,H as _,L as v,S as y,Z as b,g as x,j as S,q as C,r as w}from"./index-BPCdnHaL.js";import{t as T}from"./_plugin-vue_export-helper-B67ILkmu.js";var te={class:`api-docs`},ne={class:`card-title`},re={class:`quick-list`},ie={class:`quick-item-header`},ae={class:`quick-item-title`},oe={class:`quick-code-wrapper`},E={class:`quick-code`},D={class:`card-title`},O={class:`auth-box`},k={class:`auth-box-header`},A={class:`api-section`},j={class:`section-tabs`},M={class:`section-header`},N={class:`section-title`},P={class:`section-sub`},F={class:`api-card-top`},I={class:`api-method-row`},L={class:`api-path-display`},R={class:`api-card-title`},z={class:`api-card-desc`},B={key:0,class:`api-table-block`},V={class:`param-table`},se={key:1,class:`api-table-block`},H={class:`param-table`},U={key:2,class:`api-table-block`},W={class:`param-table`},G={class:`code-section`},K={class:`code-section-header`},ce={class:`code-block`},le={class:`code-section`},ue={class:`code-section-header`},de={class:`code-block`},fe={class:`card-title`},q={class:`param-table`},J=T(ee({__name:`ApiDocsView`,setup(ee){let T=f(`quick`),J=f(``),Y=window.location.origin,X=d(()=>Q.find(e=>e.id===T.value)),pe=d(()=>X.value?.apis||[]);function Z(e,t){navigator.clipboard.writeText(e),t&&(J.value=t,setTimeout(()=>J.value=``,1500)),w.success({message:`已复制`,duration:1200})}let me=[{title:`上传光照数据`,icon:b,lang:`curl`,code:`curl -X POST "${Y}/api/data/dev_b087404c" \\
  -H "X-Api-Key: your_device_api_key" \\
  -H "Content-Type: application/json" \\
  -d '{"sensorId": "s_1781530543162", "value": 55.5}'`,id:`quick-upload`},{title:`查询最新数据`,icon:g,lang:`curl`,code:`curl -H "X-Api-Key: your_device_api_key" \\
  "${Y}/api/data/dev_b087404c/latest?sensorId=s_1781530543162&limit=5"`,id:`quick-query`},{title:`查询设备详情`,icon:y,lang:`curl`,code:`curl -H "X-Api-Key: your_device_api_key" \\
  "${Y}/api/devices/dev_b087404c"`,id:`quick-device`},{title:`发送执行器指令`,icon:C,lang:`curl`,code:`curl -X POST "${Y}/api/data/dev_414305e8/command" \\
  -H "X-Api-Key: your_device_api_key" \\
  -H "Content-Type: application/json" \\
  -d '{"command": "1", "params": {"actuator": "电动开关"}}'`,id:`quick-command`},{title:`Python 上传示例`,icon:_,lang:`python`,code:`import requests

resp = requests.post(
    "${Y}/api/data/dev_b087404c",
    json={"sensorId": "s_1781530543162", "value": 55.5},
    headers={"X-Api-Key": "your_device_api_key"},
    timeout=5
)
print(resp.json())`,id:`quick-python`}],Q=[{id:`upload`,title:`数据上报`,icon:b,desc:`设备向平台发送传感器数据`,apis:[{method:`POST`,path:`/api/data/{deviceId}`,title:`上报传感器数据`,desc:`向指定设备上报传感器读数。后端自动写入时序数据库、更新实时缓存、触发告警评估。`,auth:`X-Api-Key`,pathParams:[{name:`deviceId`,type:`string`,desc:`设备 ID（如 dev_b087404c）`}],bodyParams:[{name:`sensorId`,type:`string`,required:!0,desc:`传感器 ID（如 s_1781530543162）`},{name:`value`,type:`number`,required:!0,desc:`传感器读数值`}],headers:[{name:`X-Api-Key`,desc:`设备 API Key（从设备详情页获取）`,required:!0},{name:`Content-Type`,desc:`application/json`,required:!0}],example:`curl -X POST "${Y}/api/data/dev_b087404c" \\
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
}`,id:`api-command`}]},{id:`query`,title:`数据查询`,icon:g,desc:`从平台查询已上报的传感器数据`,apis:[{method:`GET`,path:`/api/data/{deviceId}/latest`,title:`获取最新数据`,desc:`返回指定传感器最新 N 条读数，按时间倒序。适合轮询获取实时值。`,auth:`X-Api-Key`,pathParams:[{name:`deviceId`,type:`string`,desc:`设备 ID`}],queryParams:[{name:`sensorId`,type:`string`,required:!1,desc:`传感器 ID（不填返回全部传感器）`},{name:`limit`,type:`int`,required:!1,desc:`返回条数（默认 10）`}],example:`curl -H "X-Api-Key: your_device_api_key" \\
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
}`,id:`api-history`}]},{id:`device`,title:`设备信息`,icon:y,desc:`设备管理相关的查询接口`,apis:[{method:`GET`,path:`/api/devices`,title:`获取所有设备`,desc:`返回当前用户的所有设备列表（含传感器实时值）。`,auth:`Bearer Token`,example:`curl -H "Authorization: Bearer {token}" \\
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
}`,id:`api-device-detail`}]},{id:`mqtt`,title:`MQTT 接入`,icon:x,desc:`通过 MQTT 协议上报数据 / 下发指令（低功耗设备推荐，可用 MQTTX 等客户端直接联调）`,apis:[{method:`CONN`,path:`tcp://{服务器IP}:1883`,title:`Broker 连接参数`,desc:`平台内置 EMQX Broker，允许匿名连接（无需用户名密码）。MQTTX / 程序客户端按以下参数建立连接。`,auth:`匿名连接`,example:`# MQTTX 连接配置
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
2. WebSocket 推送前端，设备列表状态即时变化`,id:`mqtt-status`}]},{id:`tcp`,title:`TCP 接入`,icon:v,desc:`Netty TCP 长连接接入通道（端口 1884），适合 485 串口采集程序 / 单片机上位机直连，JSON 行协议（每帧一个 JSON，以 \\n 结尾）`,apis:[{method:`CONN`,path:`tcp://{服务器IP}:1884`,title:`TCP 连接参数与鉴权`,desc:`建立 TCP 连接后必须在 30 秒内发送 auth 帧，否则连接被断开。deviceId 与 apiKey 在设备详情页获取。`,auth:`deviceId + apiKey`,bodyParams:[{name:`type`,type:`string`,required:!0,desc:`固定 "auth"`},{name:`deviceId`,type:`string`,required:!0,desc:`设备 ID（如 dev_b087404c）`},{name:`apiKey`,type:`string`,required:!0,desc:`设备 API Key（与 X-Api-Key 相同）`}],example:`# 连接：nc {服务器IP} 1884 或任意 TCP 客户端
# 发送（注意结尾必须带换行 \\n）：
{"type":"auth","deviceId":"dev_b087404c","apiKey":"your_device_api_key"}\\n

# Java Socket 示例
Socket socket = new Socket("127.0.0.1", 1884);
Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
out.write("{\\"type\\":\\"auth\\",\\"deviceId\\":\\"dev_b087404c\\",\\"apiKey\\":\\"your_device_api_key\\"}\\n");
out.flush();`,response:`{"type":"auth_result","success":true,"deviceId":"dev_b087404c","message":"认证成功"}

# 失败时服务端主动断开：
{"type":"auth_result","success":false,"message":"设备ID或API Key不正确"}`,id:`tcp-conn`},{method:`SEND`,path:`telemetry 帧（设备 → 平台）`,title:`上报传感器数据`,desc:`鉴权通过后发送。处理链路与 MQTT/REST 上报完全一致：TDengine 时序落库 → Redis 缓存 → 告警评估 → WebSocket 推送前端。485 采集程序将协议帧拆解出的风速/温湿度/甲烷等物理量封装为本帧上送。`,auth:`需先完成 auth`,bodyParams:[{name:`type`,type:`string`,required:!0,desc:`固定 "telemetry"`},{name:`sensorId`,type:`string`,required:!0,desc:`传感器 ID`},{name:`value`,type:`number`,required:!0,desc:`物理量数值（甲烷 ppm / 风速 m/s 等）`},{name:`sensorType`,type:`string`,required:!1,desc:`类型: temperature / humidity / methane / wind_speed 等`},{name:`unit`,type:`string`,required:!1,desc:`单位: °C、%RH、ppm、m/s`}],example:`{"type":"telemetry","sensorId":"s_001","value":25.5,"sensorType":"temperature","unit":"°C"}\\n
{"type":"telemetry","sensorId":"s_002","value":1200,"sensorType":"methane","unit":"ppm"}\\n`,response:`无逐条回执。数据可在前端实时页面与历史曲线中查看；
告警规则（如 甲烷 > 1000 ppm）命中时自动产生告警记录并推送。`,id:`tcp-telemetry`},{method:`SEND`,path:`status / command_result 帧（设备 → 平台）`,title:`上报设备状态 / 指令回执`,desc:`status 更新设备在线状态；command_result 用于回复平台下发的指令执行结果（可选）。`,auth:`需先完成 auth`,bodyParams:[{name:`type`,type:`string`,required:!0,desc:`"status" 或 "command_result"`},{name:`status`,type:`string`,required:!1,desc:`"ONLINE" / "OFFLINE"（type=status 时必填）`},{name:`command`,type:`string`,required:!1,desc:`被回执的指令名（type=command_result 时）`},{name:`success`,type:`boolean`,required:!1,desc:`执行是否成功（type=command_result 时）`}],example:`{"type":"status","status":"ONLINE"}\\n
{"type":"command_result","command":"on","success":true,"message":"executed"}\\n`,response:`status 帧处理后设备列表状态即时刷新；
command_result 当前记录到平台日志，可扩展写入指令历史。`,id:`tcp-status`},{method:`RECV`,path:`command 帧（平台 → 设备）`,title:`接收下行控制指令`,desc:`通过 REST POST /api/data/{deviceId}/command 或 MQTT 下发指令时，若设备存在在线 TCP 连接，平台自动透传本帧。设备执行后建议回复 command_result。`,auth:`—`,example:`// 平台下发（JSON 行，UTF-8）：
{"type":"command","command":"on","params":{"actuator":"风扇"},"timestamp":1730000000000}\\n`,response:`设备侧解析 command 与 params 后驱动执行机构，
再回发 {"type":"command_result","command":"on","success":true}\\n`,id:`tcp-command`},{method:`GET`,path:`/api/tcp/status`,title:`查询 TCP 通道状态`,desc:`返回 TCP 通道是否启用及当前在线设备数。app.tcp.enabled=false 时 enabled 返回 false。`,auth:`Bearer Token`,example:`curl -H "Authorization: Bearer {token}" \\
  "${Y}/api/tcp/status"`,response:`{
  "success": true,
  "data": { "enabled": true, "onlineDevices": 3 }
}`,id:`tcp-rest-status`},{method:`GET`,path:`/api/tcp/connections`,title:`在线连接列表 / 强制断开`,desc:`列出当前 TCP 在线连接实例（普通用户仅见自己设备，管理员见全部）。管理员可 DELETE /api/tcp/connections/{deviceId} 强制断开指定设备。`,auth:`Bearer Token（断开仅限 ADMIN）`,example:`curl -H "Authorization: Bearer {token}" \\
  "${Y}/api/tcp/connections"

# 强制断开（管理员）
curl -X DELETE -H "Authorization: Bearer {token}" \\
  "${Y}/api/tcp/connections/dev_b087404c"`,response:`{
  "success": true,
  "data": [{ "deviceId": "dev_b087404c", "remoteAddr": "192.168.1.50:52333", "authAt": "..." }]
}`,id:`tcp-rest-connections`}]}];return(ee,d)=>{let f=t(`el-icon`),C=t(`el-tag`),w=t(`el-button`),Y=t(`el-card`),Q=t(`el-col`),he=t(`el-row`),$=t(`el-radio-button`),ge=t(`el-radio-group`);return r(),l(`div`,te,[d[35]||=a(`div`,{class:`page-header`},[a(`div`,null,[a(`h2`,null,`数据接口文档`),a(`span`,{class:`header-desc`},`设备数据上报、查询与命令下发 REST API 参考`)])],-1),m(Y,{class:`quick-card`,shadow:`never`},{header:o(()=>[a(`div`,ne,[m(f,{size:18,color:`var(--accent)`},{default:o(()=>[m(h(_))]),_:1}),d[2]||=a(`span`,null,`快速测试`,-1),m(C,{size:`small`,type:`success`,effect:`dark`,class:`quick-badge`},{default:o(()=>[...d[1]||=[p(`直接复制运行`,-1)]]),_:1})])]),default:o(()=>[a(`div`,re,[(r(),l(c,null,e(me,e=>a(`div`,{key:e.id,class:`quick-item`},[a(`div`,ie,[m(f,{size:14,color:`var(--accent)`},{default:o(()=>[(r(),s(i(e.icon)))]),_:2},1024),a(`span`,ae,n(e.title),1),m(C,{size:`small`,effect:`plain`,class:`quick-lang`},{default:o(()=>[p(n(e.lang),1)]),_:2},1024)]),a(`div`,oe,[a(`pre`,E,[a(`code`,null,n(e.code),1)]),m(w,{size:`small`,type:J.value===e.id?`success`:`default`,class:`quick-copy-btn`,onClick:t=>Z(e.code,e.id)},{default:o(()=>[p(n(J.value===e.id?`已复制 ✓`:`复制`),1)]),_:2},1032,[`type`,`onClick`])])])),64))])]),_:1}),m(Y,{class:`auth-card`,shadow:`never`},{header:o(()=>[a(`div`,D,[m(f,{size:18,color:`var(--accent)`},{default:o(()=>[m(h(S))]),_:1}),d[3]||=a(`span`,null,`认证方式`,-1)])]),default:o(()=>[m(he,{gutter:24},{default:o(()=>[m(Q,{span:12},{default:o(()=>[a(`div`,O,[a(`div`,k,[m(C,{type:`success`,effect:`dark`,size:`small`},{default:o(()=>[...d[4]||=[p(`推荐`,-1)]]),_:1}),d[5]||=a(`span`,null,`X-Api-Key（设备 Key）`,-1)]),d[6]||=a(`code`,{class:`code-inline`},`-H "X-Api-Key: {apiKey}"`,-1),d[7]||=a(`p`,{class:`auth-box-desc`},`在设备详情页可查看复制。适用于设备/脚本端直接调用。`,-1)])]),_:1}),m(Q,{span:12},{default:o(()=>[...d[8]||=[a(`div`,{class:`auth-box`},[a(`div`,{class:`auth-box-header`},[a(`span`,null,`Bearer Token（用户登录）`)]),a(`code`,{class:`code-inline`},`-H "Authorization: Bearer {token}"`),a(`p`,{class:`auth-box-desc`},`通过 POST /api/auth/login 获取。适用于 Web 前端调用。`)],-1)]]),_:1})]),_:1})]),_:1}),a(`div`,A,[a(`div`,j,[m(ge,{modelValue:T.value,"onUpdate:modelValue":d[0]||=e=>T.value=e,size:`small`},{default:o(()=>[m($,{value:`quick`},{default:o(()=>[m(f,{size:13},{default:o(()=>[m(h(_))]),_:1}),d[9]||=p(` 快速测试`,-1)]),_:1}),m($,{value:`upload`},{default:o(()=>[m(f,{size:13},{default:o(()=>[m(h(b))]),_:1}),d[10]||=p(` 数据上报`,-1)]),_:1}),m($,{value:`query`},{default:o(()=>[m(f,{size:13},{default:o(()=>[m(h(g))]),_:1}),d[11]||=p(` 数据查询`,-1)]),_:1}),m($,{value:`device`},{default:o(()=>[m(f,{size:13},{default:o(()=>[m(h(y))]),_:1}),d[12]||=p(` 设备信息`,-1)]),_:1}),m($,{value:`mqtt`},{default:o(()=>[m(f,{size:13},{default:o(()=>[m(h(x))]),_:1}),d[13]||=p(` MQTT 接入`,-1)]),_:1}),m($,{value:`tcp`},{default:o(()=>[m(f,{size:13},{default:o(()=>[m(h(v))]),_:1}),d[14]||=p(` TCP 接入`,-1)]),_:1})]),_:1},8,[`modelValue`])]),X.value?(r(),l(`div`,{key:X.value.id,class:`section-body`},[a(`div`,M,[m(f,{size:20,color:`var(--accent)`},{default:o(()=>[(r(),s(i(X.value.icon)))]),_:1}),a(`div`,null,[a(`h3`,N,n(X.value.title),1),a(`p`,P,n(X.value.desc),1)])]),(r(!0),l(c,null,e(pe.value,t=>(r(),s(Y,{key:t.id,class:`api-card`,shadow:`never`},{default:o(()=>[a(`div`,F,[a(`div`,I,[m(C,{type:t.method===`POST`?`success`:t.method===`GET`?`primary`:`warning`,size:`small`,effect:`dark`},{default:o(()=>[p(n(t.method),1)]),_:2},1032,[`type`]),a(`code`,L,n(t.path),1),m(C,{size:`small`,effect:`plain`,type:`info`},{default:o(()=>[p(n(t.auth),1)]),_:2},1024)]),a(`h4`,R,n(t.title),1),a(`p`,z,n(t.desc),1)]),t.pathParams?(r(),l(`div`,B,[d[16]||=a(`span`,{class:`api-section-label`},`路径参数`,-1),a(`table`,V,[d[15]||=a(`thead`,null,[a(`tr`,null,[a(`th`,null,`参数`),a(`th`,null,`类型`),a(`th`,null,`说明`)])],-1),a(`tbody`,null,[(r(!0),l(c,null,e(t.pathParams,e=>(r(),l(`tr`,{key:e.name},[a(`td`,null,[a(`code`,null,n(e.name),1)]),a(`td`,null,[m(C,{size:`small`,effect:`plain`},{default:o(()=>[p(n(e.type),1)]),_:2},1024)]),a(`td`,null,n(e.desc),1)]))),128))])])])):u(``,!0),t.queryParams?(r(),l(`div`,se,[d[18]||=a(`span`,{class:`api-section-label`},`Query 参数`,-1),a(`table`,H,[d[17]||=a(`thead`,null,[a(`tr`,null,[a(`th`,null,`参数`),a(`th`,null,`类型`),a(`th`,null,`必填`),a(`th`,null,`说明`)])],-1),a(`tbody`,null,[(r(!0),l(c,null,e(t.queryParams,e=>(r(),l(`tr`,{key:e.name},[a(`td`,null,[a(`code`,null,n(e.name),1)]),a(`td`,null,[m(C,{size:`small`,effect:`plain`},{default:o(()=>[p(n(e.type),1)]),_:2},1024)]),a(`td`,null,[m(C,{type:e.required?`danger`:`info`,size:`small`},{default:o(()=>[p(n(e.required?`是`:`否`),1)]),_:2},1032,[`type`])]),a(`td`,null,n(e.desc),1)]))),128))])])])):u(``,!0),t.bodyParams?(r(),l(`div`,U,[d[20]||=a(`span`,{class:`api-section-label`},`Body 参数 (JSON)`,-1),a(`table`,W,[d[19]||=a(`thead`,null,[a(`tr`,null,[a(`th`,null,`参数`),a(`th`,null,`类型`),a(`th`,null,`必填`),a(`th`,null,`说明`)])],-1),a(`tbody`,null,[(r(!0),l(c,null,e(t.bodyParams,e=>(r(),l(`tr`,{key:e.name},[a(`td`,null,[a(`code`,null,n(e.name),1)]),a(`td`,null,[m(C,{size:`small`,effect:`plain`},{default:o(()=>[p(n(e.type),1)]),_:2},1024)]),a(`td`,null,[m(C,{type:e.required?`danger`:`info`,size:`small`},{default:o(()=>[p(n(e.required?`是`:`否`),1)]),_:2},1032,[`type`])]),a(`td`,null,n(e.desc),1)]))),128))])])])):u(``,!0),a(`div`,G,[a(`div`,K,[m(f,{size:14},{default:o(()=>[m(h(x))]),_:1}),d[22]||=a(`span`,null,`示例请求`,-1),m(w,{size:`small`,text:``,type:`primary`,onClick:e=>Z(t.example,t.id)},{default:o(()=>[...d[21]||=[p(`复制`,-1)]]),_:1},8,[`onClick`])]),a(`pre`,ce,[a(`code`,null,n(t.example),1)])]),a(`div`,le,[a(`div`,ue,[d[24]||=a(`span`,null,`响应示例`,-1),m(w,{size:`small`,text:``,type:`primary`,onClick:e=>Z(t.response,t.id+`-resp`)},{default:o(()=>[...d[23]||=[p(`复制`,-1)]]),_:1},8,[`onClick`])]),a(`pre`,de,[a(`code`,null,n(t.response),1)])])]),_:2},1024))),128))])):u(``,!0)]),m(Y,{class:`error-card`,shadow:`never`},{header:o(()=>[a(`div`,fe,[m(f,{size:18,color:`var(--warning)`},{default:o(()=>[m(h(S))]),_:1}),d[25]||=a(`span`,null,`通用错误码`,-1)])]),default:o(()=>[a(`table`,q,[d[34]||=a(`thead`,null,[a(`tr`,null,[a(`th`,null,`状态码`),a(`th`,null,`含义`)])],-1),a(`tbody`,null,[a(`tr`,null,[a(`td`,null,[m(C,{type:`success`,size:`small`},{default:o(()=>[...d[26]||=[p(`200`,-1)]]),_:1})]),d[27]||=a(`td`,null,`成功`,-1)]),a(`tr`,null,[a(`td`,null,[m(C,{type:`danger`,size:`small`},{default:o(()=>[...d[28]||=[p(`401`,-1)]]),_:1})]),d[29]||=a(`td`,null,`认证失败 — API Key 无效或 Token 过期`,-1)]),a(`tr`,null,[a(`td`,null,[m(C,{type:`danger`,size:`small`},{default:o(()=>[...d[30]||=[p(`404`,-1)]]),_:1})]),d[31]||=a(`td`,null,`设备/传感器不存在`,-1)]),a(`tr`,null,[a(`td`,null,[m(C,{type:`danger`,size:`small`},{default:o(()=>[...d[32]||=[p(`500`,-1)]]),_:1})]),d[33]||=a(`td`,null,`服务器内部错误`,-1)])])])]),_:1})])}}}),[[`__scopeId`,`data-v-a14ab624`]]);export{J as default};