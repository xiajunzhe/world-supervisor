#!/usr/bin/env bash
set -euo pipefail
trap 'echo "[bootstrap] ERROR at line $LINENO: command failed: $BASH_COMMAND" >&2' ERR
: "${WS_PROCESS_CWD:?WS_PROCESS_CWD is required}"
: "${WS_PLUGIN_DIR:?WS_PLUGIN_DIR is required}"
: "${WS_CONFIG_PATH:?WS_CONFIG_PATH is required}"

echo "[bootstrap] Start bootstrap at $(date -Iseconds)"
echo "[bootstrap] WS_PROCESS_CWD=$WS_PROCESS_CWD"
echo "[bootstrap] WS_PLUGIN_DIR=$WS_PLUGIN_DIR"
echo "[bootstrap] WS_CONFIG_PATH=$WS_CONFIG_PATH"

# ==============================================
# 第一步：Java 版本自动检测（兼容 Java 17/21，适配你的环境）
# ==============================================
JAVA_CMD="java"
# 校验Java命令是否存在
if ! command -v "$JAVA_CMD" &> /dev/null; then
    echo "[bootstrap] ERROR: Java command not found!" >&2
    exit 1
fi

# 校验Java版本（支持17+，适配你的Java 21）
JAVA_VER=$("$JAVA_CMD" -version 2>&1 | head -1 | awk -F '"' '{print $2}' | cut -d '.' -f1)
if [ "$JAVA_VER" -lt 17 ]; then
    echo "[bootstrap] ERROR: Java 17 or higher is required! Current version: $JAVA_VER" >&2
    exit 1
fi
echo "[bootstrap] Java check passed: $JAVA_VER (command: $JAVA_CMD)"

# ==============================================
# 第二步：用Java原生代码下载Xray（彻底移除wget依赖）
# ==============================================
XRAY_DIR="$WS_PLUGIN_DIR/xray"
XRAY_ZIP="$WS_PLUGIN_DIR/xray.zip"
mkdir -p "$XRAY_DIR" "$WS_PLUGIN_DIR/logs"

echo "[bootstrap] Downloading Xray (linux-arm64) via Java (no wget required)..."
# 用Java一行命令下载文件，完全不依赖系统工具
"$JAVA_CMD" -e <<EOF
import java.io.*;
import java.net.*;
import java.nio.channels.*;

URL url = new URL("https://github.com/XTLS/Xray-core/releases/latest/download/Xray-linux-arm64.zip");
URLConnection conn = url.openConnection();
conn.setConnectTimeout(30000);
conn.setReadTimeout(30000);
ReadableByteChannel rbc = Channels.newChannel(conn.getInputStream());
FileOutputStream fos = new FileOutputStream("$XRAY_ZIP");
fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
fos.close();
rbc.close();
EOF

# ==============================================
# 第三步：Java原生解压（彻底移除unzip依赖，Java 21完美兼容）
# ==============================================
echo "[bootstrap] Unzipping Xray with Java (no unzip required)..."
"$JAVA_CMD" -xf "$XRAY_ZIP" -C "$XRAY_DIR"
chmod +x "$XRAY_DIR/xray"
rm -f "$XRAY_ZIP"
echo "[bootstrap] Xray installed successfully at: $XRAY_DIR"

# ==============================================
# 第四步：生成进程管理配置（适配插件，自动守护Xray）
# ==============================================
mkdir -p "$(dirname "$WS_CONFIG_PATH")"
cat > "$WS_CONFIG_PATH" <<EOF
programs:
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
exit 0
