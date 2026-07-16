# KOB Agent Lab 总实施计划

> **面向智能体工作者：** 必需子技能：使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 逐项实施此计划。步骤使用复选框（`- [ ]`）语法跟踪。

**目标：** 将已评审的 KOB Agent Lab 设计拆成 4 个可独立实现、验证和交接的阶段，使不同模型可以按顺序完成代码实现。

**架构：** 先抽取无 Spring 依赖的 `game-core`，再由 `botrunningsystem` 构建可信评测协调器和持久沙箱，随后在 `backend` 中实现可恢复的 Agent Workflow，最后交付 `/agent-lab/` 页面、端到端测试和面试材料。阶段之间只通过本计划声明的 Java 接口、HTTP DTO 和数据库契约连接。

**技术栈：** Java 8、Spring Boot 2.4.5、MyBatis-Plus 3.5.1、MySQL 8.0、Vue 3、Vuex 4、Vitest、Playwright、OpenAI-compatible HTTP API

## 全局约束

- 设计规格以 `docs/superpowers/specs/2026-07-16-kob-agent-lab-design.md` 为唯一产品与架构依据。
- 不新增第 4 个独立微服务。
- 不升级 Java、Spring Boot、Vue 或现有依赖版本。
- 不引入消息队列、向量数据库、RAG、多 Agent、Kubernetes 或分布式任务调度。
- Agent 生成代码不得进入 `backend` 主 JVM 执行。
- 自动化测试默认使用 `FakeLlmClient`，不得调用真实模型或消耗线上 Token。
- 公开集包含 8 个固定种子，隐藏集包含 4 个固定种子；每个种子对战 3 个基准策略并交换出生位置。
- 每个公开版本运行 48 局；每个合格版本在迭代结束后运行 24 局隐藏验证。
- 最大迭代轮数为 3，每轮最多 2 次编译尝试。
- 隐藏集只在公开集迭代结束后运行，任何隐藏集信号不得进入模型上下文或运行中 Trace。
- 合法移动率不是 100% 或 P95 延迟超限的版本不得进入隐藏验证。
- 首版 P95 合格阈值固定为 100 ms，并通过 `kob.agent.evaluation.max-p95-ms` 配置。
- 新版本相对 V1 的隐藏集得分退化超过 5% 时保留 V1。
- 同一用户只允许一个运行中任务；终态任务允许保留多个。
- Agent 任务执行线程池固定最大并发为 2。
- 当前工作区存在用户未提交的前端修改；实施者必须先运行 `git status --short`，不得覆盖或回退这些修改。
- 所有提交必须遵守仓库 `AGENTS.md` 中的 Lore Commit Protocol，并包含实际 `Tested:` 与 `Not-tested:`。

---

## 阶段与依赖

| 顺序 | 阶段计划 | 独立产物 | 开始条件 | 完成门禁 |
| --- | --- | --- | --- | --- |
| 1 | `2026-07-16-kob-agent-lab-01-game-core.md` | 确定性规则内核、基准策略、在线适配 | 设计规格已评审 | `game-core` 与 `backend` 测试通过，现有对战协议无回归 |
| 2 | `2026-07-16-kob-agent-lab-02-sandbox-evaluator.md` | 持久 Bot 子进程、批量公开/隐藏评测 API | 阶段 1 已合并 | `botrunningsystem` 测试通过，超时/取消后无残留进程 |
| 3 | `2026-07-16-kob-agent-lab-03-agent-workflow.md` | 数据表、状态机、Fake/真实 LLM Client、恢复机制 | 阶段 2 已合并 | Fake LLM 三轮闭环通过，隐藏集隔离与幂等测试通过 |
| 4 | `2026-07-16-kob-agent-lab-04-web-delivery.md` | Agent Lab 页面、E2E、README、实验报告 | 阶段 3 已合并 | 本地一键启动、前后端测试、Playwright 和 3 次真实实验完成 |

阶段必须串行合并。每个阶段内部可以把互不修改同一文件的测试、文档或只读审查交给不同模型，但写代码的主模型必须拥有该阶段的完整上下文。

## 文件所有权

### 阶段 1

- `backendcloud/game-core/**`
- `backendcloud/pom.xml`
- `backendcloud/backend/pom.xml`
- `backendcloud/backend/src/main/java/com/kob/backend/websocket/utils/Game.java`
- `backendcloud/backend/src/main/java/com/kob/backend/websocket/utils/GameRules.java`
- `backendcloud/backend/src/main/java/com/kob/backend/websocket/utils/Player.java`
- 对应 `game-core` 与 `backend` 测试

### 阶段 2

- `backendcloud/botrunningsystem/**`
- `backendcloud/botrunningsystem/pom.xml`
- 阶段 1 已稳定的 `game-core` 公共接口只能向后兼容扩展，不得随意改名。

### 阶段 3

- `backendcloud/database/schema.sql`
- `backendcloud/backend/src/main/java/com/kob/backend/agent/**`
- `backendcloud/backend/src/main/java/com/kob/backend/config/**`
- `backendcloud/backend/src/main/resources/application*.properties`
- `backendcloud/backend/src/test/java/com/kob/backend/agent/**`

### 阶段 4

- `web/src/views/agent/**`
- `web/src/components/agent/**`
- `web/src/store/agent.js`
- `web/src/assets/scripts/agentApi.js`
- `web/src/assets/scripts/agentViewModel.js`
- `web/src/router/index.js`
- `web/src/components/NavBar.vue`
- `scripts/tests/agent_lab_playwright.js`
- `scripts/dev.sh`
- `readme.md`
- `docs/agent-lab/**`

## 跨阶段固定接口

后续阶段可以扩展字段，但不得重命名以下接口：

