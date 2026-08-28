# 03. 核心技术攻关与缺陷修复台账 (Bugfix & RCA Ledger)

> **规范标准**：MetaGPT Root Cause Analysis (RCA) 规范 & Ponytail 质量追踪矩阵  
> **文档版本**：v2.2.0 (Release)  
> **编制角色**：Full-Stack Engineer & QA / Security Engineer  

---

## 📋 核心技术攻关与缺陷修复全览

| 编号 | 缺陷 / 攻关主题 | 严重级别 | 影响模块 | 根因分类 | 状态 | 关联 Commit |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **BUG-01** | Netty TCP 客户端自动断连与粘包拆包 JSON 解析崩溃 | 🔴 紧急 (P0) | `iot-backend` TCP 网关 | 传输层帧界定缺失 / 线程阻塞 | ✅ 已修复 | `62d9662`, `16d2da8` |
| **BUG-02** | 实时遥测数据刷新导致大屏卡片剧烈抖动与高度跳变 | 🟠 高危 (P1) | `DashboardView.vue` | CSS 高度塌陷 / DOM 销毁重建 | ✅ 已修复 | `16d2da8` |
| **BUG-03** | 瓦斯浓度单帧毛刺频发误报与重复告警高频刷库 | 🟠 高危 (P1) | `MethaneAlertService.java` | 缺乏滑动窗口防抖 / 状态机去重缺失 | ✅ 已修复 | `16d2da8` |
| **BUG-04** | AI 智能诊断系统切换预设或刷新页面丢失拉取的模型列表 | 🟡 中危 (P2) | `AiAssistantView.vue` | 状态机未接入 LocalStorage 缓存 | ✅ 已修复 | `9d93f43` |
| **BUG-05** | 监控大屏全屏切换后 ECharts 实例变形留白与尺寸失真 | 🟡 中危 (P2) | `DashboardView.vue` | 全屏生命周期未绑定 Resize 响应 | ✅ 已修复 | `2b5b430` |
| **BUG-06** | Windows PowerShell 5.1 解析 `deploy.ps1` 报语法异常与 NPM 异常 | 🟡 中危 (P2) | `deploy.ps1` | UTF-8 BOM 缺失 / 命令行参数绑定异常 | ✅ 已修复 | `878eed5`, `7b3ef85` |
| **BUG-07** | 企业级安全审查发现的 17 项高危/中危安全漏洞 | 🔴 紧急 (P0) | 全栈 (JWT/CORS/SQL) | 鉴权逻辑漏洞 / 缺乏输入过滤 | ✅ 已修复 | `83b231e` |

---

## 🔍 核心缺陷深度复盘与 RCA 根因分析

---

### 📌 BUG-01: Netty TCP 客户端自动断连与粘包拆包异常

#### 1. 现象描述
在工业设备或仿真脚本通过 TCP 端口 `1884` 持续高频上报 JSON 数据流时：
1. 后端日志频繁报出 `com.fasterxml.jackson.core.JsonParseException: Unexpected character ('{' (code 123))`。
2. 前端大屏上的掘进机实时数据偶尔中断，随后客户端连接被服务端主动 Close。

#### 2. 根因分析 (RCA)
- **粘包与拆包 (TCP Sticky/Unpack Packets)**：TCP 是基于字节流的面向连接协议，高并发下操作系统内核缓冲区将多个 JSON 报文合并或切片发送，服务端直接用常规 String 解码导致多个 JSON 粘连在一起，Jackson 无法解析导致抛出异常并断开 Channel。
- **IdleStateHandler 超时参数过短**：原有心跳检测设为 15 秒，工业边缘网络出现瞬间延迟时被误判为死连接切断。

#### 3. 解决方案与代码实现
1. **引入定界符解码器**：在 Netty Pipeline 最前端挂载 `DelimiterBasedFrameDecoder`，并配合 `StringDecoder(StandardCharsets.UTF_8)`。
2. **优化通道空闲检测**：心跳超时放宽至 60 秒，并在检测到心跳丢失时先发送 `{"type":"ping"}` 探测帧，双向确认无应答后再执行资源释放。

```java
// 核心修复：iot-backend/src/main/java/com/iot/service/TcpListenerManager.java
@Override
protected void initChannel(SocketChannel ch) {
    ChannelPipeline pipeline = ch.pipeline();
    // 1. 限制最大帧长 4096 字节，以 \n 作为消息换行定界符
    pipeline.addLast(new DelimiterBasedFrameDecoder(4096, Delimiters.lineDelimiter()));
    pipeline.addLast(new StringDecoder(StandardCharsets.UTF_8));
    pipeline.addLast(new StringEncoder(StandardCharsets.UTF_8));
    // 2. 读写空闲 60s 触发 IdleStateEvent
    pipeline.addLast(new IdleStateHandler(60, 0, 0, TimeUnit.SECONDS));
    pipeline.addLast(tcpMessageHandler);
}
```

