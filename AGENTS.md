# 项目级开发约定

## Superpowers 与中文技能的组合方式

Superpowers 负责开发流程和质量门禁；中文技能负责国内团队的表达习惯、Git 协作和专项工作流。两者在对应阶段组合使用，中文技能不替代 Superpowers 的流程。

### 本地工作习惯

- 默认在当前工作目录和当前分支上开发，不自动创建 Git worktree。
- 只有用户明确要求，或当前工作区存在无法安全规避的冲突时，才使用 `using-git-worktrees`。
- 不得为了使用 worktree 而中断明确、低风险的当前分支任务。
- 项目级 `AGENTS.md` 和用户当前指令的优先级高于技能默认值。

### 技能映射

| 开发场景 | Superpowers 主流程 | 中文／本地技能 | 组合规则 |
| --- | --- | --- | --- |
| 需求、设计和计划 | `brainstorming` → `writing-plans` | `chinese-documentation` | 保留设计与计划流程，同时使用自然中文、中英文空格和统一标点。 |
| 功能开发 | `test-driven-development` | 按任务选择专项技能 | 保留 RED–GREEN–REFACTOR 和最小实现原则。 |
| MCP 服务器开发 | Superpowers 设计、计划、TDD 流程 | `mcp-builder` | `mcp-builder` 提供 MCP 领域方法，不跳过设计、测试和验证。 |
| YAML 多角色工作流 | 按任务需要使用计划与验证技能 | `workflow-runner` | 用于执行 agency-orchestrator YAML；不把普通单任务强行改造为多角色工作流。 |
| 代码审查 | `requesting-code-review` → `receiving-code-review` | `chinese-code-review` | 保持技术严谨性，使用 `[必须修复]`、`[建议修改]`、`[仅供参考]` 和 `[问题]` 表达优先级。 |
| 完成验证 | `verification-before-completion` | 无替代技能 | 必须先运行并阅读验证结果，再声称完成。 |
| 提交与收尾 | `finishing-a-development-branch` | `chinese-commit-conventions` | 提交信息使用中文表达，但必须同时遵守仓库已有的 Lore Commit Protocol。 |
| 分支、PR/MR 和 CI/CD | Superpowers 收尾与验证流程 | `chinese-git-workflow` | 根据 Gitee、Coding、极狐 GitLab 或 CNB 选择平台和交付规范。 |

### 执行原则

1. 先由 `using-superpowers` 识别任务所需的流程技能。
2. 再叠加与当前阶段匹配的中文或领域技能。
3. 冲突时优先遵守用户指令和本文档的本地规则。
4. 不修改原生 Superpowers 技能文件，避免后续升级覆盖本地习惯。

# Minimal Codex Project Rules

## Goal

Use Codex with bounded context and bounded subagent cost. Prefer direct solo execution unless a subtask is independent, bounded, and materially benefits from delegation.

## Context And Cost Limits

- Avoid dumping large files, logs, or command output into the conversation.
- Prefer targeted reads, summaries, and structural search.
- Do not spawn subagents for trivial work.
- Never use more than 2 concurrent child agents.

## Subagent Routing

When spawning native Codex subagents, choose by subtask difficulty:

- Hard / frontier-critical: use `hard-worker`.
- Standard implementation, debugging, architecture, or verification: use `standard-worker`.
- Easy, bounded, mechanical, formatting, summarization, or lookup work: use `easy-worker`.

If a Codex surface cannot select the named agent directly, use the same model mapping:

- Hard: `gpt-5.6-sol` with `model_reasoning_effort = "medium"`.
- Standard: `gpt-5.5` with `model_reasoning_effort = "high"`.
- Easy: `gpt-5.5` with `model_reasoning_effort = "medium"`.

## Default Behavior

- Work directly by default.
- Delegate only independent tasks with clear ownership and expected output.
- Ask child agents to return summaries and evidence, not raw logs.
- Keep write-heavy parallel work rare, because it increases conflicts and coordination cost.
- Verify before claiming completion.