```java
package com.kob.game.core;

public interface Strategy {
    int nextMove(GameSnapshot snapshot);
}

public interface GameEngine {
    GameResult play(GameConfig config, Strategy playerA, Strategy playerB);
}
```

```java
package com.kob.service.evaluation.dto;

public enum EvaluationMode {
    COMPILE_ONLY,
    PUBLIC,
    HIDDEN
}

public final class EvaluationRequest {
    private String requestId;
    private String sourceCode;
    private EvaluationMode mode;
}

public final class EvaluationResponse {
    private String requestId;
    private boolean compileSucceeded;
    private String compileError;
    private EvaluationSummary summary;
    private java.util.List<EvaluationMatchResult> matches;
}
```

```java
package com.kob.backend.agent.llm;

public interface LlmClient {
    LlmDecision decide(LlmContext context);
}
```

```java
package com.kob.backend.agent.workflow;

public interface AgentWorkflowService {
    void runTask(Long taskId);
    void resumeIncompleteTasks();
    void cancelTask(Long taskId, Integer userId);
}
```

前端只依赖以下 HTTP API：

```text
POST /api/agent/tasks/
GET  /api/agent/tasks/
GET  /api/agent/tasks/{taskId}/
POST /api/agent/tasks/{taskId}/cancel/
GET  /api/agent/versions/{versionId}/
GET  /api/agent/evaluations/{runId}/replay/
POST /api/agent/versions/{versionId}/save-bot/
```

## 每阶段执行协议

- [ ] **步骤 1：建立干净基线**

运行：

```bash
git status --short
git log -3 --oneline
```

预期：实施者记录现有用户修改；不得使用 `git reset --hard`、`git checkout --` 或删除未跟踪文件。

- [ ] **步骤 2：只读取当前阶段计划**

运行：

```bash
sed -n '1,260p' docs/superpowers/specs/2026-07-16-kob-agent-lab-design.md
sed -n '1,360p' docs/superpowers/plans/2026-07-16-kob-agent-lab-01-game-core.md
```

阶段 2～4 分别把第二条命令替换为对应计划文件。预期：实施者在开始编辑前复述当前阶段产物、禁止事项、消费接口和完成门禁。

- [ ] **步骤 3：按任务执行 RED–GREEN–REFACTOR**

每个任务必须依次完成：

```text
新增一个会失败的行为测试
运行精确测试并确认失败原因正确
实现使该测试通过的最小代码
运行当前模块全部测试
检查 git diff
独立提交
```

- [ ] **步骤 4：阶段审查**

主实现模型完成后，使用一个没有参与实现的模型审查：

```text
规格覆盖
状态与数据不变量
安全边界
回归风险
测试是否真正证明声明
```

审查模型只给出文件与行号级问题，不直接重写整个阶段。

- [ ] **步骤 5：阶段验收**

运行阶段计划列出的全部验证命令，并将命令、退出码、测试数量和未验证项写入最后一个提交的 Lore trailers。

## 模型分工建议

| 工作 | 建议模型 | 原因 |
| --- | --- | --- |
| 阶段 1：规则抽取与在线兼容 | `gpt-5.6-sol`，中等推理 | 涉及行为等价、确定性和跨模块接口 |
| 阶段 2：持久沙箱与评测器 | `gpt-5.6-sol`，中等推理 | 涉及进程协议、资源清理和安全边界 |
| 阶段 3：状态机与恢复 | `gpt-5.5`，高推理 | 任务多但接口已冻结，重点是并发与幂等 |
| 阶段 4：前端、测试与文档 | `gpt-5.5`，中等或高推理 | 主要是页面编排、API 接入和交付整理 |
| 阶段验收 | 与实现不同的模型 | 减少实现者自证偏差 |

## 发给其他模型的统一提示

```text
你正在实现 /Users/liheng/project/Java/kob 的 KOB Agent Lab。

先阅读：
1. /Users/liheng/project/Java/kob/AGENTS.md
2. /Users/liheng/project/Java/kob/docs/superpowers/specs/2026-07-16-kob-agent-lab-design.md
3. /Users/liheng/project/Java/kob/docs/superpowers/plans/2026-07-16-kob-agent-lab.md
4. 我指定的阶段计划文件

只实施该阶段，不提前修改后续阶段。严格按计划中的复选框逐项执行，
每个任务使用 TDD，先确认失败测试，再做最小实现。不得覆盖当前工作区中
用户已有的未提交修改；遇到同文件修改时先读取并保留现有内容。

每完成一个任务：
- 运行该任务精确测试；
- 运行当前 Maven 模块或前端完整测试；
- 检查 git diff；
- 使用中文 Conventional Commit + Lore trailers 提交。

结束前必须提供：
- 改动文件；
- 测试命令、退出码和测试数量；
- 已验证的不变量；
- 剩余风险和未验证项。
```

## 最终完成定义

- [ ] 4 份阶段计划的所有任务均已完成并独立提交。
- [ ] `backendcloud` 全量 Maven 测试通过。
- [ ] `web` 的 `npm test`、`npm run lint` 与 `npm run build` 通过。
- [ ] 现有 `scripts/tests/battle_playwright.js` 继续通过。
- [ ] 新增 `scripts/tests/agent_lab_playwright.js` 通过完整 Fake LLM 闭环。
- [ ] 取消、超时和失败路径均确认无残留子进程与临时目录。
- [ ] 运行中任务与模型上下文中不存在隐藏集数据。
- [ ] 本地一键启动可以拉起 MySQL、3 个 Java 服务和 Web 前端。
- [ ] 完成至少 3 次真实模型实验，原始结果保存到 `docs/agent-lab/experiments/`。
- [ ] README、90 秒演示视频脚本、架构图、面试问答和简历 bullet 使用真实测试与实验数据。
