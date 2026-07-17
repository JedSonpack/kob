# KOB Agent Lab Anthropic Messages 接入设计

> 状态：已批准
> 日期：2026-07-17
> 范围：第 4 阶段真实模型流程验证

## 1. 背景与结论

现有 `OpenAiCompatibleLlmClient` 调用 `/v1/chat/completions`，使用 Bearer Token，并从 `choices[0].message.content` 读取模型结果。用户提供的模型实际采用 Anthropic Messages 协议。

使用 `temp.txt` 中的本地配置完成脱敏探测后，`POST /v1/messages` 在约 2.1 秒内返回 HTTP 200。响应顶层 `type` 为 `message`，`content` 是内容块数组，当前同时包含 `thinking` 与 `text`。因此，真实任务此前超时的直接原因是协议选择错误，而不是密钥、额度或模型不可用。

本次先以最小改动打通真实模型流程，后续可以迁移到 Spring AI。

## 2. 目标与非目标

### 2.1 目标

- 新增独立的 Anthropic Messages 客户端，不破坏已有 Fake 和 OpenAI 兼容实现。
- 复用项目现有 `RestTemplate`、Fastjson、重试和错误码约定，不新增依赖。
- 支持顶层 `model`、`system`、`messages`、`tools` 与 `max_tokens` 请求字段。
- 正确处理 `thinking`、`text` 与 `tool_use` 内容块。
- 将 Anthropic Token 用量映射到现有 `LlmDecision`。
- 使用 `temp.txt` 的配置完成第 4 阶段真实模型闭环。

### 2.2 非目标

- 本次不引入 Anthropic 官方 Java SDK。
- 本次不引入或迁移 Spring AI。
- 不删除 `OpenAiCompatibleLlmClient`。
- 不重构 Agent 工作流、状态机或评测逻辑。
- 不把 `temp.txt` 或其中任何值写入代码、日志、测试和版本库。

## 3. 方案比较

### 3.1 方案 A：独立 `RestTemplate` 客户端（采用）

新增 `AnthropicMessagesLlmClient`，通过 `kob.agent.llm.provider=anthropic-messages` 启用。该实现直接构造 Messages API 请求，并复用现有基础设施。

优点：改动小、无新依赖、容易模拟 HTTP 契约，也为后续 Spring AI 迁移保留清晰边界。缺点：请求和响应对象需要手工维护。

### 3.2 方案 B：Anthropic 官方 Java SDK（暂不采用）

官方 SDK 支持 Java 8，并提供 `client.messages().create(params)`。但它会引入新的依赖和对象模型，对本次「先走通流程」没有必要。

### 3.3 方案 C：直接改造 OpenAI 兼容客户端（拒绝）

在同一个类中按配置切换 Header、请求体和响应结构，会把两种协议耦合在一起，增加回归风险，也不利于后续替换为 Spring AI。

## 4. 组件与配置

新增组件：

```text
LlmClient
├── FakeLlmClient
├── OpenAiCompatibleLlmClient
└── AnthropicMessagesLlmClient
```

配置约定：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `kob.agent.llm.provider` | `fake` | 使用 `anthropic-messages` 启用新客户端 |
| `kob.agent.llm.base-url` | 无变化 | 网关 Base URL |
| `kob.agent.llm.anthropic-path` | `/v1/messages` | Anthropic 路径；仍可由 `KOB_AGENT_LLM_PATH` 覆盖 |
| `kob.agent.llm.model` | 空 | 模型 ID，必须配置 |
| `kob.agent.llm.api-key` | 空 | API Key，必须配置 |
| `kob.agent.llm.max-tokens` | `4096` | 单次响应最大 Token 数 |
| `kob.agent.llm.anthropic-version` | `2023-06-01` | Messages API 版本 Header |

真实环境继续通过现有 `KOB_AGENT_LLM_*` 环境变量注入，不读取或提交 `temp.txt`。

## 5. 请求与响应契约

请求使用：

- `POST {baseUrl}/v1/messages`
- `Content-Type: application/json`
- `x-api-key`：使用运行环境中已配置的 API Key
- `anthropic-version: 2023-06-01`

`system` 必须是请求顶层字段，不能作为 `messages` 中的 `system` 角色。`messages` 只发送 `user` 与需要时的 `assistant` 内容。

响应解析规则：

1. 校验顶层 `content` 是非空数组。
2. 忽略 `thinking`，不得将思考内容写入 Trace 或错误信息。
3. 拼接所有 `text` 块，再按现有 Agent JSON 协议解析 `LlmDecision`。
4. 识别 `tool_use` 块；首版只允许 `submit_decision`，其 `input` 使用与文本决策相同的字段，未知工具映射为 `LLM_INVALID_RESPONSE`。
5. 将 `usage.input_tokens` 映射为 `promptTokens`，将 `usage.output_tokens` 映射为 `completionTokens`。
6. 不保存 API Key、原始响应或思考内容。

首轮为保持现有工作流行为，模型仍返回结构化决策 JSON。工具定义进入请求，但服务端状态机继续决定允许执行的动作，不能信任模型自行推进阶段。

## 6. 错误处理

- 网络、连接和读取超时继续映射为 `LLM_TIMEOUT`。
- HTTP 4xx、缺失 `content`、无 `text`/有效 `tool_use`、非法决策 JSON 或未知动作映射为 `LLM_INVALID_RESPONSE`。
- 沿用现有有限重试与退避策略，不新增无限等待。
- 错误消息只包含状态和脱敏摘要，不包含请求正文、响应正文、密钥或模型思考内容。

## 7. 测试与验收

先增加 `AnthropicMessagesLlmClientTest`，再编写实现。测试至少覆盖：

- URL、`x-api-key`、`anthropic-version` 与 Content-Type 正确。
- `system` 位于顶层，请求包含 `model`、`messages`、`tools` 与 `max_tokens`。
- 响应先有 `thinking`、后有 `text` 时仍能解析决策。
- 多个 `text` 块可以正确拼接。
- Token 字段映射正确。
- 缺失内容、非法 JSON、未知动作与网络异常按约定失败。
- Fake 和 OpenAI 兼容客户端的原有测试不回归。

最终验收顺序：

1. 运行新增客户端单元测试。
2. 运行 `backend` 全量测试。
3. 使用 Fake LLM 运行 Agent Lab E2E 回归。
4. 使用 `temp.txt` 映射出的环境变量运行一次真实模型任务。
5. 验证任务完成、版本生成、公开与隐藏评测、Trace、Token 统计和保存 Bot 闭环。

## 8. Spring AI 迁移边界

Agent 工作流只依赖 `LlmClient`，不依赖 Anthropic 请求对象。未来引入 Spring AI 时，以新的 `LlmClient` 实现替换协议适配层即可，`LlmContext`、`LlmDecision`、状态机和评测工具保持不变。此次不提前引入 Spring AI 抽象或兼容层。
