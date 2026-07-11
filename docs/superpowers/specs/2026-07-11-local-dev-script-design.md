# KOB 本地一键启停脚本设计

## 目标

提供单一入口 `scripts/dev.sh`，让开发者通过一条命令启动、检查、查看日志、重启或停止 KOB 的全部应用服务。

脚本管理 backend、matchingsystem、botrunningsystem 和 web。MySQL 作为共享基础设施只负责按需启动和状态检查，`stop` 不关闭 MySQL。

## 命令接口

```bash
./scripts/dev.sh start
./scripts/dev.sh status
./scripts/dev.sh logs [backend|matching|bot|web]
./scripts/dev.sh stop
./scripts/dev.sh restart
```

- 无参数时打印用法并返回非零状态。
- 未知命令或未知日志服务名打印错误并返回非零状态。
- `start` 可重复执行；已由脚本启动且仍存活的服务不重复启动。
- `restart` 等价于依次执行应用服务的 `stop` 和 `start`，不重启 MySQL。

## 运行时目录

脚本在仓库根目录使用 `.runtime/`：

```text
.runtime/
├── pids/
│   ├── backend.pid
│   ├── matching.pid
│   ├── bot.pid
│   └── web.pid
└── logs/
    ├── backend.log
    ├── matching.log
    ├── bot.log
    └── web.log
```

`.runtime/` 加入 `.gitignore`，不得提交 PID 和运行日志。

## 环境约束

- macOS 和 zsh/bash 兼容的 POSIX 风格 Shell。
- JDK 8 默认路径：`/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home`。
- Maven 默认使用 IntelliJ IDEA 内置版本：`/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn`。
- Node.js 默认使用本机现有 Node 20：`$HOME/.nvm/versions/node/v20.20.2/bin`。
- MySQL 启动脚本：`/usr/local/mysql/support-files/mysql.server`。
- 以上路径允许分别通过 `JAVA_HOME`、`MAVEN_BIN`、`NODE_BIN_DIR`、`MYSQL_SERVER` 环境变量覆盖。
- 脚本不读取、不输出也不复制数据库密码；后端继续使用已忽略的 `application-local.properties`。

## 启动流程

1. 解析仓库根目录，确保从任意工作目录调用都使用正确路径。
2. 检查 Java、Maven、Node、npm、MySQL 启动脚本及各模块目录。
3. 检查 `3306`：若未监听，执行 `sudo "$MYSQL_SERVER" start`，等待端口就绪；失败则停止后续启动。
4. 按 backend、matching、bot、web 顺序后台启动应用。
5. Java 服务使用 JDK 8 执行 Maven `spring-boot:run`；web 使用 Node 20 的 npm 执行 `npm run serve`。
6. 每个服务启动后等待对应端口，默认最长 60 秒。
7. 若某个服务超时或进程提前退出，打印对应日志末尾，停止本轮已启动的 KOB 应用服务并返回非零状态。
8. 全部端口就绪后打印服务名、端口、PID、日志路径和前端访问地址。

## PID 与端口安全

- PID 文件只用于管理脚本自己启动的进程。
- 使用 `kill -0` 判断 PID 是否存活；陈旧 PID 文件会被自动删除。
- `stop` 先发送 `SIGTERM`，等待最多 10 秒，再对仍存活的已记录 PID 发送 `SIGKILL`。
- 启动前若目标端口已被占用但没有对应的有效 PID 文件，脚本报错退出，不接管、不终止未知进程。
- 停止后删除对应 PID 文件。
- MySQL 不写 PID 文件，也不在 `stop` 中关闭。

## 状态与日志

- `status` 同时报告 PID 状态和端口状态；两者一致才显示应用服务为运行中。
- MySQL 仅依据 `3306` 端口报告状态。
- `logs` 无服务参数时持续查看全部 4 个日志；指定服务时只持续查看对应日志。
- 尚无日志文件时给出明确提示，不创建空日志。

## 验证

1. 使用 Shell 语法检查验证脚本可解析。
2. 在全部服务停止时执行 `start`，确认 `3000`、`3001`、`3002`、`8080` 均监听。
3. 再次执行 `start`，确认 PID 不变且没有重复进程。
4. 执行 `status`，确认 4 个应用服务和 MySQL 状态准确。
5. 对前端执行 HTTP 请求，确认返回 `200`。
6. 执行 `stop`，确认 4 个应用端口关闭而 MySQL `3306` 保持监听。
7. 人为占用一个应用端口，确认脚本拒绝误杀未知进程。

## 非目标

- 不用于生产部署。
- 不安装 JDK、Maven、Node、npm 或 MySQL。
- 不启动浏览器。
- 不管理数据库 schema 或数据。
- 不修改现有 Maven、npm 或业务代码。
