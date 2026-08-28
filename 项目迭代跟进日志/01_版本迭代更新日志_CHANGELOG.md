# 01. 版本迭代更新日志 (Changelog)

> **规范标准**：Keep a Changelog 1.1.0 & MetaGPT Versioning Standard  
> **版本范围**：v1.0.0-alpha ~ v2.2.0 (Release)  
> **代码仓库**：`wangyifan1314159-code/xiangmu`  

---

## 📌 版本演进速览表

| 版本号 | 发布时间 | Git 提交标识 | 核心变更类型 | 简要描述 |
| :--- | :--- | :--- | :--- | :--- |
| **v2.2.0** | 2026-08-28 10:39 | `74a9aec` | `feat(dashboard)` | 大屏接入真实后端流数据（真实优先，Mock兜底双模机制） |
| **v2.1.2** | 2026-08-28 10:23 | `2b5b430` | `feat(dashboard)` | 大屏全面重构为 DataV 政企科技指挥风，修复全屏图表重绘 Bug |
| **v2.1.1** | 2026-08-28 09:56 | `00e8614` | `feat(dashboard)` | 监控大屏按方案B重构为经典三栏指挥布局（掘进机数字孪生中台） |
| **v2.1.0** | 2026-08-28 09:26 | `7b3ef85` | `fix(deploy)` | 修复 Windows PowerShell 5.1 解析 UTF-8 BOM 报错 |
| **v2.0.2** | 2026-08-28 08:43 | `83b231e` | `security` | 修复全栈安全审查发现的 17 处高危与中危漏洞（JWT/SQL/CORS） |
| **v2.0.1** | 2026-08-27 17:43 | `9d93f43` | `fix(ai)` | 增加 AI 模型列表与配置的 LocalStorage 持久化，防止预设切换丢失 |
| **v2.0.0** | 2026-08-27 17:32 | `c33dcd2`, `878eed5` | `feat(devops)` | 构建一键自动化全栈部署脚本体系 (`deploy.ps1`/`bat`/`sh`) 与大数据流闭环压测 |
| **v1.5.0** | 2026-08-27 16:45 | `16d2da8` | `feat(ui/ai)` | 重构概览与大屏UI，新增AI工业辅助诊断系统，修复卡片跳动与告警 |
| **v1.1.0** | 2026-08-27 10:47 | `9da529c`, `62d9662` | `fix/feat(backend)`| Netty TCP 协议重构、瓦斯多帧防抖告警、修复 Docker 路径非 ASCII 兼容 |
| **v1.0.0** | 2026-08-26 18:44 | `fd25d7c`, `4bf73c4` | `feat(init)` | 物联网大数据与实时湖仓一体化平台初始工程提交 (Spring Boot + Flink + Vue3) |

---

## 🚀 详细版本更新记录

### [v2.2.0] - 2026-08-28
#### 🌟 核心突破：大屏真实后端数据接入（真实优先，Mock兜底）
- **需求背景**：用户在实战演练与大赛演示中，需要大屏不仅能展现绚丽的视觉效果，更能实时消费来自 Spring Boot 后端、Netty TCP 采集通道以及 Flink 聚合中台的真实时序数据，同时在后端未启动或网络中断时无缝降级至 Mock 模式，确保大屏永不白屏。
- **Commit**：`74a9aec` - *feat(dashboard): 大屏接入真实后端数据（真实优先, mock 兜底）*
- **修改文件**：
  - `vite-project/src/views/dashboard/DashboardView.vue`