---

### 📌 BUG-02: 实时遥测数据刷新导致大屏卡片剧烈跳动

#### 1. 现象描述
大屏在每秒接收 WebSocket 推送的传感器数据时，卡片容器不断上下跳动、页面出现剧烈闪烁，导致用户无法正常点击操作或观察图表。

#### 2. 根因分析 (RCA)
- **DOM 高度坍塌与动态伸缩**：原有卡片未设置 `min-height` 和 `flex-shrink: 0`，数值从整数变为小数（如 `25 -> 25.432`）或告警标签出现/消失时引起容器尺寸微变。
- **Vue 模板全量重渲染**：子组件使用了动态随机 Key，导致每次数据变更时整个 Card DOM 树被销毁并重新挂载，ECharts 实例被重复销毁与重建。

#### 3. 解决方案与代码实现
1. **尺寸锁死与 CSS 隔离**：为大屏所有卡片容器设置严格的固定/最小高度，开启 `overflow: hidden` 与 `box-sizing: border-box`。
2. **响应式局部更新**：移除随机 Key，通过 Vue `reactive` 定点更新数值属性，图表更新改用 `chart.setOption(..., { notMerge: false })` 增量更新。

---

### 📌 BUG-03: 瓦斯浓度单帧毛刺频发误报与重复告警高频刷库

#### 1. 现象描述
矿井瓦斯传感器在受到井下电磁干扰时，偶发单帧数据跳变至 $1.2\%$（超安全阈值 $1.0\%$），下一帧立即回落至 $0.3\%$。系统每次均触发全矿警报并在数据库 `alert_record` 表中疯狂插入数千条重复告警，导致数据库连接池耗尽。

#### 2. 根因分析 (RCA)
- 告警判定为纯**瞬时阈值比较**（`currentVal > threshold`），缺乏时间维度的去噪滤波与滑动窗口确认机制。
- 告警入库未设计“告警中（ACTIVE）”状态机，处于超限区间内的每一帧都执行 `INSERT` 语句。

#### 3. 解决方案与代码实现
1. **多帧滑动防抖算法 (Multi-Frame Window Debouncing)**：引入滑动窗口（如连续 3 帧超限方确认为真实超限）。
2. **告警生命周期状态机**：设计 `ACTIVE -> RECOVERED` 状态迁移，超限触发时仅新增一条激活告警，在恢复安全线前持续更新 `last_trigger_time`，彻底杜绝重复落库。

```java
// 核心逻辑：iot-backend/src/main/java/com/iot/service/MethaneAlertService.java
public synchronized void processMethaneTelemetry(String deviceId, double ch4Value) {
    FixedSizeQueue<Double> window = deviceMethaneWindows.computeIfAbsent(deviceId, k -> new FixedSizeQueue<>(3));
    window.add(ch4Value);
    
    // 连续 3 帧均超过安全阈值 (1.0%)
    boolean isConsistentlyExceeded = window.isFull() && window.stream().allMatch(v -> v >= safetyThreshold);
    
    AlertRecord activeAlert = alertRecordRepository.findActiveAlert(deviceId, "METHANE_CH4_OVERFLOW");
    if (isConsistentlyExceeded) {
        if (activeAlert == null) {
            // 首次触发：新建告警记录并持久化，广播紧急事件
            AlertRecord newAlert = new AlertRecord(deviceId, "METHANE_CH4_OVERFLOW", "CRITICAL", ch4Value, "ACTIVE");
            alertRecordRepository.save(newAlert);
            webSocketPushService.broadcastAlert(newAlert);
        } else {
            // 持续超限：更新极值与时间戳，不重复 INSERT
            activeAlert.setMaxValue(Math.max(activeAlert.getMaxValue(), ch4Value));
            activeAlert.setLastUpdateTime(LocalDateTime.now());
            alertRecordRepository.save(activeAlert);
        }
    } else if (activeAlert != null && ch4Value < safetyThreshold * 0.9) {
        // 浓度降至安全线以下：自动恢复告警
        activeAlert.setStatus("RECOVERED");
        activeAlert.setEndTime(LocalDateTime.now());
        alertRecordRepository.save(activeAlert);
        webSocketPushService.broadcastAlertRecovery(activeAlert);
    }
}
```

---

### 📌 BUG-04: AI 智能诊断系统切换预设或刷新页面丢失配置

