# KOB Agent Lab 阶段 3：Agent Workflow 与恢复机制实施计划

> **面向智能体工作者：** 必需子技能：使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 逐项实施此计划。步骤使用复选框（`- [ ]`）语法跟踪。

**目标：** 在现有 `backend` 中实现持久化 Agent 任务状态机、受控工具路由、Fake/真实 LLM Client、隐藏验证、取消和重启恢复，跑通最多 3 轮的自主进化闭环。

**架构：** Controller 只处理鉴权与 DTO；`AgentTaskService` 负责用户命令；`AgentWorkflowServiceImpl` 在有界线程池中推进状态；`AgentTaskRepository` 使用版本号和期望状态执行 CAS；`AgentToolRouter` 以持久 Step 和确定性 requestId 调用 `botrunningsystem`。LLM 只返回结构化 `LlmDecision`，服务端状态机决定下一阶段。

**技术栈：** Java 8、Spring Boot 2.4.5、MyBatis-Plus 3.5.1、MySQL 8.0、Fastjson 1.2.78、RestTemplate、JUnit 5、Mockito

## 全局约束

- 不新增微服务、消息队列或数据库依赖。
- Agent 执行线程池最大并发为 2，队列容量为 20。
- 同一用户最多一个 `active_slot = 1` 的运行任务。
- 每次状态推进都必须包含 `id`、`version` 和期望 `status` 条件。
- 每个工具调用必须先创建唯一 `idempotency_key` 的 `agent_step`。
- 最大迭代为 3，每轮最多 2 个编译尝试。
- 编译失败的第 2 次尝试不会创建下一轮，任务直接进入 `FAILED`。
- 公开集结果可以进入模型上下文；隐藏集结果、种子和通过状态不得进入模型上下文。
- 隐藏验证只在公开迭代结束后执行。
- 自动测试默认使用 Fake 或 Scripted LLM，不调用真实模型。
- LLM API Key 只从环境变量读取，日志与 Trace 不保存 Authorization Header 或模型原始响应。
- 终态为 `COMPLETED`、`FAILED`、`CANCELLED`，终态不可再次推进。
- 状态冲突时重新读取任务，不重复执行已成功工具。
- 执行本计划 Maven 命令前先运行：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home
export PATH="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin:$PATH"
```

---

## 文件结构

### 创建

```text
backendcloud/backend/src/main/java/com/kob/backend/agent/
├── controller/
│   ├── AgentTaskController.java
│   ├── AgentVersionController.java
│   └── AgentEvaluationController.java
├── dto/
│   ├── CreateAgentTaskRequest.java
│   ├── AgentTaskListItemDto.java
│   ├── AgentTaskDetailDto.java
│   ├── AgentVersionDetailDto.java
│   └── SaveAgentVersionRequest.java
├── llm/
│   ├── LlmClient.java
│   ├── LlmContext.java
│   ├── LlmDecision.java
│   ├── LlmDecisionValidator.java
│   ├── LlmStepExecutor.java
│   ├── FakeLlmClient.java
│   └── OpenAiCompatibleLlmClient.java
├── mapper/
│   ├── AgentTaskMapper.java
│   ├── BotVersionMapper.java
│   ├── AgentStepMapper.java
│   └── EvaluationRunMapper.java
├── model/
│   ├── AgentTask.java
│   ├── BotVersion.java
│   ├── AgentStep.java
│   ├── EvaluationRun.java
│   ├── AgentTaskStatus.java
│   ├── AgentAction.java
│   ├── CompileStatus.java
│   ├── DatasetType.java
│   ├── StepStatus.java
│   └── AgentErrorCode.java
├── repository/
│   ├── AgentTaskRepository.java
│   ├── BotVersionRepository.java
│   ├── AgentStepRepository.java
│   └── EvaluationRunRepository.java
├── service/
│   ├── AgentTaskService.java
│   ├── AgentTaskQueryService.java
│   ├── BotVersionService.java
│   └── impl/
├── tool/
│   ├── EvaluationClient.java
│   ├── AgentToolRouter.java
│   ├── CompileToolResult.java
│   └── EvaluationAggregate.java
└── workflow/
    ├── AgentWorkflowService.java
    ├── AgentWorkflowServiceImpl.java
    ├── AgentWorkflowExecutor.java
    ├── AgentWorkflowRecovery.java
    └── BestVersionSelector.java