- **详细特性与代码变更**：
  1. **双模数据驱动机制 (Dual-Mode Data Pipeline)**：
     - 构建 `fetchRealOrMockData()` 统一调度网关：优先调用 `/api/dashboard/overview`、`/api/data/devices/realtime`、`/api/data/tunneling/latest`。
     - 捕获 HTTP 404/500 或网络断连异常，自动以毫秒级静默切换至本地拟真 Mock 生成器，控制台输出清晰诊断日志 `[Dashboard] Fallback to Mock Data Generator`。
  2. **掘进机实时姿态与刀盘遥测联动**：
     - 将中台 2D/3D 掘进机视图绑定的截割头转速 (`cutterRpm`)、推进速度 (`advanceSpeed`)、俯仰角 (`pitchAngle`)、滚转角 (`rollAngle`)、瓦斯浓度 (`ch4Value`) 全面绑定到真实后端数据字典。
  3. **自愈式心跳探针**：
     - 定时 3 秒轮询健康探针，一旦检测到真实后端恢复，自动无感切回真实流数据。

---

### [v2.1.2] - 2026-08-28
#### 🌟 核心突破：大屏改造为 DataV 政企指挥风并修复全屏 Bug
- **需求背景**：原有大屏视觉偏普通后台管理界面，缺乏工业级政企指挥中心的科技感、厚重感与沉浸式体验；同时在点击全屏按钮后，ECharts 图表未自适应重绘，产生局部留白或布局挤压。
- **Commit**：`2b5b430` - *feat(dashboard): 大屏改造为 DataV 政企指挥风并修复全屏 bug*
- **修改文件**：
  - `vite-project/src/views/dashboard/DashboardView.vue`
- **详细特性与代码变更**：
  1. **DataV 政企科技指挥中心视觉升级**：
     - 引入深邃黑金科技背景 `#060d1f` 与极光蓝渐变边框，重构顶部大屏标题栏（梯形装饰切割、动态呼吸发光灯带、实时工况状态指示器）。
     - 卡片容器统一采用高透毛玻璃效果与科技棱角边框（HUD 风格角标），强化工业大数据质感。
  2. **全屏切换与 Resize 响应式重构**：
     - 封装全屏管理 Hook `toggleFullScreen()`，兼容 `document.fullscreenElement` 与 Webkit/Moz 前缀。
     - 监听 `fullscreenchange` 与 `window.resize` 事件，对页面内 6 个 ECharts 实例统一执行 `chart.resize()`，彻底解决全屏缩放时图表变形或留白问题。

---

### [v2.1.1] - 2026-08-28
#### 🌟 核心突破：监控大屏按方案B重构为经典指挥大屏布局
- **需求背景**：用户明确提出将掘进机作为指挥中心的主视觉焦点，需将大屏从单调的瀑布流卡片改造为符合工业调度标准的“左-中-右”三栏经典指挥大屏架构。
- **Commit**：`00e8614` - *feat(dashboard): 监控大屏按方案B重构为经典指挥大屏布局*
- **修改文件**：
  - `vite-project/src/views/dashboard/DashboardView.vue`
  - `vite-project/public/roadheader.png`
- **详细特性与代码变更**：
  1. **方案B经典指挥大屏拓扑落地**：
     - **左侧 (28% 宽度)**：矿井安全感知态势（瓦斯 CH4 浓度趋势折线图、一氧化碳 CO 监控、温湿度环境指标、多级安全告警即时流）。
     - **中间 (44% 宽度)**：掘进机数字孪生中台（居中高清掘进机结构剖析图、六向姿态陀螺仪、截割臂伸缩行程、液压主泵压力状态仪表盘、紧急一键启停控制区）。
     - **右侧 (28% 宽度)**：生产效能与大数据流态势（当日进尺完成率仪表盘、设备综合健康指数 (PHM)、Flink 流计算吞吐量、Iceberg/Doris 写入速率指标）。
  2. **中台数字孪生热区标定**：
     - 在掘进机高清图上精准标注传感器热区（截割头温度测点、液压油温测点、主电机电流测点），悬浮即时显示遥测详情。

---

