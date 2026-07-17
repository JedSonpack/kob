# Agent Lab 真实模型实验协议

> 目的：用真实 LLM 验证「自然语言策略 -> 自主进化 Bot」是否可重复提升，并留下可复现的原始记录。
> 约束：不得人工修改候选源码后继续评测；不删除失败实验；密钥不入库、不入日志、不入 Markdown。

## 1. 实验固定条件

- 策略目标（三次实验保持一致）：

  ```text
  尽量扩大可活动区域，避免进入狭窄通道；在多个安全方向中优先保留后续选择。
  ```

- 公开调试集：8 个固定地图种子，每图对战 3 个基准策略并交换出生位置，共 48 局。
- 隐藏验证集：4 个固定地图种子，共 24 局；仅在公开集迭代结束后运行，结果不进入模型上下文。
- 评测配置：`kob.agent.evaluation.max-p95-ms=100`，合法移动率必须为 100% 才进入隐藏验证。
- 最大迭代轮数：3，每轮最多 2 次编译尝试。
- 评测规模、基准策略、数据集在三次实验中不得改动。

## 2. 每次实验需记录

- 日期时间、模型名称、Prompt 版本。
- V1～V3 公开集分数、隐藏集分数、胜率、合法移动率、P95 延迟、Token、总耗时。
- 最终选择版本与选择理由（隐藏集最高分，退化超过 5% 时保留 V1）。
- 失败原因（编译失败、非法移动、超时等）原样保留。

## 3. 指标文件命名与表格

- 文件命名：`YYYYMMDD-HHmm-模型名.md`，存于 `docs/agent-lab/experiments/`。
- 每份实验文件包含以下表格。数据从任务详情接口和受控实验输出中逐项抄录，不估算、不补齐缺失值：

  ```text
  版本 | 公开得分 | 隐藏得分 | 胜率 | 合法移动率 | P95 | Token | 是否接受
  ```

- Token = 该版本 Trace 中所有 Step 的 `prompt_tokens + completion_tokens` 之和。
- 公开得分和隐藏得分为综合得分（胜 1、平 0.5），范围为 0～1；未进入隐藏验证的版本记为「未验证」，不得填 0。

## 4. 结论判定

- 三次中至少两次同方向改善（隐藏集相对 V1 提升），才描述为「可重复提升」。
- 写中位数与波动范围，不写单次最优。
- 没有稳定提升：写「系统保留旧版本，并通过隐藏集避免伪优化」。
- 某次失败：保留失败原因，作为可靠性案例，不抹除。

## 5. 安全约束

- `KOB_AGENT_LLM_API_KEY` 只从环境变量读取，不写入任何 Markdown、日志摘要或 Git 文件。
- 提交前扫描：`rg -n "KOB_AGENT_LLM_API_KEY|Authorization: Bearer [A-Za-z0-9]" readme.md docs backendcloud web scripts`。
- 真实模型实验与自动化 E2E 互斥：E2E 必须用 `KOB_AGENT_LLM_PROVIDER=fake`，不得消耗线上 Token。

## 6. 运行步骤

```bash
export KOB_AGENT_LLM_PROVIDER=openai-compatible
export KOB_AGENT_LLM_BASE_URL=https://api.openai.com
export KOB_AGENT_LLM_MODEL=<实际可用模型>
export KOB_AGENT_LLM_API_KEY=<本地密钥，不入库>

./scripts/dev.sh restart
# 浏览器打开 http://localhost:8080/agent-lab/ ，输入固定目标，3 轮。
# 完成后从任务详情接口抄录聚合数据，写入 docs/agent-lab/experiments/YYYYMMDD-HHmm-模型名.md
./scripts/dev.sh stop
```
