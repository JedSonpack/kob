# KOB Agent Lab

**自然语言策略 -> Java Bot -> 沙箱编译 -> 每个最终验证版本 48 局公开评测 + 24 局隐藏验证 -> 失败分析 -> 最佳版本**

KOB Agent Lab 是在原 King Of Bots 在线贪吃蛇对战平台上的 Agent 化升级：用户用自然语言描述策略目标，Agent 在受控工具集内自主完成 Java Bot 生成、沙箱编译、批量对战、失败分析与策略改进，最多 3 轮可评测版本，最终交付最佳 Bot 与完整演进报告。系统采用「LLM 负责规划与生成，确定性引擎负责执行与评测」的混合架构——任何胜率、延迟或稳定性结论都来自工程侧计算，不由模型自评。

## 项目定位

- 面向 Java 后端 + AI Agent 交叉方向的工程演示。
- 复用既有实时对战、Bot 执行沙箱与游戏规则，形成可验证闭环，而非新增割裂的聊天功能。
- 不新增第 4 个独立微服务；编排保留在 `backend`，规则下沉到无 Spring 依赖的 `game-core`。

## 90 秒体验路径

```bash
./scripts/dev.sh start          # 拉起 MySQL + backend + matching + bot + web
# 浏览器打开 http://localhost:8080/agent-lab/ ，输入策略目标，选 3 轮，点「开始进化」
./scripts/dev.sh stop
```

演示脚本与时间轴见 [`docs/agent-lab/demo-script.md`](docs/agent-lab/demo-script.md)。

## 核心架构

```text
Web(/agent-lab/) -> backend(任务 API + AgentWorkflowService) -> LlmClient(Fake/OpenAI/Anthropic)
                                                       \-> botrunningsystem(可信评测协调器)
                                                            -> game-core(确定性规则引擎)
                                                            -> 持久化 Bot 沙箱子进程
```

- `game-core`：无 Spring 依赖的确定性规则内核，在线对战与离线评测共用。
- `botrunningsystem`：持久 Bot 子进程 + 批量评测协调器，候选代码不进入协调器 JVM。
- `backend`：Agent 状态机、工具路由、LLM Client、恢复机制与用户 API。
- `web`：`/agent-lab/` 工作台，独立 `agentApi.js` 与 namespaced Vuex 模块。

## Agent Workflow

状态机：`CREATED -> GENERATING -> COMPILING (-失败-> REPAIRING) -> EVALUATING -> ANALYZING (-继续-> IMPROVING -> COMPILING | -结束-> VALIDATING) -> COMPLETED`，任一执行中状态可进入 `FAILED`/`CANCELLED`。

- 最大 3 轮迭代，每轮最多 2 次编译尝试；失败尝试只出现在 Trace，不进展示版本。
- 模型只输出结构化动作（GENERATE_CODE/REPAIR_CODE/IMPROVE_CODE/FINISH）+ 源码 + 摘要，服务端校验动作是否符合当前状态。
- 隐藏集只在公开集迭代结束后运行，结果不进入模型上下文与运行中 Trace。

## 确定性评测

- 公开调试集 8 个固定种子，每图对战 3 个基准策略（Safe/Greedy/Territory）并交换出生位置，共 48 局。
- 隐藏验证集 4 个固定种子，共 24 局，仅终态可见聚合，不展示具体种子。
- 指标：编译成功率、合法移动率、综合胜率（平局 0.5）、平均回合、P95 延迟、失败类型占比。
- 合法移动率非 100% 或 P95 超过 `kob.agent.evaluation.max-p95-ms`（默认 100ms）的版本不进入隐藏验证。
- 最佳版本由隐藏集最高分决定；新版本相对 V1 隐藏集退化超过 5% 时保留 V1。

## 沙箱与安全边界

- 候选 Bot 在独立持久 JVM 子进程运行，只编译一次连续响应局面。
- SecurityManager 禁止网络访问、文件写删和进程执行，并限制读取 `application.properties` 与环境变量。
- 发生超时或越权时调用 `destroyForcibly`，并递归清理临时目录。
- 当前 Java 8 SecurityManager 仅适用于本地演示与受控环境；生产应迁移到容器、cgroup、seccomp 或独立执行节点。

## 并发、幂等与恢复

- 固定大小线程池（默认最大并发 2）；同一用户只允许一个运行中任务（`active_slot` 唯一索引）。
- 状态推进用 `WHERE id=? AND version=? AND status=?` 乐观锁，终态不可再推进。
- 每次工具调用先写带唯一幂等键的 `agent_step`，已成功 Step 不重复执行。
- 服务启动扫描非终态任务，从最后成功 Step 续跑；取消后终止评测子进程并阻止后续推进。

## 本地启动

```bash
./scripts/dev.sh start    # MySQL（需本机已装）+ 3 个 Java 服务 + web
./scripts/dev.sh status
./scripts/dev.sh logs backend
./scripts/dev.sh stop
```

依赖：JDK 1.8（`/Library/Java/JavaVirtualMachines/jdk-1.8.jdk`）、Maven、Node 20.19+、MySQL 8.0。运行前用 `node --version` 确认版本；本轮验证使用 v20.20.2。`scripts/dev.sh` 默认导出 `KOB_AGENT_LLM_PROVIDER=fake`，自动化与本地演示不调真实模型。

