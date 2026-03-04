# World Supervisor

`World Supervisor` 是一个 Minecraft（Spigot/Paper/Purpur/Folia/Mohist/Arclight）插件，用于在服务端启动时拉起并管理外部进程，提供轻量级的进程守护能力。

## 功能概览

- 在插件启用时加载 `supervisor.yml`
- 按配置自动启动外部程序
- 支持进程退出后的重启策略（`always` / `never` / `unexpected`）
- 支持重试次数、重试间隔、停止等待时间
- 支持为每个进程注入环境变量（`environment`）
- 支持首启 bootstrap 脚本自动安装外部程序并生成配置

## 运行环境

- Java 21
- Spigot API `1.21.x`
- Linux（bootstrap 脚本执行流程按 Linux 设计）

## 项目结构

```text
src/main/java/com/github/vevc/
├─ WorldSupervisorPlugin.java        # 插件入口
├─ bootstrap/
│  └─ BootstrapService.java          # 首启脚本发现与执行
├─ config/
│  ├─ ProgramDefinition.java         # 单个程序配置实体
│  ├─ RestartPolicy.java             # 重启策略枚举
│  ├─ SupervisorConfig.java          # 配置聚合实体
│  └─ SupervisorConfigLoader.java    # 配置加载与默认模板生成
└─ supervisor/
   └─ ProcessSupervisor.java         # 进程守护逻辑
```

## 快速开始

### 1. 构建插件

```bash
mvn clean package -DskipTests
```

构建产物位于 `target/world-supervisor.jar`。

### 2. 部署到服务端

将 `world-supervisor.jar` 放入服务端 `plugins/` 目录。

### 3. 可选：首启自动安装

如果你希望首次启动自动安装外部程序并自动生成配置，可在 `plugins/` 同级放置：

- `world-supervisor.bootstrap.sh`

插件首次启动时会按以下优先级查找脚本：

1. `plugins/WorldSupervisor/bootstrap/install.sh`
2. `plugins/world-supervisor.bootstrap.sh`

脚本执行成功后会写入：

- `plugins/WorldSupervisor/bootstrap/bootstrap.done`

后续启动将跳过首启安装流程。

## bootstrap 脚本环境变量

插件执行脚本时会注入以下环境变量：

- `WS_PROCESS_CWD`：服务端 Java 进程当前工作目录
- `WS_PLUGIN_DIR`：插件数据目录绝对路径
- `WS_CONFIG_PATH`：`supervisor.yml` 绝对路径

项目根目录已提供示例脚本：`world-supervisor.bootstrap.sh`。

## 配置文件

配置文件路径：`plugins/WorldSupervisor/supervisor.yml`

配置文件结构如下：

```yaml
programs:
  - name: init-once
    directory: "/opt/world-supervisor/jobs"
    command: ["/bin/sh", "./init-once.sh"]
    autostart: true
    autorestart: false
    startretries: 0
    exitcodes: [0]
    stopwaitsecs: 10
    logfile: "logs/init-once.log"

  - name: health-check-loop
    directory: "/opt/world-supervisor/agents"
    command: ["/bin/sh", "./health-check-loop.sh"]
    autostart: true
    autorestart: unexpected
    startretries: 3
    exitcodes: [0, 2]
    stopwaitsecs: 10
    logfile: "logs/health-check-loop.log"
    restart_delay_secs: 3
    failure_retry_delay_secs: 5
    environment:
      APP_ENV: "prod"
      CHECK_INTERVAL_SEC: "30"
```

### 字段说明

- `name`：程序名称（唯一标识，建议可读）
- `directory`：工作目录，支持绝对路径和相对插件目录路径
- `command`：命令数组（建议使用相对路径 `./xx` 或绝对路径）
- `autostart`：插件启动时是否自动拉起
- `autorestart`：重启策略
  - `always` / `true`：总是重启
  - `never` / `false`：不重启
  - `unexpected`：仅非预期退出时重启
- `startretries`：最大重试次数
- `exitcodes`：正常退出码列表
- `stopwaitsecs`：停止时等待秒数，超时后强制结束
- `logfile`：日志文件路径；可设为 `/dev/null` 丢弃日志
- `restart_delay_secs`：退出后重启等待秒数
- `failure_retry_delay_secs`：异常后重试等待秒数
- `environment`：注入给进程的环境变量

## 默认行为

当 `supervisor.yml` 不存在时，插件会自动生成一个空配置：

```yaml
programs: []
```

因此即使未配置任何程序，插件也不会报错。

## 日志与排障

- 插件启动时会打印 `Server process cwd`
- bootstrap 日志路径：`plugins/WorldSupervisor/bootstrap/bootstrap.log`
- 若 bootstrap 失败，插件会跳过 supervisor 启动并输出失败原因
- 若 bootstrap 脚本执行超时（默认 15 分钟），会强制终止脚本进程

## 常见建议

- Linux 环境优先使用 `./binary` + `directory` 的写法，避免长绝对路径命令
- 对关键二进制下载建议增加校验（如 `sha256sum`）
- 将安装逻辑和配置生成逻辑收敛在 bootstrap 脚本，便于多环境定制

