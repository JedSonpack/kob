# KOB Agent Lab Anthropic 接入与阶段 4 收尾实施计划

> **完成状态（2026-07-18）：** 任务 1～4 已完成。Anthropic Messages 客户端、真实实验超时覆盖、Fake 回归、5 次真实实验、交付文档和最终全量验收均已落地；真实任务 #16 与 Fake 任务 #17 完成三轮闭环。实验没有证明策略可重复提升，失败调用 Token 未落入失败 Step 与生产容器沙箱列为后续风险。

> 正文复选框保留为实施时的原始 TDD 模板，未逐项回填；完成结论以本段、Git 提交和总计划验收清单为准。

> **面向智能体工作者：** 必需子技能：使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 逐项实施此计划。步骤使用复选框（`- [ ]`）语法跟踪。

**目标：** 用现有 Java 8 技术栈接入 Anthropic Messages API，完成 3 次真实模型实验和阶段 4 最终验收。

**架构：** 新增独立的 `AnthropicMessagesLlmClient` 实现 `LlmClient`，保持 Agent 工作流不感知具体协议。客户端通过 Spring 条件装配选择，复用 `RestTemplate`、Fastjson、错误码和有限重试；真实实验继续由现有 Agent Lab E2E 驱动并按协议保存结果。

**技术栈：** Java 8、Spring Boot、RestTemplate、Fastjson、JUnit 5、MockRestServiceServer、Node.js Test Runner、Playwright、Maven。

## 全局约束

- 不新增 Maven 或 npm 依赖。
- 不修改 `LlmClient`、`LlmContext`、`LlmDecision` 的公共契约。
- 不删除 `FakeLlmClient` 或 `OpenAiCompatibleLlmClient`。
- API Key 只从运行环境读取，不进入 Git、Markdown、日志或 Trace。
- 忽略 Anthropic `thinking` 内容，不能记录或参与决策 JSON 解析。
- HTTP 408/429 与 5xx 只做有限重试；终态 4xx 不重试；线程中断立即停止调用。
- 异常消息和 cause 不得保留模型响应正文、思考内容或 API Key。
- 自动化回归使用 Fake LLM；只有受控真实实验使用 `anthropic-messages`。
- 真实实验保持目标、数据集、评测规模和最大迭代次数不变。
- 保留用户现有的 `backendcloud/botrunningsystem/input.txt` 删除，不修改无关工作区内容。

---

### 任务 1：实现 Anthropic Messages 协议适配

**文件：**
- 创建：`backendcloud/backend/src/test/java/com/kob/backend/agent/llm/AnthropicMessagesLlmClientTest.java`
- 创建：`backendcloud/backend/src/main/java/com/kob/backend/agent/llm/AnthropicMessagesLlmClient.java`
- 修改：`backendcloud/backend/src/main/resources/application.properties`

**接口：**
- 消费：`LlmClient.decide(LlmContext context)`、`LlmDecision`、具名 Bean `agentLlmRestTemplate`。
- 产出：条件装配值 `kob.agent.llm.provider=anthropic-messages`；构造器参数依次为 `RestTemplate`、Base URL、Anthropic Path、模型、API Key、最大 Token、Anthropic 版本。

- [ ] **步骤 1：编写请求与响应的失败测试**

测试构造最小客户端并要求请求满足 Anthropic 契约：

```java
private AnthropicMessagesLlmClient client(RestTemplate restTemplate) {
    return new AnthropicMessagesLlmClient(
            restTemplate, "https://gateway.example", "/v1/messages",
            "model-test", "test-key", 4096, "2023-06-01");
}

@Test
void sendsMessagesContractAndParsesTextAfterThinking() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
    server.expect(requestTo("https://gateway.example/v1/messages"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("x-api-key", "test-key"))
            .andExpect(header("anthropic-version", "2023-06-01"))
            .andExpect(jsonPath("$.model").value("model-test"))
            .andExpect(jsonPath("$.system").isString())
            .andExpect(jsonPath("$.messages[0].role").value("user"))
            .andExpect(jsonPath("$.max_tokens").value(4096))
            .andRespond(withSuccess(ANTHROPIC_FINISH_RESPONSE, MediaType.APPLICATION_JSON));

    LlmDecision decision = client(restTemplate).decide(context());

    assertEquals(AgentAction.FINISH, decision.getAction());
    assertEquals(Integer.valueOf(123), decision.getPromptTokens());
    assertEquals(Integer.valueOf(45), decision.getCompletionTokens());
    server.verify();
}
```

`ANTHROPIC_FINISH_RESPONSE` 必须先包含 `thinking` 块，再包含承载决策 JSON 的 `text` 块，并使用 `input_tokens`、`output_tokens`。

- [ ] **步骤 2：运行测试，确认按预期失败**

运行：

