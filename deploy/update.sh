#!/usr/bin/env bash
# ============================================================
# MindMan 更新脚本
#   代码更新后（重新上传了文件），在项目根目录执行：
#       sudo bash deploy/update.sh
#   它会重新构建并只重启变化的服务，数据库/Redis 数据不会丢失。
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_DIR}"

[ -f docker-compose.yml ] || { echo "未找到 docker-compose.yml"; exit 1; }

if [ "$(id -u)" -ne 0 ]; then
  command -v sudo >/dev/null 2>&1 && exec sudo -E bash "$0" "$@" || { echo "请用 root 运行"; exit 1; }
fi

if docker compose version >/dev/null 2>&1; then DC="docker compose"; else DC="docker-compose"; fi

echo "[更新] 等待数据库就绪……"
${DC} up -d mysql redis nacos
ready=no
for i in $(seq 1 60); do
  if ${DC} exec -T mysql sh -c 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; mysqladmin ping -h 127.0.0.1 -uroot --silent' >/dev/null 2>&1; then ready=yes; break; fi
  sleep 2
done
[ "$ready" = yes ] || { echo "数据库尚未就绪，更新停止"; exit 1; }
bash deploy/migrate.sh

echo "[更新] 重新构建并启动（仅重建有变化的镜像）……"
${DC} up -d --build

echo "[更新] 完成，当前状态："
${DC} ps
