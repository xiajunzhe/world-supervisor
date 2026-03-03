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

mkdir -p "$(dirname "$WS_CONFIG_PATH")"

cat > "$WS_CONFIG_PATH" <<EOF
programs:
  - name: hello-world-once
    directory: "$WS_PLUGIN_DIR"
    command: ["/bin/bash", "-lc", "echo hello-world"]
    autostart: true
    autorestart: false
    startretries: 0
    exitcodes: [0]
    stopwaitsecs: 5
    logfile: "logs/hello-world-once.log"
EOF

echo "[bootstrap] Generated supervisor config: $WS_CONFIG_PATH"
echo "[bootstrap] Bootstrap completed successfully"