#### 1. 现象描述
用户在 `AiAssistantView` 界面配置自定义 API Key，或拉取了本地 Ollama 大模型列表（如 `deepseek-r1:7b`, `qwen2.5:14b`），切换预设提供商或刷新浏览器后，已拉取的模型下拉框变为空白，自定义端点丢失。

#### 2. 根因分析 (RCA)
模型列表及选中的活跃模型保存在 Vue 组件的内存响应式变量（`ref([])`）中，未对接浏览器 LocalStorage；切换预设时，直接执行了全量清空逻辑。

#### 3. 解决方案与代码实现
1. 建立 `AI_CONFIG_STORAGE_KEY` 持久化字典，在配置修改或模型拉取成功时同步写入 LocalStorage。
2. 组件在 `onMounted` 阶段执行缓存恢复，切换预设时采用差异合并而非暴力重置。

---

### 📌 BUG-05: 监控大屏全屏切换后 ECharts 图表变形留白

#### 1. 现象描述
在 `DashboardView` 界面点击右上角“全屏监控”或按下 `F11` 进入全屏模式时，大屏整体居中，但左侧瓦斯折线图、右侧进尺饼图等尺寸未按全屏比例拉伸，出现明显黑边与留白。

#### 2. 根因分析 (RCA)
- 浏览器在执行全屏动画切换时，DOM 尺寸的实际改变存在数十毫秒的渐变过渡期，立即调用 `chart.resize()` 时获取到的容器宽高仍为过渡前尺寸。
- 未监听标准 `document.addEventListener('fullscreenchange', ...)` 事件。

#### 3. 解决方案与代码实现
1. 监听标准 `fullscreenchange` 与 `resize` 事件。
2. 引入 `setTimeout` 与 `requestAnimationFrame` 双重延迟重绘机制，确保在 DOM 尺寸完全沉降后执行图表自适应。

---

### 📌 BUG-06: Windows PowerShell 5.1 解析 `deploy.ps1` 编码与参数异常

#### 1. 现象描述
在 Windows 默认终端运行 `./deploy.ps1` 时，报出 `无法识别的标记: ֻScriptBlock` 或在执行 `npm run build` 时报参数绑定异常。

#### 2. 根因分析 (RCA)
- PowerShell 5.1 默认将无 BOM 的 UTF-8 文本按 Windows 本地 ANSI (GBK/CP936) 解析，导致脚本中的中文字符与特型引号破坏了语法分析器。
- 命令行调用 `npm` 时，PowerShell 5.1 对带空格路径或内联参数的传递机制与 Linux 存在差异。

#### 3. 解决方案与代码实现
1. 使用带有 UTF-8 BOM (`0xEF, 0xBB, 0xBF`) 签名重新保存 `deploy.ps1`。
2. 将原生直接调用 `npm` 改为 `& npm.cmd run build` 显示显式调用，并在最外层提供免配置的 `deploy.bat` 批处理入口。

---

### 📌 BUG-07: 全栈 17 项企业级安全漏洞深度收敛加固

#### 1. 审查与修复明细表

| 序号 | 漏洞类型 | 涉及组件/文件 | 修复前隐患 | 修复后加固策略 |
| :--- | :--- | :--- | :--- | :--- |
| **S-01** | JWT 签名绕过 | `iot-data-service/.../JwtValidator.java` | 允许空密钥或默认弱密钥通过验证 | 强制校验密钥长度 $\ge 256	ext{bit}$，增加过期时间与 Issuer 强校验 |
| **S-02** | CORS 跨域任意信任 | `WebSecurityConfig.java` | `allowedOriginPatterns("*")` 且开启凭证 | 严格限制白名单域名，收敛 Allowed Methods 与 Headers |
| **S-03** | SQL 注入风险 | `DataService.java`, `schema.sql` | 存在动态拼接的 `${orderBy}` 字段 | 全面采用预编译绑定 `#{...}`，字段排序采用枚举白名单校验 |
| **S-04** | API Key 敏感信息泄露 | `AiController.java`, 前端展示 | 日志和前端报文全量打印明文 Key | 引入掩码器，仅显示前 4 位与后 4 位（如 `sk-8f****3d`） |
| **S-05** | 路径遍历漏洞 | `DashboardView.vue`, 静态路由 | 静态资源下载未校验 `../` 越界路径 | 增加 `normalize()` 路径净化与根目录白名单限制 |
| **S-06** | XSS 跨站脚本攻击 | `AiAssistantView.vue` | Markdown 渲染直接使用 `v-html` | 引入 `DOMPurify` 库对 HTML 标签与属性进行严格白名单清洗 |
| **S-07** | TCP 未鉴权报文注入 | `TcpMessageHandler.java` | 未鉴权通道可直接发送 telemetry 报文 | 强制握手鉴权，首帧非 auth 报文直接强制断开连接 |
