#!/usr/bin/env bash
# ==============================================================================
# IoT 工业物联网与大数据平台 · 一键自动化构建与部署脚本 (Linux / macOS / WSL)
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

MODE="${1:-full}"

GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${CYAN}╔══════════════════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║           IoT 工业物联网与大数据平台 · 自动化部署与构建工具 v2.0             ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════════════════════════════╝${NC}"

check_cmd() {
    if command -v "$1" >/dev/null 2>&1; then
        echo -e " ${GREEN}[✔]${NC} $2 ($1) 已就绪"
    else
        echo -e " ${RED}[✘]${NC} 缺少核心依赖: $2 ($1)。请安装后重试！"
        exit 1
    fi
}

check_env() {
    echo -e "${CYAN}[STEP] 1/5 检查系统运行环境依赖...${NC}"
    check_cmd "docker" "Docker 引擎"
    check_cmd "node" "Node.js 运行时"
    check_cmd "npm" "NPM 包管理器"
    check_cmd "mvn" "Apache Maven 构建工具"
    check_cmd "java" "Java 17+ 运行时"
    echo ""
}

ensure_env_file() {
    echo -e "${CYAN}[STEP] 2/5 初始化部署环境变量 (.env)...${NC}"
    if [ ! -f .env ]; then
        echo -e " ${YELLOW}[!]${NC} .env 文件不存在，正在自动生成安全随机密钥与默认配置..."
        JWT_SECRET=$(openssl rand -base64 48 2>/dev/null || head -c 32 /dev/urandom | base64)
        DB_PASS="IotPlatform#$(shuf -i 1000-9999 -n 1 2>/dev/null || echo 2024)!Deploy"
        EMQX_DASH_PASS="$(openssl rand -hex 8 2>/dev/null || openssl rand -hex 6)"
        EMQX_MQTT_PASS="Iot$(openssl rand -hex 12 2>/dev/null || head -c 12 /dev/urandom | base64 | tr -d '=+/')"
        REDIS_PASS="$(openssl rand -hex 12 2>/dev/null || head -c 12 /dev/urandom | base64 | tr -d '=+/')"
        cat <<EOF > .env
# 自动生成的部署环境变量
DB_PASSWORD=$DB_PASS
APP_JWT_SECRET=$JWT_SECRET
EMQX_DASHBOARD_PASSWORD=$EMQX_DASH_PASS
EMQX_MQTT_USERNAME=iot-platform
EMQX_MQTT_PASSWORD=$EMQX_MQTT_PASS
REDIS_PASSWORD=$REDIS_PASS
MINIO_ROOT_USER=iotminio
MINIO_ROOT_PASSWORD=Minio_$(openssl rand -hex 6 2>/dev/null || echo 9d3a7c1f5b8e)
EOF
        echo -e " ${GREEN}[✔]${NC} .env 已生成并保存"
    else
        echo -e " ${GREEN}[✔]${NC} .env 配置文件已存在"
    fi
    echo ""
}

build_frontend() {
    echo -e "${CYAN}[STEP] 3/5 编译前端 Vue3 + TypeScript 工程 (vite-project)...${NC}"
    cd "$SCRIPT_DIR/vite-project"
    if [ ! -d "node_modules" ]; then
        echo "正在安装前端依赖 (npm install)..."
        npm install
    fi
    npm run build
    mkdir -p "$SCRIPT_DIR/iot-backend/src/main/resources/static"
    cp -r dist/* "$SCRIPT_DIR/iot-backend/src/main/resources/static/"
    echo -e " ${GREEN}[✔]${NC} 前端打包完成并同步至后端静态资源目录"
    cd "$SCRIPT_DIR"
    echo ""
}

build_backend() {
    echo -e "${CYAN}[STEP] 4/5 编译并打包 Java 后端及大数据多模块工程 (Maven)...${NC}"
    mvn clean package -DskipTests
    echo -e " ${GREEN}[✔]${NC} 所有 Java 模块打包完成 (Jar 位于 target 目录)"
    echo ""
}

deploy_docker() {
    echo -e "${CYAN}[STEP] 5/5 启动 Docker Compose 标准生产集群 (PostgreSQL + EMQX + iot-platform)...${NC}"
    docker compose up -d --build
    echo -e " ${GREEN}[✔]${NC} 容器已在后台启动，等待服务就绪..."
    sleep 8
}

show_dashboard() {
    echo ""
    echo -e "${CYAN}════════════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}                      🎉 平台部署成功 · 服务访问清单                           ${NC}"
    echo -e "${CYAN}════════════════════════════════════════════════════════════════════════════════${NC}"
    echo -e " ${YELLOW}🌐 Web 前端与平台控制台 : http://localhost:8080${NC}"
    echo -e "    ├─ 默认种子账号已禁用；如需演示账号请设置 APP_SEED_USERS_ENABLED=true 并立即修改密码"
    echo -e "    ├─ 概览 & 掘进机孪生  : http://localhost:8080/dashboard"
    echo -e "    ├─ 大数据分析大屏     : http://localhost:8080/bigdata"
    echo -e "    └─ AI 智能辅助系统    : http://localhost:8080/ai-assistant"
    echo ""
    echo -e " ${CYAN}🔌 工业数据采集网关通道 : localhost:1884 (Netty TCP JSON 行协议)${NC}"
    echo -e " ${CYAN}📡 EMQX MQTT 消息代理   : localhost:1883 (TCP) / localhost:8083 (WS)${NC}"
    echo -e "    └─ EMQX 管理控制台    : http://localhost:18083 (admin / 查阅 .env)"
    echo -e " ${CYAN}🗄️ PostgreSQL 业务数据库 : localhost:5432 (Database: iotdb)${NC}"
    echo -e "${CYAN}════════════════════════════════════════════════════════════════════════════════${NC}"
    echo ""
}

case "$MODE" in
    full)
        check_env
        ensure_env_file
        build_frontend
        build_backend
        deploy_docker
        show_dashboard
        ;;
    bigdata)
        check_env
        ensure_env_file
        build_backend
        docker compose -f docker-compose-bigdata.yml up -d
        show_dashboard
        ;;
    build-only)
        check_env
        ensure_env_file
        build_frontend
        build_backend
        ;;
    dev)
        check_env
        ensure_env_file
        docker compose up -d postgres emqx
        echo -e " ${GREEN}[✔]${NC} 基础设施已启动。请在本地启动后端与前端开发服务！"
        ;;
    stop)
        docker compose down
        docker compose -f docker-compose-bigdata.yml down 2>/dev/null || true
        echo -e " ${GREEN}[✔]${NC} 所有容器已停止"
        ;;
    status)
        docker compose ps
        ;;
    clean)
        docker compose down -v
        mvn clean
        echo -e " ${GREEN}[✔]${NC} 清理完毕"
        ;;
    *)
        echo "未知模式: $MODE。可用选项: full | bigdata | build-only | dev | stop | status | clean"
        exit 1
        ;;
esac