```

### 其他创建文件

- `backendcloud/backend/src/main/java/com/kob/backend/config/AgentHttpClientConfig.java`

### 修改

- `backendcloud/database/schema.sql`
- `backendcloud/backend/src/main/resources/application.properties`
- `backendcloud/backend/src/main/resources/application-local.example.properties`

---

### 任务 1：创建 4 张表、实体和 Mapper

**文件：**
- 修改：`backendcloud/database/schema.sql`
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/agent/model/*.java`
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/agent/mapper/*.java`
- 测试：`backendcloud/backend/src/test/java/com/kob/backend/agent/model/AgentModelContractTest.java`

**接口：**
- 产出：4 个 MyBatis-Plus 实体和 BaseMapper。

- [ ] **步骤 1：编写失败的模型契约测试**

```java
package com.kob.backend.agent.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentModelContractTest {
    @Test
    void exposesTerminalAndRunningStates() {
        assertTrue(AgentTaskStatus.COMPLETED.isTerminal());
        assertTrue(AgentTaskStatus.FAILED.isTerminal());
        assertTrue(AgentTaskStatus.CANCELLED.isTerminal());
        assertFalse(AgentTaskStatus.EVALUATING.isTerminal());
    }

    @Test
    void limitsActionsByPhase() {
        assertTrue(AgentAction.GENERATE_CODE.isAllowedIn(AgentTaskStatus.GENERATING));
        assertTrue(AgentAction.REPAIR_CODE.isAllowedIn(AgentTaskStatus.REPAIRING));
        assertTrue(AgentAction.IMPROVE_CODE.isAllowedIn(AgentTaskStatus.ANALYZING));
        assertTrue(AgentAction.FINISH.isAllowedIn(AgentTaskStatus.ANALYZING));
        assertFalse(AgentAction.IMPROVE_CODE.isAllowedIn(AgentTaskStatus.GENERATING));
        assertFalse(AgentAction.IMPROVE_CODE.isAllowedIn(AgentTaskStatus.IMPROVING));
    }
}
```

- [ ] **步骤 2：确认测试失败**

运行：

```bash
cd backendcloud
mvn -pl backend -Dtest=AgentModelContractTest test
```

预期：缺少 Agent model。

- [ ] **步骤 3：追加数据库结构**

在现有 3 张业务表后追加：

```sql
CREATE TABLE IF NOT EXISTS `agent_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL,
  `goal` VARCHAR(1000) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `current_iteration` TINYINT NOT NULL DEFAULT 0,
  `max_iterations` TINYINT NOT NULL,
  `best_version_id` BIGINT NULL,
  `active_slot` TINYINT NULL,
  `version` INT NOT NULL DEFAULT 0,
  `error_code` VARCHAR(64) NULL,
  `error_message` VARCHAR(1000) NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_task_user_active` (`user_id`, `active_slot`),
  KEY `idx_agent_task_status` (`status`),
  CONSTRAINT `fk_agent_task_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
    ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `bot_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT NOT NULL,
  `iteration` TINYINT NOT NULL,
  `attempt` TINYINT NOT NULL,
  `parent_version_id` BIGINT NULL,
  `source_code` MEDIUMTEXT NOT NULL,
  `strategy_summary` VARCHAR(1000) NOT NULL,
  `change_reason` VARCHAR(1000) NULL,
  `compile_status` VARCHAR(32) NOT NULL,
  `compile_error` TEXT NULL,
  `accepted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bot_version_attempt` (`task_id`, `iteration`, `attempt`),
  KEY `idx_bot_version_parent` (`parent_version_id`),
  CONSTRAINT `fk_bot_version_task`
    FOREIGN KEY (`task_id`) REFERENCES `agent_task` (`id`)
    ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `agent_step` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT NOT NULL,
  `sequence` INT NOT NULL,
  `phase` VARCHAR(32) NOT NULL,
  `tool_name` VARCHAR(64) NOT NULL,
  `idempotency_key` VARCHAR(160) NOT NULL,
  `input_summary` TEXT NULL,
  `output_summary` TEXT NULL,
  `status` VARCHAR(32) NOT NULL,
  `duration_ms` BIGINT NULL,
  `prompt_tokens` INT NULL,
  `completion_tokens` INT NULL,
  `error_code` VARCHAR(64) NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_step_idempotency` (`idempotency_key`),
  UNIQUE KEY `uk_agent_step_sequence` (`task_id`, `sequence`),
  CONSTRAINT `fk_agent_step_task`
    FOREIGN KEY (`task_id`) REFERENCES `agent_task` (`id`)
    ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `evaluation_run` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `version_id` BIGINT NOT NULL,
  `dataset_type` VARCHAR(16) NOT NULL,
  `opponent_key` VARCHAR(32) NOT NULL,
  `map_seed` BIGINT NOT NULL,
  `side` CHAR(1) NOT NULL,
  `result` VARCHAR(16) NOT NULL,
  `rounds` INT NOT NULL,
  `decision_p95_ms` BIGINT NOT NULL,
  `invalid_move_count` INT NOT NULL,
  `failure_reason` VARCHAR(64) NULL,
  `replay` MEDIUMTEXT NOT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_evaluation_match`
    (`version_id`, `dataset_type`, `opponent_key`, `map_seed`, `side`),
  KEY `idx_evaluation_version_dataset` (`version_id`, `dataset_type`),
  CONSTRAINT `fk_evaluation_version`
    FOREIGN KEY (`version_id`) REFERENCES `bot_version` (`id`)
    ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

- [ ] **步骤 4：实现实体和枚举**

实体使用 Lombok `@Data`、`@NoArgsConstructor`、`@AllArgsConstructor`，ID 使用：

```java
@TableId(type = IdType.AUTO)
private Long id;
```

日期字段统一为 `Date createdAt` 和 `Date updatedAt`，依赖 MyBatis-Plus 驼峰映射。枚举写入数据库时使用 `name()`。

`AgentTaskStatus` 固定为：

```java
CREATED, GENERATING, COMPILING, REPAIRING, EVALUATING,
ANALYZING, IMPROVING, VALIDATING, COMPLETED, FAILED, CANCELLED
```

- [ ] **步骤 5：运行模型测试**

```bash
cd backendcloud
mvn -pl backend -Dtest=AgentModelContractTest test
```

预期：通过。

- [ ] **步骤 6：提交**

```bash
git add backendcloud/database/schema.sql \
  backendcloud/backend/src/main/java/com/kob/backend/agent/model \
  backendcloud/backend/src/main/java/com/kob/backend/agent/mapper \
  backendcloud/backend/src/test/java/com/kob/backend/agent/model
git commit -m "feat(Agent 数据): 持久化任务版本 Trace 与评测"
```

### 任务 2：实现状态机 CAS 和版本不变量

**文件：**
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/agent/repository/AgentTaskRepository.java`
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/agent/repository/BotVersionRepository.java`
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/agent/repository/AgentStepRepository.java`
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/agent/repository/EvaluationRunRepository.java`
- 修改：`backendcloud/backend/src/main/java/com/kob/backend/agent/mapper/AgentTaskMapper.java`
- 测试：`backendcloud/backend/src/test/java/com/kob/backend/agent/repository/AgentTaskRepositoryTest.java`
- 测试：`backendcloud/backend/src/test/java/com/kob/backend/agent/repository/BotVersionRepositoryTest.java`

**接口：**
- 产出：

```java
boolean AgentTaskRepository.transition(
    AgentTask task,
    AgentTaskStatus target,
    Integer currentIteration,
    Long bestVersionId,
    AgentErrorCode errorCode,
    String errorMessage,
    boolean clearActiveSlot
);
```

- [ ] **步骤 1：编写失败测试**

使用 Mockito 断言：

```java
when(mapper.compareAndSetStatus(
        12L, 3, "COMPILING", "EVALUATING",
        1, null, null, null, false
)).thenReturn(1);

assertTrue(repository.transition(
        task, AgentTaskStatus.EVALUATING, 1,
        null, null, null, false
));
```

当 Mapper 返回 `0` 时 Repository 返回 `false`，不得修改传入实体。

版本仓库必须拒绝：

- iteration 不在 `1..3`。
- attempt 不在 `1..2`。
- `attempt=2` 但同轮没有 attempt 1。
- `iteration>1` 但没有已完成公开评测的父版本。

- [ ] **步骤 2：确认测试失败**

```bash
cd backendcloud
mvn -pl backend \
  -Dtest=AgentTaskRepositoryTest,BotVersionRepositoryTest \
  test
```

预期：缺少 Repository 或自定义 Mapper 方法。

- [ ] **步骤 3：实现 CAS Mapper**

`AgentTaskMapper.compareAndSetStatus` 使用注解动态 SQL：

```java
@Update({
    "<script>",
    "UPDATE agent_task",
    "SET status = #{targetStatus}, version = version + 1, updated_at = NOW()",
    "<if test='currentIteration != null'>, current_iteration = #{currentIteration}</if>",
    "<if test='bestVersionId != null'>, best_version_id = #{bestVersionId}</if>",
    "<if test='errorCode != null'>, error_code = #{errorCode}</if>",
    "<if test='errorMessage != null'>, error_message = #{errorMessage}</if>",
    "<if test='clearActiveSlot'>, active_slot = NULL</if>",
    "WHERE id = #{id} AND version = #{version} AND status = #{expectedStatus}",
    "</script>"
})
int compareAndSetStatus(...);
```

所有字符串错误摘要写入前先截断为 1000 个字符。

- [ ] **步骤 4：实现 Repository**

- `findOwnedTask(taskId,userId)` 必须同时按 ID 和 userId 查询。
- `findIncompleteTasks()` 只返回非终态。
- `AgentStepRepository.nextSequence(taskId)` 读取最大 sequence 后加 1；插入冲突时重新查询已有 Step。
- `EvaluationRunRepository.saveIfAbsent` 依赖数据库唯一键，重复时读取现有记录。

- [ ] **步骤 5：运行仓库测试**

运行同上。

预期：CAS、迭代、尝试和父版本不变量全部通过。

- [ ] **步骤 6：提交**

```bash
git add backendcloud/backend/src/main/java/com/kob/backend/agent/mapper \
  backendcloud/backend/src/main/java/com/kob/backend/agent/repository \
  backendcloud/backend/src/test/java/com/kob/backend/agent/repository
git commit -m "feat(Agent 状态): 用乐观锁保护任务单向推进"
```

### 任务 3：实现幂等工具路由和内部评测 Client

**文件：**
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/agent/tool/EvaluationClient.java`
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/agent/tool/AgentToolRouter.java`
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/agent/tool/CompileToolResult.java`
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/agent/tool/EvaluationAggregate.java`
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/config/AgentHttpClientConfig.java`
- 测试：`backendcloud/backend/src/test/java/com/kob/backend/agent/tool/AgentToolRouterTest.java`

**接口：**
- 产出：

```java
CompileToolResult compile(AgentTask task, BotVersion version);
EvaluationAggregate evaluate(AgentTask task, BotVersion version, DatasetType dataset);
void cancel(Long taskId, Long versionId, DatasetType dataset);
```

- [ ] **步骤 1：编写失败测试**

测试以下行为：

```java
CompileToolResult first = router.compile(task, version);
CompileToolResult second = router.compile(task, version);
verify(client, times(1)).evaluate(any());
assertEquals(first, second);
```

已有成功 Step 时不得再次调用 Client；已有 `evaluation_run` 时重新聚合。`COMPILING` 状态调用公开评测或 `EVALUATING` 状态调用隐藏评测必须抛 `IllegalStateException`。

- [ ] **步骤 2：确认测试失败**

```bash
cd backendcloud
mvn -pl backend -Dtest=AgentToolRouterTest test
```

预期：缺少工具路由。

- [ ] **步骤 3：实现内部 Client**

配置：

```properties
kob.agent.evaluation.base-url=http://127.0.0.1:3002
kob.agent.evaluation.read-timeout-ms=130000
kob.agent.evaluation.max-p95-ms=100
```

`AgentHttpClientConfig` 创建独立 Bean，禁止修改现有通用 `RestTemplate` 的 3 秒读取超时：

```java
@Bean("agentEvaluationRestTemplate")
public RestTemplate agentEvaluationRestTemplate() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(agentEvaluationReadTimeoutMs);
    return new RestTemplate(factory);
}
```

`EvaluationClient` 使用 `@Qualifier("agentEvaluationRestTemplate")` 注入。

requestId 固定生成：

```java
String requestId = taskId + ":" + versionId + ":" + mode.name();
```

`COMPILE_ONLY`、`PUBLIC`、`HIDDEN` 都调用：

```text
POST {baseUrl}/bot/evaluate/
```

取消调用：

```text
POST {baseUrl}/bot/evaluate/{requestId}/cancel/
```

不得把隐藏种子写入请求。

- [ ] **步骤 4：实现 Step 幂等**

idempotencyKey 固定为：

```java
"task:" + taskId + ":version:" + versionId + ":tool:" + toolName
```

工具执行前插入 `RUNNING` Step；成功后写 `SUCCESS`、耗时和脱敏摘要；失败写 `FAILED` 和标准错误码。

重复成功：

- 编译从 `bot_version.compile_status` 读取。
- 评测从 `evaluation_run` 聚合。

- [ ] **步骤 5：运行工具测试**

```bash
cd backendcloud
mvn -pl backend -Dtest=AgentToolRouterTest test
```

预期：幂等、状态越权、错误映射和取消测试通过。

- [ ] **步骤 6：提交**

```bash
git add backendcloud/backend/src/main/java/com/kob/backend/agent/tool \
  backendcloud/backend/src/main/java/com/kob/backend/config/AgentHttpClientConfig.java \
  backendcloud/backend/src/test/java/com/kob/backend/agent/tool \
  backendcloud/backend/src/main/resources/application.properties
git commit -m "feat(Agent 工具): 以持久 Step 约束编译和评测调用"
```

### 任务 4：实现结构化 LLM 契约和 Fake Client

**文件：**
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/agent/llm/LlmClient.java`
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/agent/llm/LlmContext.java`
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/agent/llm/LlmDecision.java`
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/agent/llm/LlmDecisionValidator.java`
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/agent/llm/LlmStepExecutor.java`
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/agent/llm/FakeLlmClient.java`
- 测试：`backendcloud/backend/src/test/java/com/kob/backend/agent/llm/LlmDecisionValidatorTest.java`
- 测试：`backendcloud/backend/src/test/java/com/kob/backend/agent/llm/LlmStepExecutorTest.java`
- 测试：`backendcloud/backend/src/test/java/com/kob/backend/agent/llm/FakeLlmClientTest.java`

**接口：**
- 产出：`LlmDecision LlmClient.decide(LlmContext context)`。

- [ ] **步骤 1：编写失败测试**

```java
assertThrows(IllegalArgumentException.class, () ->
    validator.validate(
        AgentTaskStatus.GENERATING,
        new LlmDecision(AgentAction.IMPROVE_CODE, "s", "r", SOURCE, 1, 1)
    )
);
```

还必须拒绝空源码、超过 10000 字符源码、错误 action、缺少策略摘要。`FINISH` 允许 `sourceCode` 为空。

Fake Client 测试连续给出：

```text
GENERATING -> GENERATE_CODE
REPAIRING -> REPAIR_CODE
ANALYZING 且未达到 maxIterations -> IMPROVE_CODE
ANALYZING 且达到 maxIterations -> FINISH
```

- [ ] **步骤 2：确认测试失败**

```bash
cd backendcloud
mvn -pl backend \
  -Dtest=LlmDecisionValidatorTest,LlmStepExecutorTest,FakeLlmClientTest \
  test
```

预期：缺少 LLM 类型。

- [ ] **步骤 3：实现上下文边界**

`LlmContext` 只包含：

```java
Long taskId
AgentTaskStatus status
String goal
int iteration
int maxIterations
String currentSourceCode
String compileError
EvaluationAggregate publicEvaluation
java.util.List<String> failureSummaries
String previousChangeSummary
```

不得包含 `DatasetType.HIDDEN`、隐藏评测、mapSeed、Authorization Header 或其他用户数据。

`failureSummaries` 由公开集失败记录按出现次数降序、`failureReason` 字典序排序后截取前 3 条；每条只包含失败类型、回合数和压缩方向序列，不包含完整比赛日志。

- [ ] **步骤 4：固定决策结构和幂等 Trace**

`LlmDecision` 字段固定为：

```java
AgentAction action
String strategySummary
String changeReason
String sourceCode
Integer promptTokens
Integer completionTokens
```

`LlmStepExecutor` 负责：

```text
1. 以 task:{taskId}:phase:{status}:iteration:{iteration}:llm 建立幂等键。
2. 已有 SUCCESS Step 时，从对应 bot_version 或 FINISH 摘要恢复决策。
3. 外部 LLM 返回后校验动作。
4. GENERATE/REPAIR/IMPROVE 的 bot_version 插入与 Step SUCCESS 在同一事务提交。
5. FINISH 把 action 和 changeReason 写入脱敏 output_summary。
6. 前端 Trace 只映射策略摘要和修改原因，不返回源码。
```

如果进程在外部响应后、数据库事务前崩溃，该调用可能重试；一旦 Step 与版本成功持久化，恢复过程不得再次调用模型。

- [ ] **步骤 5：实现 Fake Client**

使用 `@ConditionalOnProperty(name="kob.agent.llm.provider", havingValue="fake", matchIfMissing=true)`。

Fake Bot 源码必须是可编译的完整 `com.kob.test.Bot`，暴露：

```java
public Integer nextMove(String input)
```

V1 使用第一个安全方向；V2 使用 BFS 可达空间；V3 在 V2 基础上避免单出口区域。Fake Client 的返回完全由状态与 iteration 决定，不使用随机数。`IMPROVING` 是服务端已准备好新版本后的持久检查点，不调用 LLM。

- [ ] **步骤 6：运行 LLM 契约测试**

运行同上。

预期：通过。

- [ ] **步骤 7：提交**

```bash
git add backendcloud/backend/src/main/java/com/kob/backend/agent/llm \
  backendcloud/backend/src/test/java/com/kob/backend/agent/llm
git commit -m "feat(Agent 模型): 以结构化动作隔离模型与状态机"
```

### 任务 5：实现主 Workflow 和最佳版本选择

**文件：**
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/agent/workflow/AgentWorkflowService.java`
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/agent/workflow/AgentWorkflowServiceImpl.java`
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/agent/workflow/BestVersionSelector.java`
- 测试：`backendcloud/backend/src/test/java/com/kob/backend/agent/workflow/AgentWorkflowServiceImplTest.java`
- 测试：`backendcloud/backend/src/test/java/com/kob/backend/agent/workflow/BestVersionSelectorTest.java`

**接口：**
- 消费：Repository、`LlmStepExecutor`、`AgentToolRouter`。
- 产出：计划总入口声明的 `AgentWorkflowService`。

- [ ] **步骤 1：编写正常三轮失败测试**

使用内存 Fake Repository、Scripted LLM 和 Fake ToolRouter：

```java
workflow.runTask(taskId);

AgentTask completed = tasks.get(taskId);
assertEquals(AgentTaskStatus.COMPLETED, completed.getStatus());
assertEquals(3, versions.findByTask(taskId).stream()
        .filter(v -> v.getCompileStatus() == CompileStatus.SUCCESS)
        .count());
assertEquals(48 * 3, evaluations.countPublicMatches(taskId));
assertEquals(24 * 3, evaluations.countHiddenMatches(taskId));
```

断言 LLM 收到的所有上下文都不含 `"HIDDEN"` 和隐藏分数。

- [ ] **步骤 2：编写修复、提前结束和退化选择测试**

覆盖：

- V1 attempt 1 编译失败，attempt 2 成功，`current_iteration` 仍为 1。
- V1 公开结果达标后 LLM 返回 `FINISH`，只创建 1 个展示版本。
- V2/V3 隐藏得分比 V1 退化超过 5%，最终 `bestVersionId` 为 V1。
- V2 隐藏得分最高时选择 V2。
- 同分时比较公开得分，再比较 P95 延迟。
- 非法移动率不为 0 的版本不进入隐藏验证。

- [ ] **步骤 3：确认 Workflow 测试失败**

```bash
cd backendcloud
mvn -pl backend \
  -Dtest=AgentWorkflowServiceImplTest,BestVersionSelectorTest \
  test
```

预期：缺少 Workflow。

- [ ] **步骤 4：实现单步循环**

`runTask` 每次循环先重新读取任务，再按状态调用一个私有方法：

```java
switch (task.getStatus()) {
    case CREATED: moveToGenerating(task); break;
    case GENERATING: generateFirstVersion(task); break;
    case COMPILING: compileCurrentVersion(task); break;
    case REPAIRING: repairCurrentVersion(task); break;
    case EVALUATING: evaluateCurrentVersion(task); break;
    case ANALYZING: decideNextAction(task); break;
    case IMPROVING: movePreparedVersionToCompiling(task); break;
    case VALIDATING: validateCandidates(task); break;
    default: return;
}
```

CAS 返回 `false` 时不抛业务错误，重新进入循环读取最新状态。

`decideNextAction` 使用 `LlmStepExecutor`：

- `FINISH`：直接 CAS 到 `VALIDATING`。
- `IMPROVE_CODE`：基于公开集最佳版本创建下一轮 attempt 1，再 CAS 到 `IMPROVING`。
- `IMPROVING`：只验证新版本存在且父版本已完成公开评测，然后 CAS 到 `COMPILING`。
- `VALIDATING` 找不到合法移动率 100% 且 P95 不超过 100 ms 的候选时，进入 `FAILED`，错误码为 `INVALID_MOVE_RATE` 或 `EVALUATION_TIMEOUT`，不得选择不合格版本。

- [ ] **步骤 5：实现版本选择**

公开最佳分数：

```text
score = wins + draws * 0.5
```

隐藏选择顺序：

```text
隐藏 score 降序
公开 score 降序
decisionP95Ms 升序
versionId 升序
```

选择后：

- 把一个版本 `accepted = 1`，其余为 `0`。
- 更新 `agent_task.best_version_id`。
- 清空 `active_slot`。
- 状态进入 `COMPLETED`。

- [ ] **步骤 6：运行 Workflow 测试**

运行同上。

预期：三轮、修复、提前结束、隐藏隔离和退化保留测试通过。

- [ ] **步骤 7：提交**

```bash
git add backendcloud/backend/src/main/java/com/kob/backend/agent/workflow \
  backendcloud/backend/src/test/java/com/kob/backend/agent/workflow
git commit -m "feat(Agent 工作流): 闭环生成评测改进与隐藏选择"
```

### 任务 6：实现有界执行、取消和重启恢复

**文件：**
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/agent/workflow/AgentWorkflowExecutor.java`
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/agent/workflow/AgentWorkflowRecovery.java`
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/config/AgentExecutorConfig.java`
- 测试：`backendcloud/backend/src/test/java/com/kob/backend/agent/workflow/AgentWorkflowRecoveryTest.java`
- 测试：`backendcloud/backend/src/test/java/com/kob/backend/agent/workflow/AgentCancellationTest.java`

**接口：**
- 产出：`submit(taskId)`、`cancel(taskId,userId)`、应用启动恢复。

- [ ] **步骤 1：编写失败测试**

恢复测试：

```java
when(taskRepository.findIncompleteTasks()).thenReturn(Arrays.asList(task1, task2));
recovery.onApplicationReady();
verify(executor).submit(task1.getId());
verify(executor).submit(task2.getId());
```

取消测试断言：

```java
workflow.cancelTask(taskId, userId);
assertEquals(AgentTaskStatus.CANCELLED, taskRepository.findById(taskId).getStatus());
verify(toolRouter).cancel(taskId, versionId, DatasetType.PUBLIC);
verify(executor).cancel(taskId);
```

取消后再次调用 `runTask` 不得创建新版本或 Step。

- [ ] **步骤 2：确认测试失败**

```bash
cd backendcloud
mvn -pl backend \
  -Dtest=AgentWorkflowRecoveryTest,AgentCancellationTest \
  test
```

预期：缺少执行器和恢复组件。

- [ ] **步骤 3：实现线程池**

```java
@Bean("agentTaskExecutor")
public ThreadPoolTaskExecutor agentTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(20);
    executor.setThreadNamePrefix("kob-agent-");
    executor.initialize();
    return executor;
}
```

`AgentWorkflowExecutor` 保存 `taskId -> Future<?>`，同一任务重复 submit 返回现有 Future。

- [ ] **步骤 4：实现恢复与取消**

- `AgentWorkflowRecovery` 监听 `ApplicationReadyEvent`。
- 扫描非终态任务并提交。
- Workflow 根据最后成功 Step 和持久化版本重新执行当前状态；工具幂等层负责复用结果。
- 取消先以 CAS 进入 `CANCELLED` 并清空 active slot，再取消远端评测和本地 Future。
- `InterruptedException` 映射为 `TASK_CANCELLED`，不得改成 `FAILED`。

- [ ] **步骤 5：运行恢复测试**

运行同上。

预期：通过。

- [ ] **步骤 6：提交**

```bash
git add backendcloud/backend/src/main/java/com/kob/backend/config/AgentExecutorConfig.java \
  backendcloud/backend/src/main/java/com/kob/backend/agent/workflow \
  backendcloud/backend/src/test/java/com/kob/backend/agent/workflow
git commit -m "feat(Agent 恢复): 支持有界执行取消与断点续跑"
```

### 任务 7：实现真实 OpenAI-compatible Client

**文件：**
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/agent/llm/OpenAiCompatibleLlmClient.java`
- 修改：`backendcloud/backend/src/main/resources/application.properties`
- 修改：`backendcloud/backend/src/main/resources/application-local.example.properties`
- 测试：`backendcloud/backend/src/test/java/com/kob/backend/agent/llm/OpenAiCompatibleLlmClientTest.java`

**接口：**
- 消费：`LlmContext`。
- 产出：经过 `LlmDecisionValidator` 校验的 `LlmDecision`。

- [ ] **步骤 1：编写 HTTP 失败测试**

使用 `MockRestServiceServer` 返回：

```json
{
  "choices": [{
    "message": {
      "content": "{\"action\":\"FINISH\",\"strategySummary\":\"公开集已稳定\",\"changeReason\":\"达到迭代上限\",\"sourceCode\":null}"
    }
  }],
  "usage": {
    "prompt_tokens": 123,
    "completion_tokens": 45
  }
}
```

断言：

- 请求带 `Authorization: Bearer test-key`。
- model 来自配置。
- 解析出 `FINISH`。
- Token 写入返回元数据。
- Trace 不包含 key 和完整原始响应。

另写非法 JSON 重试 1 次、HTTP 超时最多重试 2 次测试。

- [ ] **步骤 2：确认测试失败**

```bash
cd backendcloud
mvn -pl backend -Dtest=OpenAiCompatibleLlmClientTest test
```

预期：缺少真实 Client。

- [ ] **步骤 3：实现配置**

```properties
kob.agent.llm.provider=fake
kob.agent.llm.base-url=${KOB_AGENT_LLM_BASE_URL:https://api.openai.com}
kob.agent.llm.path=${KOB_AGENT_LLM_PATH:/v1/chat/completions}
kob.agent.llm.model=${KOB_AGENT_LLM_MODEL:}
kob.agent.llm.api-key=${KOB_AGENT_LLM_API_KEY:}
kob.agent.llm.connect-timeout-ms=5000
kob.agent.llm.read-timeout-ms=60000
```

真实 Client 使用：

```java
@ConditionalOnProperty(
    name = "kob.agent.llm.provider",
    havingValue = "openai-compatible"
)
```

真实 provider 下 API Key 或 model 为空时启动必须失败，并给出不包含密钥的配置错误。

在 `AgentHttpClientConfig` 增加独立 Bean：

```java
@Bean("agentLlmRestTemplate")
public RestTemplate agentLlmRestTemplate() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(agentLlmConnectTimeoutMs);
    factory.setReadTimeout(agentLlmReadTimeoutMs);
    return new RestTemplate(factory);
}
```

真实 Client 使用 `@Qualifier("agentLlmRestTemplate")`，不得复用评测 Client 的 130 秒超时。

- [ ] **步骤 4：实现请求和校验**

System Prompt 必须明确：

```text
只返回 JSON 对象。
允许动作只有 GENERATE_CODE、REPAIR_CODE、IMPROVE_CODE、FINISH。
源码必须是完整 com.kob.test.Bot，并提供 public Integer nextMove(String input)。
不得返回 Markdown、Shell 命令、网络请求或文件操作。
```

请求失败采用 `200 ms`、`400 ms` 指数退避。非法结构只重试 1 次。最终异常映射为 `LLM_TIMEOUT` 或 `LLM_INVALID_RESPONSE`。

- [ ] **步骤 5：运行 Client 测试**

运行同上。

预期：正常、非法 JSON、超时、Token 和脱敏测试通过，不发真实网络请求。

- [ ] **步骤 6：提交**

```bash
git add backendcloud/backend/src/main/java/com/kob/backend/agent/llm/OpenAiCompatibleLlmClient.java \
  backendcloud/backend/src/main/java/com/kob/backend/config/AgentHttpClientConfig.java \
  backendcloud/backend/src/main/resources/application.properties \
  backendcloud/backend/src/main/resources/application-local.example.properties \
  backendcloud/backend/src/test/java/com/kob/backend/agent/llm/OpenAiCompatibleLlmClientTest.java
git commit -m "feat(Agent 模型): 接入可配置的结构化 LLM 服务"
```

### 任务 8：实现用户 API、查询 DTO 和保存为正式 Bot

**文件：**
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/agent/controller/*.java`
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/agent/dto/*.java`
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/agent/service/*.java`
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/agent/service/impl/*.java`
- 测试：`backendcloud/backend/src/test/java/com/kob/backend/agent/service/AgentTaskServiceImplTest.java`
- 测试：`backendcloud/backend/src/test/java/com/kob/backend/agent/controller/AgentTaskControllerTest.java`

**接口：**
- 产出：设计规格中的 7 个前端 API。

- [ ] **步骤 1：编写 Service 失败测试**

覆盖：

- goal 为空或超过 1000 字符拒绝。
- maxIterations 不在 `1..3` 拒绝。
- 同用户已有 active task 返回 `TASK_CONFLICT`。
- 查询、取消、版本详情和 Replay 都校验 `user_id`。
- 保存 Bot 只允许编译成功且属于已完成任务的版本。

- [ ] **步骤 2：编写 Controller 失败测试**

使用 MockMvc 验证：

```text
POST /api/agent/tasks/                    -> 200
GET  /api/agent/tasks/                    -> 200
GET  /api/agent/tasks/{taskId}/           -> 200 / 404
POST /api/agent/tasks/{taskId}/cancel/    -> 200 / 409
GET  /api/agent/versions/{versionId}/     -> 200 / 404
GET  /api/agent/evaluations/{runId}/replay/ -> 200 / 404
POST /api/agent/versions/{versionId}/save-bot/ -> 200 / 409
```

- [ ] **步骤 3：确认测试失败**

```bash
cd backendcloud
mvn -pl backend \
  -Dtest=AgentTaskServiceImplTest,AgentTaskControllerTest \
  test
```

预期：缺少 API。

- [ ] **步骤 4：实现创建与查询**

创建任务：

```text
1. 从 SecurityContext 获取当前 User。
2. 校验请求。
3. 插入 CREATED、currentIteration=0、activeSlot=1、version=0。
4. 提交 AgentWorkflowExecutor。
5. 返回 taskId 与 CREATED。
```

详情 DTO 包含任务、最多 3 个公开完成版本、Trace、公开聚合；只有任务终态才包含隐藏聚合。

- [ ] **步骤 5：实现 Replay 响应**

Replay Endpoint 根据 `evaluation_run.map_seed` 和固定 `GameConfig(13,14,20,seed,1000)` 调用 `DeterministicMapGenerator` 重建地图，并把 `evaluation_run.replay` 拆成双方方向字符串。返回 JSON 固定为：

```json
{
  "record": {
    "map": "001...",
    "aid": 0,
    "asx": 11,
    "asy": 1,
    "bid": 1,
    "bsx": 1,
    "bsy": 12,
    "asteps": "0123",
    "bsteps": "3210",
    "loser": "A"
  },
  "opponentKey": "territory",
  "side": "A",
  "failureReason": "COLLISION"
}
```

隐藏集 Replay 仅在任务终态返回；运行中请求返回 HTTP 409。

- [ ] **步骤 6：实现保存 Bot**

复用现有 `AddService.add(Map<String,String>)`：

```java
Map<String, String> data = new HashMap<>();
data.put("title", request.getTitle());
data.put("description", version.getStrategySummary());
data.put("content", version.getSourceCode());
return addService.add(data);
```

这样沿用 Bot 数量、标题、描述和源码长度规则，不复制业务逻辑。

- [ ] **步骤 7：运行 API 测试**

运行同上。

预期：鉴权、所有权、冲突、取消与保存测试通过。

- [ ] **步骤 8：提交**

```bash
git add backendcloud/backend/src/main/java/com/kob/backend/agent/controller \
  backendcloud/backend/src/main/java/com/kob/backend/agent/dto \
  backendcloud/backend/src/main/java/com/kob/backend/agent/service \
  backendcloud/backend/src/test/java/com/kob/backend/agent
git commit -m "feat(Agent API): 提供任务查询取消与 Bot 交付"
```

### 任务 9：阶段 Fake LLM 闭环验收

**文件：**
- 创建：`backendcloud/backend/src/test/java/com/kob/backend/agent/workflow/FakeAgentWorkflowAcceptanceTest.java`

**接口：**
- 产出：不依赖真实模型的完整 Workflow 验证证据。

- [ ] **步骤 1：编写验收测试**

测试使用：

- 真实 `AgentWorkflowServiceImpl`。
- `FakeLlmClient`。
- 内存 Repository。
- Fake `AgentToolRouter`，为 V1/V2/V3 返回固定公开和隐藏指标。

断言：

```java
assertEquals(AgentTaskStatus.COMPLETED, task.getStatus());
assertNotNull(task.getBestVersionId());
assertNull(task.getActiveSlot());
assertEquals(3, displayedVersions.size());
assertEquals(0, hiddenSignalsSeenByLlm.get());
assertEquals(1, acceptedVersions.size());
```

再运行取消、编译二次失败、公开评测超时和恢复 4 条路径。

- [ ] **步骤 2：运行验收和 backend 全测**

```bash
cd backendcloud
mvn -pl backend -Dtest=FakeAgentWorkflowAcceptanceTest test
mvn test
```

预期：验收测试和全部模块测试通过。

- [ ] **步骤 3：扫描隐藏数据泄漏**

```bash
rg -n "hiddenEvaluation|hiddenSeeds|mapSeed" \
  backendcloud/backend/src/main/java/com/kob/backend/agent/llm \
  backendcloud/backend/src/main/java/com/kob/backend/agent/dto
```

预期：LLM 包无隐藏字段；DTO 只在终态详情映射逻辑中读取隐藏聚合。

- [ ] **步骤 4：检查数据库脚本**

```bash
rg -n "CREATE TABLE IF NOT EXISTS `(agent_task|bot_version|agent_step|evaluation_run)`" \
  backendcloud/database/schema.sql
```

预期：4 张表各出现 1 次。

- [ ] **步骤 5：检查差异**

```bash
git diff --check
git status --short
```

预期：无空白错误，用户原有前端修改未被覆盖。

- [ ] **步骤 6：记录阶段结果**

最后一个阶段提交必须包含：

```text
Tested: Fake LLM 三轮闭环、修复、取消、恢复、隐藏隔离和 backendcloud 全量测试
Not-tested: 真实模型和浏览器工作台由阶段 4 验证
Directive: 状态推进必须继续使用 id+version+expectedStatus，禁止改为无条件 updateById
```