### [v2.1.0] - 2026-08-28
#### 🛠️ 运维与脚本兼容性修复：PowerShell 5.1 UTF-8 BOM 修复
- **需求背景**：Windows 10 / Server 默认搭载的 Windows PowerShell 5.1 在解析无 BOM 的 UTF-8 脚本文件时，将中文注释与输出字符解析为乱码，导致脚本在第 1 行解析失败或报语法异常。
- **Commit**：`7b3ef85` - *fix(deploy): deploy.ps1 补加 UTF-8 BOM 修复 Windows PowerShell 5.1 解析报错*
- **修改文件**：
  - `deploy.ps1`
- **详细修复点**：
  - 将 `deploy.ps1` 文件编码格式统一转为带有 UTF-8 Signature (BOM: `0xEF, 0xBB, 0xBF`) 格式，确保 PowerShell 5.1/7+ 及 CMD 批处理包装器均可稳定解析运行。

---

### [v2.0.2] - 2026-08-28
#### 🛡️ 企业级代码安全加固：17 项高危与中危漏洞全量收敛
- **需求背景**：针对全栈代码开展深层次安全合规审查，发现鉴权绕过风险、CORS 任意来源信任、SQL 拼接隐患、明文敏感配置及前端 XSS 风险，需全面加固以达到企业级上云安全标准。
- **Commit**：`83b231e` - *security: 修复安全审查发现的高危与中危漏洞*
- **修改文件**（共 17 个关键文件）：
  - `iot-data-service/src/main/java/com/iot/dataservice/config/JwtValidator.java`
  - `iot-data-service/src/main/java/com/iot/dataservice/config/SecurityConfig.java`
  - `iot-backend/src/main/java/com/iot/config/WebSecurityConfig.java`
  - `iot-backend/src/main/java/com/iot/controller/TcpController.java`
  - `iot-backend/src/main/java/com/iot/controller/AiController.java`
  - `vite-project/src/views/ai/AiAssistantView.vue`
  - `vite-project/src/api/realApi.ts`
  - 关联测试用例与环境配置文件
- **详细加固清单**：
  1. **JWT 签名密钥强制校验**：重构 `JwtValidator`，禁止空密钥/弱密钥，对所有进入 `iot-data-service` 的请求执行签名、过期时间与 Issuer 强校验。
  2. **CORS 跨域收敛**：废除 `allowedOriginPatterns("*")`，改为严格白名单校验，禁止携凭证 (`allowCredentials=true`) 与通配符同时存在。
  3. **SQL 参数化查询**：排查 MyBatis 与 JDBC 动态查询，全量消除 `${}` 字符串拼接，强制使用 `#{}` 预编译参数化。
  4. **AI 与 TCP 敏感凭证脱敏**：前端配置 API Key 增加掩码处理，后端日志输出过滤 Authorization 报文头与设备连接 Key。

---

### [v2.0.1] - 2026-08-27
#### 🛠️ AI 智能诊断系统加固：模型列表与配置本地持久化
- **需求背景**：用户在 `AiAssistantView` 界面配置自定义 OpenAI/Gemini 端点或成功拉取本地 Ollama 模型列表后，刷新页面或切换服务商预设会导致已拉取的模型列表和自定义配置丢失，操作体验中断。
- **Commit**：`9d93f43` - *fix(ai): 增加模型列表与配置的持久化存储，切换预设与重新加载不丢失已拉取模型*
- **修改文件**（涉及 23 个前后端资产）：
  - `vite-project/src/views/ai/AiAssistantView.vue`
  - `iot-backend/src/main/resources/static/...` (静态资源同步)
- **详细修复点**：
  1. **LocalStorage 状态机持久化**：
     - 定义 `ai_model_registry_cache` 与 `ai_endpoint_configs` 存储规范，在 `onMounted` 钩子中实现静默恢复。
  2. **智能合并策略**：
     - 切换预设（如从 DeepSeek 切换到 Gemini 或 Ollama）时，保留用户已录入的 Key 与已探测成功的模型数组，避免重复拉取。

---

