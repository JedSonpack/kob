# KOB 本地一键启停脚本实施计划

> **面向智能体工作者：** 必需子技能：使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 逐项实施此计划。步骤使用复选框（`- [ ]`）语法跟踪。

**目标：** 实现 `scripts/dev.sh`，安全管理 MySQL 检查与 KOB 四个应用服务的启动、状态、日志、重启和停止。

**架构：** 一个 Bash 脚本保存脚本自身启动进程的 PID 和独立日志，端口只用于就绪及冲突检测。单元级 Shell 测试通过环境变量注入临时目录和假服务，真实集成验证使用仓库现有 JDK 8、Maven、Node 20 和 MySQL。

**技术栈：** Bash、macOS `lsof`、JDK 8、Maven、Node.js 20、npm、MySQL 8

## 全局约束

- `stop` 只能终止脚本记录的 KOB PID，不能根据端口杀未知进程。
- MySQL 未运行时由 `start` 使用 `sudo` 启动；`stop` 不关闭 MySQL。
- PID 和日志只写入已忽略的 `.runtime/`。
- 不读取或输出数据库密码。
- 不修改业务代码、Maven 配置或 npm 配置。

---

### 任务 1：命令与进程安全

**文件：**
- 创建：`scripts/dev.sh`
- 创建：`scripts/tests/dev_test.sh`
- 修改：`.gitignore`

**接口：**
- 产出：`scripts/dev.sh start|status|logs|stop|restart`。
- 测试注入：`RUNTIME_DIR`、`START_TIMEOUT`、`STOP_TIMEOUT`、`KOB_JAVA_HOME` 和规格中的运行时路径变量。

- [x] **步骤 1：编写失败的 Shell 行为测试**

测试至少覆盖：无参数失败、未知命令失败、未知日志服务失败、陈旧 PID 清理、未知端口占用时拒绝启动、`stop` 不杀未记录进程。

- [x] **步骤 2：运行测试并确认因脚本缺失而失败**

```bash
bash scripts/tests/dev_test.sh
```

预期：非零退出，提示 `scripts/dev.sh` 不存在。

- [x] **步骤 3：实现最小生命周期脚本**

实现规格中的路径覆盖、依赖检查、MySQL 启动、PID 安全、端口等待、失败回滚、状态和日志命令。后台进程使用独立进程组，确保 Maven/npm 的子进程可以随 `stop` 一并结束。

- [x] **步骤 4：忽略运行时文件**

在 `.gitignore` 加入：

```gitignore
.runtime/
```

- [x] **步骤 5：运行静态和行为测试**

```bash
bash -n scripts/dev.sh
bash -n scripts/tests/dev_test.sh
bash scripts/tests/dev_test.sh
```

预期：全部退出码为 `0`。

### 任务 2：真实服务集成验证

**文件：**
- 修改：`docs/superpowers/plans/2026-07-11-local-dev-script.md`（勾选结果）

**接口：**
- 消费：任务 1 的 `scripts/dev.sh`。
- 产出：四服务真实启停证据。

- [x] **步骤 1：执行首次启动**

```bash
./scripts/dev.sh start
```

预期：MySQL 和 `3000`、`3001`、`3002`、`8080` 均就绪，四个 PID 文件存在。

- [x] **步骤 2：验证幂等启动和状态**

保存 PID，第二次执行 `start`，再执行 `status`。预期 PID 不变，所有服务显示运行中。

- [x] **步骤 3：验证 HTTP 与日志**

```bash
curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/
```

预期：返回 `200`；四个日志文件非空。

- [x] **步骤 4：验证停止边界**

```bash
./scripts/dev.sh stop
./scripts/dev.sh status
```

预期：四个应用端口关闭、PID 文件删除，MySQL `3306` 仍监听。

- [x] **步骤 5：提交**

仅提交 `.gitignore`、`scripts/dev.sh`、`scripts/tests/dev_test.sh` 和计划文档，使用 Lore trailers 记录验证证据及剩余风险。
