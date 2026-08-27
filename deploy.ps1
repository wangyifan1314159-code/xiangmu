# ==============================================================================
# IoT 工业物联网与大数据平台 · 自动化部署与构建工具 v2.0
# ==============================================================================

param(
    [string]$Mode = 'full',
    [switch]$SkipFrontend,
    [switch]$SkipBackend
)

$ErrorActionPreference = 'Stop'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
Set-Location $ScriptDir

function Write-Banner {
    Write-Host ""
    Write-Host "==========================================================================" -ForegroundColor Cyan
    Write-Host "          IoT 工业物联网与大数据平台 · 自动化部署与构建工具 v2.0          " -ForegroundColor Cyan
    Write-Host "==========================================================================" -ForegroundColor Cyan
    Write-Host ""
}

function Write-Step {
    param([string]$Msg)
    Write-Host "[STEP] " -ForegroundColor Blue -NoNewline
    Write-Host ($Msg + "...") -ForegroundColor White
}

function Write-Success {
    param([string]$Msg)
    Write-Host " [OK] " -ForegroundColor Green -NoNewline
    Write-Host $Msg -ForegroundColor Green
}

function Write-Warn {
    param([string]$Msg)
    Write-Host " [WARN] " -ForegroundColor Yellow -NoNewline
    Write-Host $Msg -ForegroundColor Yellow
}

function Write-Err {
    param([string]$Msg)
    Write-Host " [ERR] " -ForegroundColor Red -NoNewline
    Write-Host $Msg -ForegroundColor Red
}

function Check-Command {
    param([string]$CmdName, [string]$DisplayTitle, [bool]$Required = $true)
    if (Get-Command $CmdName -ErrorAction SilentlyContinue) {
        Write-Success ($DisplayTitle + " (" + $CmdName + ") 已就绪")
        return $true
    } else {
        if ($Required) {
            Write-Err ("缺少核心依赖: " + $DisplayTitle + " (" + $CmdName + ")。请安装后重试！")
            exit 1
        } else {
            Write-Warn ("可选依赖未找到: " + $DisplayTitle + " (" + $CmdName + ")")
            return $false
        }
    }
}

function Check-Environment {
    Write-Step "1/5 检查系统运行环境依赖"
    Check-Command "docker" "Docker 引擎" $true
    Check-Command "node" "Node.js 运行时" $true
    Check-Command "npm" "NPM 包管理器" $true
    Check-Command "mvn" "Apache Maven 构建工具" $true
    Check-Command "java" "Java 17+ 运行时" $true
    Write-Host ""
}

function Ensure-EnvFile {
    Write-Step "2/5 初始化部署环境变量 (.env)"
    $EnvPath = Join-Path $ScriptDir ".env"
    if (-not (Test-Path $EnvPath)) {
        Write-Warn ".env 文件不存在，正在自动生成安全随机密钥与默认配置..."
        $JwtSecret = [Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Max 256 }))
        $DbPass = "IotPlatform#" + (Get-Random -Minimum 1000 -Maximum 9999) + "!Deploy"
        $EmqxDashPass = "EmqxDash_" + [System.Guid]::NewGuid().ToString("N").Substring(0, 12)
        $MinioPass = "Minio_" + [System.Guid]::NewGuid().ToString("N").Substring(0, 12)
        
        $lines = @(
            "# 自动生成的部署环境变量",
            ("DB_PASSWORD=" + $DbPass),
            ("APP_JWT_SECRET=" + $JwtSecret),
            ("EMQX_DASHBOARD_PASSWORD=" + $EmqxDashPass),
            "EMQX_MQTT_USERNAME=iot-platform",
            "EMQX_MQTT_PASSWORD=iotplatform2024",
            "MINIO_ROOT_USER=iotminio",
            ("MINIO_ROOT_PASSWORD=" + $MinioPass)
        )
        $lines | Out-File -FilePath $EnvPath -Encoding utf8
        Write-Success ".env 已生成并保存"
    } else {
        Write-Success ".env 配置文件已存在"
    }
    Write-Host ""
}

