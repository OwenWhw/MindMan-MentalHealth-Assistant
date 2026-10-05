#!/usr/bin/env bash
# ============================================================
# MindMan 一键部署脚本
#   适用系统：Ubuntu / Debian / Alibaba Cloud Linux / CentOS / Rocky
#   使用方法：把项目上传到服务器后，在项目根目录执行
#             sudo bash deploy/deploy.sh
#
#   它会依次完成：
#     1. 检查系统、安装 Docker 与 Compose 插件（用国内镜像源加速）
#     2. 配置 Docker 镜像加速 + 日志轮转
#     3. 内存较小(<2G)时自动创建 swap，避免构建时内存不足
#     4. 自动生成随机 JWT 密钥与数据库密码，写入 .env
#     5. 构建镜像并启动 mysql/redis/nacos/backend/frontend 全套服务
#     6. 做健康检查并打印访问地址
#
#   可重复执行：已完成的步骤会自动跳过，不会破坏已有数据。
# ============================================================
set -euo pipefail

# ---------------- 彩色输出 ----------------
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; BLUE='\033[0;36m'; NC='\033[0m'
info(){ echo -e "${GREEN}[信息]${NC} $*"; }
warn(){ echo -e "${YELLOW}[提示]${NC} $*"; }
err(){  echo -e "${RED}[错误]${NC} $*" >&2; }
die(){ err "$*"; exit 1; }
step(){ echo -e "\n${BLUE}==== $* ====${NC}"; }

# ---------------- 定位项目根目录 ----------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_DIR}"
[ -f docker-compose.yml ] || die "未找到 docker-compose.yml。请确认 deploy/ 目录与 docker-compose.yml 在同一项目根目录下。"
info "项目目录：${PROJECT_DIR}"

# ---------------- 需要 root 权限 ----------------
if [ "$(id -u)" -ne 0 ]; then
  if command -v sudo >/dev/null 2>&1; then
    info "需要管理员权限，正在通过 sudo 重新运行……"
    exec sudo -E bash "$0" "$@"
  else
    die "请切换到 root 用户后再运行本脚本。"
  fi
fi

# ============================================================
# 1. 安装 Docker 与 Compose 插件
# ============================================================
step "步骤 1/6：检查并安装 Docker"

install_docker() {
  info "开始安装 Docker（使用阿里云镜像源下载，速度更快）……"
  if ! command -v curl >/dev/null 2>&1; then
    if command -v apt-get >/dev/null 2>&1; then
      apt-get update -y && apt-get install -y curl
    elif command -v dnf >/dev/null 2>&1; then
      dnf install -y curl
    elif command -v yum >/dev/null 2>&1; then
      yum install -y curl
    fi
  fi
  curl -fsSL https://get.docker.com -o /tmp/get-docker.sh \
    || die "下载 Docker 安装脚本失败，请检查服务器能否访问外网（或先配置好网络）。"
  sh /tmp/get-docker.sh --mirror Aliyun
}

if command -v docker >/dev/null 2>&1; then
  info "已检测到 Docker，跳过安装。"
else
  install_docker
fi

# 启动并开机自启
systemctl enable --now docker >/dev/null 2>&1 || service docker start >/dev/null 2>&1 || true

# 等待 Docker 就绪
for i in $(seq 1 30); do
  docker info >/dev/null 2>&1 && break
  sleep 2
done
docker info >/dev/null 2>&1 || die "Docker 未能正常启动，请查看 systemctl status docker。"

# 确定 compose 命令形式
if docker compose version >/dev/null 2>&1; then
  DC="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  DC="docker-compose"
  warn "使用旧版 docker-compose（建议后续升级为 docker compose 插件）。"
else
  info "尝试安装 docker compose 插件……"
  if command -v apt-get >/dev/null 2>&1; then
    apt-get update -y && apt-get install -y docker-compose-plugin
  elif command -v dnf >/dev/null 2>&1; then
    dnf install -y docker-compose-plugin
  elif command -v yum >/dev/null 2>&1; then
    yum install -y docker-compose-plugin
  fi
  docker compose version >/dev/null 2>&1 || die "docker compose 插件安装失败，请手动安装后重试。"
  DC="docker compose"
fi
info "Compose 命令：${DC}"

# ============================================================
# 2. 配置镜像加速与日志轮转
# ============================================================
step "步骤 2/6：配置 Docker 镜像加速"

if [ ! -f /etc/docker/daemon.json ]; then
  mkdir -p /etc/docker
  cat > /etc/docker/daemon.json <<'EOF'
{
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://docker.1panel.live",
    "https://hub.rat.dev"
  ],
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" }
}
EOF
  info "已写入 /etc/docker/daemon.json，重启 Docker 生效。"
  systemctl restart docker >/dev/null 2>&1 || service docker restart >/dev/null 2>&1 || true
  sleep 3
else
  warn "已存在 /etc/docker/daemon.json，保持不动（如需加速请自行修改）。"
fi

# ============================================================
# 3. swap（内存不足时兜底）
# ============================================================
step "步骤 3/6：检查内存与 swap"

MEM_MB=$(free -m | awk '/^Mem:/{print $2}')
info "物理内存：${MEM_MB} MB"