## 配置真实模型

```bash
export KOB_AGENT_LLM_PROVIDER=anthropic-messages
export KOB_AGENT_LLM_BASE_URL=https://gateway.example.com
export KOB_AGENT_LLM_PATH=/v1/messages
export KOB_AGENT_LLM_MODEL=<实际可用模型>
export KOB_AGENT_LLM_API_KEY=<本地密钥，不入库>
./scripts/dev.sh restart
```

API Key 只从环境变量读取，不写入任何 Markdown、日志或 Git 文件。实验协议见 [`docs/agent-lab/experiment-protocol.md`](docs/agent-lab/experiment-protocol.md)。

## 测试与验证

- 后端：`cd backendcloud && JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home mvn test`
- 前端：`cd web && npm test && npm run lint && npm run build`
- 开发脚本行为：`bash scripts/tests/dev_test.sh`
- 现有对战回归：`NODE_PATH=$(npm root -g) node scripts/tests/battle_playwright.js`
- Agent Lab 三轮闭环（Fake LLM）：`NODE_PATH=$(npm root -g) node scripts/tests/agent_lab_playwright.js`

测试数量与实验数据以实际命令输出与 `docs/agent-lab/experiments/` 原始记录为准。

当前工作区验证状态：

| 验证项 | 状态 | 证据或限制 |
| --- | --- | --- |
| 后端全量 Maven 测试 | 已通过 | 177 项测试，Reactor 5/5 成功 |
| 前端 test / lint / build | 已通过 | Node 20.20.2 下 37/37，lint 0 错误，生产构建成功 |
| 开发脚本行为 | 已通过 | 提升本地端口探测权限后 `scripts/dev.sh behavior tests` 通过 |
| 现有对战 Playwright | 已通过 | human-human、human-bot、bot-bot 3/3 场景通过 |
| Agent Lab Playwright | 已通过 | Fake LLM 任务 #17 与真实 Anthropic Messages 任务 #16 均完成 3 轮、Replay 与保存 Bot |

## 真实实验结果

真实模型实验记录存放于 [`docs/agent-lab/experiments/`](docs/agent-lab/experiments/)，命名 `YYYYMMDD-HHmm-模型名.md`。结论判定（至少 3 次中有至少 2 次同方向改善才称「可重复提升」）与表格列见实验协议。

2026-07-18 使用同一固定目标完成 5 次受控真实运行。#12 因响应预算未产生工具调用，#13～#14 因模型忽略源码长度约束失败，#15 暴露服务端迭代上限缺陷；这些失败均保留并推动协议与状态机修复。最终任务 #16 在 111 秒内完成 3 个版本，每版 48 局公开评测和 24 局隐藏验证，0 非法移动，完成 Replay 并保存最佳版本 V3。

三个版本的隐藏集得分均为 0，没有相对 V1 的改善；5 次运行也只有 1 次完整成功。因此当前只声明「真实 Anthropic Messages 闭环已走通」，不声明「可重复提升」或胜率提升。原始记录及逐版 Token、P95、失败码见 [`docs/agent-lab/experiments/`](docs/agent-lab/experiments/)。

## 项目取舍

- 不实现 RAG、向量库、多 Agent、Kubernetes、公网部署与多租户。
- 不一次性升级 Java/Spring Boot/Vue。
- 当前沙箱非生产级容器安全边界。
- 真人、Bot 对战、录像与排行榜行为保持不变。

## 面试讲法

- Java 多模块系统边界（game-core 共享库 + 3 个 Spring Boot 应用）。
- 长任务状态机、乐观锁、幂等与失败恢复。
- Agent Workflow、结构化输出与受控 Tool Calling。
- 用户代码沙箱、超时与资源治理。
- 确定性评测、隐藏验证集与回归测试。
- Agent Trace、Token、延迟与版本指标可观测性。

## 目录结构

```text
backendcloud/
  game-core/            确定性规则内核（无 Spring 依赖）
  backend/              账户、Bot、对战、排行榜 + agent 包（状态机/工具/LLM/恢复/API）
  botrunningsystem/     Bot 执行 + 持久沙箱 + 批量评测协调器
  matchingsystem/       匹配
  database/schema.sql   user/bot/record + 4 张 Agent 表
web/src/
  views/agent/          Agent Lab 工作台
  components/agent/     表单/时间线/版本表/Trace/Replay
  store/agent.js        namespaced 任务轮询与详情状态
  assets/scripts/       agentApi.js + agentViewModel.js（纯函数）
scripts/
  dev.sh                一键启停
  tests/                battle_playwright.js + agent_lab_playwright.js + dev_test.sh
docs/agent-lab/         演示脚本、实验协议、实验记录
```

进一步阅读：[Agent Lab 设计规格](docs/superpowers/specs/2026-07-16-kob-agent-lab-design.md)、[旧项目架构审计与重构路线图](docs/architecture/legacy-project-audit.md)、[重构进度看板](docs/architecture/refactor-progress.md) 和 [密钥轮换操作手册](docs/architecture/secret-rotation-runbook.md)。