### [v2.0.0] - 2026-08-27
#### 🚀 自动化部署体系发布：一键 DevOps 自动化套件与流计算闭环
- **需求背景**：项目包含后端 Spring Boot 服务、前端 Vite 工程、大数据湖仓 Docker 容器群（Kafka/Flink/Doris/MinIO/TDengine）以及 Python 压测发生器，多环境人工启动繁琐易错，亟需一键自动化交付方案。
- **Commit**：`c33dcd2`, `878eed5` - *feat: 添加一键自动化部署脚本(deploy.ps1, deploy.bat, deploy.sh)与大数据流闭环测试*
- **修改文件**：
  - `deploy.ps1`, `deploy.bat`, `deploy.sh`
  - `docker-compose-bigdata.yml`
  - `scripts/ApiConnectivityTest.java`
  - `test_code/bigdata_simulator.py`
- **详细特性**：
  1. **全栈构建流水线编排**：
     - 自动检测并校验 Java 17+, Maven 3.8+, Node.js 18+, Docker 环境。
     - 执行 `mvn clean package` 打包 Jar，进入 `vite-project` 执行 `npm run build` 并自动将产物同构拷贝至 `iot-backend/src/main/resources/static/`。
  2. **自愈式健康检测探针**：
     - 启动后自动轮询 `http://localhost:8080/api/health` 与 `http://localhost:8088/api/health`，输出就绪就绪度报告。

---

### [v1.5.0] - 2026-08-27
#### 🌟 大版本重构：概览大屏升级、AI工业中枢与卡片防抖优化
- **需求背景**：解决多项痛点——大屏实时数据刷新时卡片布局上下跳动、无法直观查看设备瓦斯历史超限、缺乏基于大模型的设备异常智能排障功能。
- **Commit**：`16d2da8` - *feat: 重构概览与大屏UI、接入真实流数据、新增AI辅助系统及模型配置、修复卡片跳动与告警*
- **修改文件**（77 个前后端文件）：
  - `vite-project/src/views/dashboard/DashboardView.vue`
  - `vite-project/src/views/ai/AiAssistantView.vue`
  - `iot-backend/src/main/java/com/iot/service/MethaneAlertService.java`
  - `iot-backend/src/main/java/com/iot/controller/AiController.java`
- **核心变更**：
  1. **卡片尺寸锁死与 CSS 防抖**：固定指标卡片最小高度 `min-height: 120px`，数据更新采用局部 Text 替换而非 DOM 销毁重建，彻底消除跳动。
  2. **AI 工业助手上线**：实现支持 Markdown 渲染、代码块高亮、故障代码快速解析、实时多轮对话的工业 Copilot。
  3. **瓦斯多帧防抖算法**：设置 $N=3$ 连续帧过滤单点毛刺，报警记录去重写入持久化存储。

---

### [v1.1.0] - 2026-08-27
#### 🛠️ 边缘协议与内核修复：Netty TCP 协议重构与非 ASCII 路径支持
- **Commit**：`9da529c`, `62d9662`
- **核心变更**：
  1. **Netty 解码器升级**：引入 `DelimiterBasedFrameDecoder(4096, Delimiters.lineDelimiter())`，彻底解决 TCP 粘包拆包引发的 JSON 解析崩溃。
  2. **Docker Compose 跨平台修复**：显式声明 `name: iot-bigdata-platform`，解决 Windows 非 ASCII 文件夹路径导致的容器编排解析失败。

---

### [v1.0.0] - 2026-08-26
#### 🎉 初始基线发布：物联网大数据与实时湖仓一体化平台立项提交
- **Commit**：`fd25d7c`, `4bf73c4`
- **核心变更**：
  - 建立 Maven 多模块工程架构（`iot-common`, `iot-flink-jobs`, `iot-data-service`, `iot-backend`）。
  - 编写 Vue 3 + TypeScript 前端基础框架，集成 Element Plus 与 ECharts。
  - 编排大数据基础套件（EMQX, Kafka, Flink, MinIO, TDengine, Doris）。