```bash
cd backendcloud
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home \
  mvn -pl backend -am -Dtest=AnthropicMessagesLlmClientTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：编译失败，提示 `AnthropicMessagesLlmClient` 不存在。

- [ ] **步骤 3：补齐解析和错误路径测试**

新增以下测试：

```java
@Test
void rejectsUnknownToolUse() {
    AgentLlmException error = assertThrows(AgentLlmException.class,
            () -> client(restTemplate).decide(context()));
    assertEquals(AgentErrorCode.LLM_INVALID_RESPONSE, error.getCode());
}

@Test
void failsFastWhenRequiredConfigurationMissing() {
    assertThrows(IllegalStateException.class,
            () -> new AnthropicMessagesLlmClient(
                    new RestTemplate(), "https://gateway.example", "/v1/messages",
                    "model-test", "", 4096, "2023-06-01"));
}
```

同一测试类还要用完整的 Mock 响应覆盖多 `text` 块拼接、首次非法决策后重试成功、连续 3 次网络失败、缺少文本和工具块。未知 `tool_use.name` 使用 `unknown_tool`，断言异常代码为 `LLM_INVALID_RESPONSE`。缺少 API Key、模型或非正数 `maxTokens` 必须在构造时失败。

- [ ] **步骤 4：编写最小客户端实现**

核心装配与请求：

```java
@Component
@ConditionalOnProperty(name = "kob.agent.llm.provider", havingValue = "anthropic-messages")
public class AnthropicMessagesLlmClient implements LlmClient {
    // 构造器校验配置并拼接 baseUrl + path

    @Override
    public LlmDecision decide(LlmContext context) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", anthropicVersion);
        return requestWithFiniteRetry(buildRequestBody(context), headers);
    }
}
```

请求体必须采用顶层 `system`：

```java
request.put("model", model);
request.put("system", SYSTEM_PROMPT);
request.put("messages", userMessages);
request.put("tools", allowedTools());
request.put("max_tokens", maxTokens);
```

解析时遍历全部 `content` 块，只拼接 `type=text` 的 `text`；`thinking` 直接忽略。首版只注册 `submit_decision` 工具，其输入字段为 `action`、`strategySummary`、`changeReason`、`sourceCode`。若响应存在该工具块，则从其 `input` 对象构造与文本 JSON 相同的决策对象；未知工具名必须拒绝。

- [ ] **步骤 5：增加配置并运行客户端测试**

在 `application.properties` 增加：

```properties
kob.agent.llm.anthropic-path=${KOB_AGENT_LLM_PATH:/v1/messages}
kob.agent.llm.max-tokens=${KOB_AGENT_LLM_MAX_TOKENS:4096}
kob.agent.llm.anthropic-version=${KOB_AGENT_LLM_ANTHROPIC_VERSION:2023-06-01}
```

Anthropic 使用独立路径属性，避免继承 `kob.agent.llm.path` 的 OpenAI 默认值；两种实现仍可由 `KOB_AGENT_LLM_PATH` 覆盖当前启用协议的路径。

重复运行步骤 2 命令。预期：全部测试通过。

- [ ] **步骤 6：运行 LLM 客户端与 backend 全量回归**

```bash
cd backendcloud
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home \
  mvn -pl backend -am test
```

预期：`game-core` 与 `backend` 全部测试通过，OpenAI 和 Fake 客户端无回归。

- [ ] **步骤 7：提交协议适配**

仅暂存任务 1 的 3 个文件，使用 Lore 提交，`Tested` 写入实际测试命令与结果。

---

### 任务 2：完成真实实验 E2E 超时覆盖

**文件：**
- 修改：`scripts/tests/agent_lab_playwright.js`
- 创建：`scripts/tests/agent_lab_playwright.unit.test.js`

**接口：**
- 消费：环境变量 `KOB_AGENT_E2E_TIMEOUT_MS`。
- 产出：`taskTimeoutMs(value)`；默认仍为 180 秒，显式合法值可以延长真实实验等待时间。

- [ ] **步骤 1：确认既有 RED/GREEN 证据与当前 diff**

现有测试已按 TDD 编写。重新运行：

```bash
node --test scripts/tests/agent_lab_playwright.unit.test.js \
  scripts/tests/agent_lab_playwright.security.test.js