if swapon --show 2>/dev/null | grep -q .; then
  info "已存在 swap，跳过创建。"
elif [ "${MEM_MB}" -lt 2048 ]; then
  info "内存小于 2G，创建 4G swap 以防构建时内存不足……"
  if [ ! -f /swapfile ]; then
    fallocate -l 4G /swapfile 2>/dev/null || dd if=/dev/zero of=/swapfile bs=1M count=4096
    chmod 600 /swapfile
    mkswap /swapfile
  fi
  swapon /swapfile && info "swap 已启用。" || warn "swap 启用失败，将继续（构建可能较慢）。"
  grep -q '^/swapfile' /etc/fstab 2>/dev/null || echo '/swapfile none swap sw 0 0' >> /etc/fstab
else
  info "内存充足，无需额外 swap。"
fi

# ============================================================
# 4. 生成 .env
# ============================================================
step "步骤 4/6：生成环境变量文件 .env"

gen_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex "$1"
  else
    head -c "$1" /dev/urandom | od -An -tx1 | tr -d ' \n'
  fi
}

if [ -f .env ]; then
  warn ".env 已存在，保留现有配置（不会覆盖你的密钥与密码）。"
else
  JWT_SECRET="$(gen_secret 32)"
  DB_PASSWORD="$(gen_secret 16)"
  cat > .env <<EOF
# ============================================================
# 由 deploy.sh 自动生成于 $(date '+%Y-%m-%d %H:%M:%S')
# ⚠️ 本文件含密码与密钥，请勿提交到 Git、勿随意分享。
# ============================================================
MYSQL_ROOT_PASSWORD=${DB_PASSWORD}
MYSQL_DATABASE=mindman
MYSQL_PORT=3306
REDIS_PORT=6379
NACOS_PORT=8848
BACKEND_PORT=8080
FRONTEND_PORT=80

JWT_SECRET=${JWT_SECRET}

# 云端 AI：留空时应用使用本地模拟回复。填入百炼 API Key 可启用真实大模型。
AI_PROVIDER=auto
AI_BAILIAN_API_KEY=
AI_OLLAMA_ENABLED=false
AI_RAG_ENABLED=false
EOF
  chmod 600 .env
  info "已生成 .env（含随机密钥）。"
fi

# ============================================================
# 5. 构建并启动
# ============================================================
step "步骤 5/6：构建镜像并启动服务（首次约 5-15 分钟，请耐心等待）"

info "开始构建……（这一步最耗时，中间会输出大量日志，属正常现象）"
${DC} up -d --build || die "启动失败，请执行 '${DC} logs' 查看具体错误。"

# ============================================================
# 6. 健康检查
# ============================================================
step "步骤 6/6：等待服务就绪并检查"

PUBLIC_IP="$(curl -s --max-time 5 http://100.100.100.200/latest/meta-data/eipv4 2>/dev/null || true)"
[ -n "${PUBLIC_IP}" ] || PUBLIC_IP="$(curl -s --max-time 5 https://ifconfig.me 2>/dev/null || true)"
[ -n "${PUBLIC_IP}" ] || PUBLIC_IP="<你的服务器公网IP>"

FRONTEND_PORT="$(grep -E '^FRONTEND_PORT=' .env | cut -d= -f2)"
FRONTEND_PORT="${FRONTEND_PORT:-80}"

info "等待后端启动（最多约 90 秒）……"
BACKEND_OK="no"
for i in $(seq 1 30); do
  if curl -fsS "http://127.0.0.1:${FRONTEND_PORT}/" >/dev/null 2>&1; then
    BACKEND_OK="yes"; break
  fi
  sleep 3
done

echo ""
${DC} ps || true
echo ""

if [ "${BACKEND_OK}" = "yes" ]; then
  echo -e "${GREEN}============================================================${NC}"
  echo -e "${GREEN} ✅ 部署完成！${NC}"
  echo -e "${GREEN}============================================================${NC}"
  if [ "${FRONTEND_PORT}" = "80" ]; then
    echo -e " 访问地址：${BLUE}http://${PUBLIC_IP}${NC}"
  else
    echo -e " 访问地址：${BLUE}http://${PUBLIC_IP}:${FRONTEND_PORT}${NC}"
  fi
  echo ""
  echo -e " ⚠️ 如果打不开，请检查阿里云【安全组】是否放行了 ${FRONTEND_PORT} 端口。"
  echo -e " 常用命令："
  echo -e "   查看状态：cd ${PROJECT_DIR} && ${DC} ps"
  echo -e "   查看日志：cd ${PROJECT_DIR} && ${DC} logs -f backend"
  echo -e "   停止服务：cd ${PROJECT_DIR} && ${DC} down"
  echo -e "   重启服务：cd ${PROJECT_DIR} && ${DC} restart"
  echo -e "${GREEN}============================================================${NC}"
else
  warn "前端页面暂时访问不通，服务可能仍在启动中。"
  echo -e " 请稍等 1-2 分钟后重试，或执行以下命令查看日志："
  echo -e "   cd ${PROJECT_DIR} && ${DC} logs -f backend"
  echo -e "   cd ${PROJECT_DIR} && ${DC} logs -f frontend"
fi
