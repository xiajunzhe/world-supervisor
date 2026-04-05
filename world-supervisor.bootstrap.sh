#!/usr/bin/env bash
set -euo pipefail
trap 'echo "[bootstrap] ERROR at line $LINENO" >&2' ERR
: "${WS_PROCESS_CWD:?WS_PROCESS_CWD is required}"
: "${WS_PLUGIN_DIR:?WS_PLUGIN_DIR is required}"
: "${WS_CONFIG_PATH:?WS_CONFIG_PATH is required}"

echo "[bootstrap] Start bootstrap at $(date -Iseconds)"
echo "[bootstrap] WS_PROCESS_CWD=$WS_PROCESS_CWD"
echo "[bootstrap] WS_PLUGIN_DIR=$WS_PLUGIN_DIR"
echo "[bootstrap] WS_CONFIG_PATH=$WS_CONFIG_PATH"

# ==============================================
# 新增：Java 17校验（插件必装，兜底检查无unzip环境）
# ==============================================
if ! command -v java &> /dev/null; then
    echo "[bootstrap] ERROR: Java not found! Please install Java 17 first." >&2
    exit 1
fi
JAVA_VER=$(java -version 2>&1 | head -1 | awk -F '"' '{print $2}' | cut -d '.' -f1)
if [ "$JAVA_VER" -ne 17 ]; then
    echo "[bootstrap] ERROR: Java 17 is required! Current version: $JAVA_VER" >&2
    exit 1
fi
echo "[bootstrap] Java 17 check passed: $JAVA_VER"

# ==============================================
# 新增：Xray安装（Java原生解压，无unzip依赖）
# 注意：根据游戏机架构替换Xray下载链接（默认arm64，游戏机主流架构）
# ==============================================
XRAY_DIR="$WS_PLUGIN_DIR/xray"
XRAY_ZIP="$WS_PLUGIN_DIR/xray.zip"
# 创建Xray目录
mkdir -p "$XRAY_DIR" "$WS_PLUGIN_DIR/logs"
# 下载Xray（arm64架构，游戏机通用；需要其他架构直接替换链接）
echo "[bootstrap] Downloading Xray for linux-arm64..."
wget -q -O "$XRAY_ZIP" https://github.com/XTLS/Xray-core/releases/latest/download/Xray-linux-arm64.zip --no-check-certificate
# 核心：Java原生解压（替换unzip，无任何系统依赖）
echo "[bootstrap] Unzipping Xray with Java (no unzip required)..."
java -xf "$XRAY_ZIP" -C "$XRAY_DIR"
# 给Xray加执行权限
chmod +x "$XRAY_DIR/xray"
# 删除压缩包节省空间
rm -f "$XRAY_ZIP"
echo "[bootstrap] Xray installed at: $XRAY_DIR"

# ==============================================
# 修改配置：保留原有格式，新增Xray进程管理（替换原hello-world示例）
# ==============================================
mkdir -p "$(dirname "$WS_CONFIG_PATH")"
cat > "$WS_CONFIG_PATH" <<EOF
programs:
  # 保留原有hello-world示例（可选，可删除）
  - name: hello-world-once
    directory: "$WS_PLUGIN_DIR"
    command: ["/bin/bash", "-lc", "echo hello-world"]
    autostart: true
    autorestart: false
    startretries: 0
    exitcodes: [0]
    stopwaitsecs: 5
    logfile: "logs/hello-world-once.log"
  # 新增：Xray进程管理（插件自动启动+异常重启）
  - name: xray-proxy
    directory: "$XRAY_DIR"
    command: ["./xray", "run", "-c", "config.json"]
    autostart: true
    autorestart: always
    startretries: 5
    exitcodes: [0]
    stopwaitsecs: 10
    logfile: "logs/xray.log"
    restart_delay_secs: 3
    failure_retry_delay_secs: 5
EOF

echo "[bootstrap] Generated supervisor config: $WS_CONFIG_PATH"
echo "[bootstrap] Bootstrap completed successfully"