```

预期：9 项测试全部通过。

- [ ] **步骤 2：检查默认门禁未放宽**

确认以下断言保持不变：

```javascript
assert.equal(taskTimeoutMs(undefined), 180_000);
assert.equal(taskTimeoutMs("179999"), 180_000);
assert.equal(taskTimeoutMs("600000"), 600_000);
```

真实实验设置 `KOB_AGENT_E2E_TIMEOUT_MS=900000`；Fake E2E 不设置该变量。

- [ ] **步骤 3：提交超时覆盖**

仅暂存上述 2 个脚本文件，Lore 的 `Directive` 明确「不得提高 Fake E2E 默认超时」。

---

### 任务 3：执行 Fake 回归与 3 次真实模型实验

**文件：**
- 创建：`docs/agent-lab/experiments/YYYYMMDD-HHmm-model.md`，至少 3 份；同一分钟重复时追加序号。
- 修改：必要时更新 `docs/agent-lab/experiment-protocol.md` 中过时的 provider 示例。

**接口：**
- 消费：`temp.txt` 中 `ANTHROPIC_BASE_URL`、`MODEL_ID`、`ANTHROPIC_API_KEY`，只在进程环境中映射。
- 产出：Fake E2E 回归证据、3 份不可删改的真实实验记录。

- [ ] **步骤 1：先运行 Fake LLM E2E**

```bash
./scripts/dev.sh start
node scripts/tests/agent_lab_playwright.js
./scripts/dev.sh stop
```

启动环境必须为 `KOB_AGENT_LLM_PROVIDER=fake`。预期：任务完成、3 个版本、必需 Trace 阶段、录像回放和保存 Bot 全部通过。

- [ ] **步骤 2：安全映射真实模型配置**

不 `source temp.txt`。逐行读取允许键后映射：

```text
ANTHROPIC_BASE_URL -> KOB_AGENT_LLM_BASE_URL
MODEL_ID           -> KOB_AGENT_LLM_MODEL
ANTHROPIC_API_KEY   -> KOB_AGENT_LLM_API_KEY
```

同时设置：

```text
KOB_AGENT_LLM_PROVIDER=anthropic-messages
KOB_AGENT_LLM_PATH=/v1/messages
KOB_AGENT_LLM_MAX_TOKENS=4096
KOB_AGENT_LLM_READ_TIMEOUT_MS=300000
KOB_AGENT_E2E_TIMEOUT_MS=900000
```

- [ ] **步骤 3：执行第 1 次真实实验并保存原始结果**

使用固定目标创建 3 轮任务。记录任务 ID、状态、总耗时、各版本公开/隐藏指标、Token、最终版本和失败码。不得记录模型原文、思考块或密钥。

- [ ] **步骤 4：按相同条件执行第 2、3 次实验**

每次实验使用新任务，不人工修改生成源码，不删除失败实验。某次失败时先记录失败事实；仅修复确定的系统缺陷后才允许补充新实验，原失败文件继续保留。

- [ ] **步骤 5：验证实验文件完整性与安全性**

```bash
rg -n "ANTHROPIC_API_KEY=|x-api-key:|Authorization: Bearer" \
  docs/agent-lab/experiments readme.md KOB-Agent-Lab-面试报告.md
```

预期：无匹配。每份实验包含固定条件、版本表、选择理由、失败信息和任务耗时。

---

### 任务 4：更新交付结论并完成阶段 4 验收

**文件：**
- 修改：`readme.md`
- 修改：`KOB-Agent-Lab-面试报告.md`
- 修改：`docs/agent-lab/demo-script.md`
- 修改：`docs/superpowers/plans/2026-07-16-kob-agent-lab-04-web-delivery.md`

**接口：**
- 消费：任务 3 的 3 份真实实验文件和全量测试结果。
- 产出：只引用实测数据的 README、面试叙事、演示脚本和阶段完成状态。

- [ ] **步骤 1：计算实验结论**

三次中至少两次隐藏集相对 V1 同方向改善，才写「可重复提升」。否则写「系统通过隐藏集和版本保留避免伪优化」。报告中使用中位数与波动范围，不使用单次最优冒充稳定结果。

- [ ] **步骤 2：更新文档中的真实 provider 示例**

示例统一为：

```bash
export KOB_AGENT_LLM_PROVIDER=anthropic-messages
export KOB_AGENT_LLM_PATH=/v1/messages
```

Base URL、模型和 API Key 继续使用占位说明，不写真实值。

- [ ] **步骤 3：运行最终全量验证**

```bash
cd backendcloud
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home mvn test

cd ../web
npm test
npm run lint
npm run build

cd ..
bash scripts/tests/dev_test.sh
node --test scripts/tests/agent_lab_playwright.unit.test.js \
  scripts/tests/agent_lab_playwright.security.test.js
```

随后启动 Fake 环境，依次运行现有对战 Playwright 与 Agent Lab Playwright。预期：全部退出码为 0。

- [ ] **步骤 4：运行安全和残留检查**

```bash
find "${TMPDIR:-/tmp}" -maxdepth 1 -name 'kob-evaluation-*' -print
pgrep -fl 'PersistentSandboxMain'
git diff --check
git status --short
```

预期：无评测临时目录、无沙箱残留进程、无空白错误；状态中仅存在已知用户改动或本计划交付文件。

- [ ] **步骤 5：提交阶段 4 最终材料**

仅暂存实验与本任务文档，Lore 提交的 `Tested` 必须列出实际通过项，`Not-tested` 保留公网部署和容器级生产沙箱，`Directive` 要求未来指标继续引用原始实验。
