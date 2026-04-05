#!/bin/sh
set -eu

# 环境变量校验
: "${WS_PROCESS_CWD:?WS_PROCESS_CWD is required}"
: "${WS_PLUGIN_DIR:?WS_PLUGIN_DIR is required}"
: "${WS_CONFIG_PATH:?WS_CONFIG_PATH is required}"

echo "[bootstrap] Start bootstrap"
echo "[bootstrap] WS_PLUGIN_DIR=$WS_PLUGIN_DIR"

# Java 检测
if ! command -v java >/dev/null 2>&1; then
    echo "[bootstrap] ERROR: java not found" >&2
    exit 1
fi

# 定义路径
XRAY_DIR="$WS_PLUGIN_DIR/xray"
XRAY_ZIP="$WS_PLUGIN_DIR/xray.zip"
mkdir -p "$XRAY_DIR" "$WS_PLUGIN_DIR/logs"

# 用 Java 下载（无 wget 依赖）
echo "[bootstrap] Downloading Xray via Java..."
java -e <<EOF
import java.io.*;
import java.net.*;
import java.nio.channels.*;
URL url = new URL("https://github.com/XTLS/Xray-core/releases/latest/download/Xray-linux-arm64.zip");
URLConnection conn = url.openConnection();
ReadableByteChannel rbc = Channels.newChannel(conn.getInputStream());
FileOutputStream fos = new FileOutputStream("$XRAY_ZIP");
fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
fos.close();
rbc.close();
EOF

# 用 Java 解压（无 unzip 依赖）
echo "[bootstrap] Unzipping Xray via Java..."
java -xf "$XRAY_ZIP" -C "$XRAY_DIR"
chmod +x "$XRAY_DIR/xray"
rm -f "$XRAY_ZIP"

# 生成配置
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
EOF

echo "[bootstrap] Bootstrap completed"
exit 0