function Build-FrontendProject {
    if ($SkipFrontend) {
        Write-Warn "跳过前端构建 (-SkipFrontend)"
        return
    }
    Write-Step "3/5 编译前端 Vue3 + TypeScript 工程 (vite-project)"
    $ViteDir = Join-Path $ScriptDir "vite-project"
    Push-Location $ViteDir
    try {
        if (-not (Test-Path "node_modules")) {
            Write-Step "正在安装前端依赖 (npm install)"
            & npm install
        }
        Write-Step "执行生产环境打包 (npm run build)"
        & npm run build
        if ($LASTEXITCODE -ne 0) {
            Write-Err ("前端编译失败，退出码: " + $LASTEXITCODE)
            exit $LASTEXITCODE
        }
        Write-Success "前端打包成功"

        $BackendStaticDir = Join-Path $ScriptDir "iot-backend\src\main\resources\static"
        if (-not (Test-Path $BackendStaticDir)) {
            New-Item -ItemType Directory -Path $BackendStaticDir -Force | Out-Null
        }
        Write-Step "同步前端静态资源至后端 static 目录"
        Copy-Item -Path "dist\*" -Destination $BackendStaticDir -Recurse -Force
        Write-Success "前端资源已成功嵌入后端工程"
    } finally {
        Pop-Location
    }
    Write-Host ""
}

function Build-BackendProject {
    if ($SkipBackend) {
        Write-Warn "跳过后端构建 (-SkipBackend)"
        return
    }
    Write-Step "4/5 编译并打包 Java 后端及大数据多模块工程 (Maven)"
    Push-Location $ScriptDir
    try {
        & mvn clean package -DskipTests
        if ($LASTEXITCODE -ne 0) {
            Write-Err ("Maven 构建失败，退出码: " + $LASTEXITCODE)
            exit $LASTEXITCODE
        }
        Write-Success "所有 Java 模块打包完成 (iot-common, iot-backend, iot-flink-jobs, iot-data-service)"
    } finally {
        Pop-Location
    }
    Write-Host ""
}

function Deploy-StandardStack {
    Write-Step "5/5 启动 Docker Compose 标准生产集群 (PostgreSQL + EMQX + iot-platform)"
    Push-Location $ScriptDir
    try {
        & docker compose up -d --build
        if ($LASTEXITCODE -ne 0) {
            Write-Err "Docker Compose 启动失败"
            exit $LASTEXITCODE
        }
        Write-Success "容器已在后台启动，等待服务就绪与健康自检..."
        
        $maxRetries = 15
        $retries = 0
        $isHealthy = $false
        while ($retries -lt $maxRetries) {
            Start-Sleep -Seconds 3
            $retries++
            try {
                $resp = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -TimeoutSec 3 -ErrorAction SilentlyContinue
                if ($resp.status -eq "UP") {
                    $isHealthy = $true
                    break
                }
            } catch {
                Write-Host "." -NoNewline
            }
        }
        Write-Host ""

        if ($isHealthy) {
            Write-Success "iot-platform 后端及前端服务已成功启动且健康状态为 [UP]！"
        } else {
            Write-Warn "服务启动耗时较长，请通过 docker logs -f iot-platform 观察实时状态。"
        }
    } finally {
        Pop-Location
    }
}

function Deploy-BigDataStack {
    Write-Step "启动大数据湖仓一体化全栈 (Kafka + Flink + MinIO + Redis + TDengine)"
    Push-Location $ScriptDir
    try {
        & docker compose -f docker-compose-bigdata.yml up -d
        Write-Success "大数据基础设施集群已成功拉起！"
    } finally {
        Pop-Location
    }
}

function Show-Dashboard {
    Write-Host ""
    Write-Host "==========================================================================" -ForegroundColor DarkCyan
    Write-Host "                    平台部署成功 · 服务访问清单                           " -ForegroundColor Green
    Write-Host "==========================================================================" -ForegroundColor DarkCyan
    Write-Host " Web 前端与平台控制台 : http://localhost:8080" -ForegroundColor Yellow
    Write-Host "    - 初始超级管理员账号 : admin / admin123" -ForegroundColor Gray
    Write-Host "    - 初始普通用户账号   : user / user123" -ForegroundColor Gray
    Write-Host "    - 概览与数字孪生看板 : http://localhost:8080/dashboard" -ForegroundColor Gray
    Write-Host "    - 大数据分析大屏     : http://localhost:8080/bigdata" -ForegroundColor Gray
    Write-Host "    - AI 智能辅助系统    : http://localhost:8080/ai-assistant" -ForegroundColor Gray
    Write-Host ""
    Write-Host " 工业数据采集网关通道 : localhost:1884 (Netty TCP JSON 行协议)" -ForegroundColor Cyan
    Write-Host " EMQX MQTT 消息代理   : localhost:1883 (TCP) / localhost:8083 (WS)" -ForegroundColor Cyan
    Write-Host "    - EMQX 管理控制台    : http://localhost:18083" -ForegroundColor Gray
    Write-Host " PostgreSQL 业务数据库 : localhost:5432 (Database: iotdb)" -ForegroundColor Cyan
    if ($Mode -eq "bigdata") {
        Write-Host " Flink Stream 控制台   : http://localhost:8081" -ForegroundColor Magenta
        Write-Host " MinIO 湖仓对象存储    : http://localhost:9001" -ForegroundColor Magenta
        Write-Host " Apache Kafka 消息总线 : localhost:9092 / 9094" -ForegroundColor Magenta
    }
    Write-Host "==========================================================================" -ForegroundColor DarkCyan
    Write-Host ""
    Write-Host "常用管理指令:" -ForegroundColor White
    Write-Host "  查看日志: docker logs -f iot-platform" -ForegroundColor DarkGray
    Write-Host "  停止服务: .\deploy.ps1 -Mode stop" -ForegroundColor DarkGray
    Write-Host "  重启部署: .\deploy.ps1" -ForegroundColor DarkGray
    Write-Host ""
}

Write-Banner

switch ($Mode) {
    "full" {
        Check-Environment
        Ensure-EnvFile
        Build-FrontendProject
        Build-BackendProject
        Deploy-StandardStack
        Show-Dashboard
    }
    "bigdata" {
        Check-Environment
        Ensure-EnvFile
        Build-BackendProject
        Deploy-BigDataStack
        Show-Dashboard
    }
    "build-only" {
        Check-Environment
        Ensure-EnvFile
        Build-FrontendProject
        Build-BackendProject
        Write-Success "全部代码已编译打包完成"
    }
    "dev" {
        Check-Environment
        Ensure-EnvFile
        Write-Step "启动基础设施容器 (PostgreSQL 与 EMQX)"
        & docker compose up -d postgres emqx
        Write-Success "数据库与 MQTT 代理已启动。请在本地启动后端与前端开发服务！"
    }
    "stop" {
        Write-Step "停止所有运行中的 Docker 容器"
        & docker compose down
        try {
            & docker compose -f docker-compose-bigdata.yml down -ErrorAction SilentlyContinue
        } catch {}
        Write-Success "所有容器已停止"
    }
    "status" {
        Write-Step "当前运行容器列表:"
        & docker compose ps
    }
    "clean" {
        Write-Step "清理所有构建缓存和容器数据"
        & docker compose down -v
        & mvn clean
        Write-Success "清理完毕"
    }
    default {
        Write-Err ("未知模式: " + $Mode + "。可用选项: full | bigdata | build-only | dev | stop | status | clean")
        exit 1
    }
}
