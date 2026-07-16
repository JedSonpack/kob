# KOB Agent Lab 阶段 4：Web 工作台与面试交付实施计划

> **面向智能体工作者：** 必需子技能：使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 逐项实施此计划。步骤使用复选框（`- [ ]`）语法跟踪。

**目标：** 交付可用的 `/agent-lab/` 工作台、Fake LLM 端到端测试、一键启动说明、真实模型实验报告、90 秒演示脚本和面试材料。

**架构：** 前端使用独立 `agentApi.js` 和 namespaced Vuex 模块管理任务、轮询和版本详情，不修改当前用户已有的 `apiClient.js` 变更。页面由任务表单、稳定尺寸时间线、版本表、Trace 表和 Replay 面板组成；运行状态每秒轮询，终态自动停止。

**技术栈：** Vue 3、Vuex 4、Vue Router 4、Bootstrap 5、jQuery Ajax、Vitest、Playwright、Node.js 20

## 全局约束

- 首屏直接是 Agent Lab 工作台，不增加营销落地页。
- 不新增前端依赖。
- 不修改或覆盖用户当前未提交的 `web/src/assets/scripts/apiClient.js`、`web/src/store/user.js` 和现有页面变更。
- 使用现有 Bootstrap 与项目配色，但工作台采用全宽分区，不把页面区块全部做成嵌套卡片。
- 状态时间线、版本指标列和按钮尺寸必须稳定，轮询时不得引发布局跳动。
- 最大迭代默认 3，前端不允许提交超过 3。
- 运行中每秒轮询；`COMPLETED`、`FAILED` 或 `CANCELLED` 后停止。
- 运行中响应不得渲染或缓存隐藏集指标。
- 错误只展示标准摘要，不展示服务栈、密钥或模型原始响应。
- 自动化 E2E 使用 `FakeLlmClient`，不得调用真实模型。
- README 中所有测试数量、延迟和胜率必须来自实际命令或实验文件。
- 至少完成 3 次真实模型实验后，才能把稳定提升写入简历。
- 执行 Java 验证命令前先运行：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home
export PATH="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin:$PATH"
```

---

## 文件结构

### 创建

- `web/src/assets/scripts/agentApi.js`
- `web/src/assets/scripts/agentViewModel.js`
- `web/src/assets/scripts/agentViewModel.test.js`
- `web/src/store/agent.js`
- `web/src/store/agent.test.js`
- `web/src/views/agent/AgentLabIndexView.vue`
- `web/src/components/agent/AgentTaskForm.vue`
- `web/src/components/agent/AgentWorkflowTimeline.vue`
- `web/src/components/agent/AgentVersionTable.vue`
- `web/src/components/agent/AgentTraceTable.vue`
- `web/src/components/agent/AgentReplayPanel.vue`
- `scripts/tests/agent_lab_playwright.js`
- `docs/agent-lab/demo-script.md`
- `docs/agent-lab/experiment-protocol.md`
- `docs/agent-lab/experiments/.gitkeep`

### 修改

- `web/src/store/index.js`
- `web/src/router/index.js`
- `web/src/components/NavBar.vue`
- `readme.md`
- `scripts/dev.sh`：显式导出 Fake LLM 默认配置，但不得改变现有 `start/status/logs/stop/restart` 命令。

---

### 任务 1：实现独立 Agent API 和纯视图模型

**文件：**
- 创建：`web/src/assets/scripts/agentApi.js`
- 创建：`web/src/assets/scripts/agentViewModel.js`
- 创建：`web/src/assets/scripts/agentViewModel.test.js`

**接口：**
- 产出：

```javascript
agentApi.createTask(payload, token)
agentApi.listTasks(token)
agentApi.getTask(taskId, token)
agentApi.cancelTask(taskId, token)
agentApi.getVersion(versionId, token)
agentApi.getReplay(runId, token)
agentApi.saveVersion(versionId, payload, token)
```

```javascript
isTerminal(status)
buildTimeline(status, steps)
displayVersions(task)
formatMetric(value, digits)
buildLineDiff(parentSource, currentSource)
```

- [ ] **步骤 1：编写失败测试**

```javascript
import { describe, expect, test } from "vitest";
import {
  buildTimeline,
  displayVersions,
  formatMetric,
  isTerminal,
} from "./agentViewModel";

describe("agentViewModel", () => {
  test("识别三种终态", () => {
    expect(isTerminal("COMPLETED")).toBe(true);
    expect(isTerminal("FAILED")).toBe(true);
    expect(isTerminal("CANCELLED")).toBe(true);
    expect(isTerminal("EVALUATING")).toBe(false);
  });

  test("失败编译尝试不进入展示版本", () => {
    const versions = displayVersions({
      versions: [
        { id: 1, compileStatus: "FAILED", publicEvaluation: null },
        { id: 2, compileStatus: "SUCCESS", publicEvaluation: { gameCount: 48 } },
      ],
    });
    expect(versions.map((version) => version.id)).toEqual([2]);
  });

  test("运行中时间线只标记一个当前阶段", () => {
    const timeline = buildTimeline("EVALUATING", []);
    expect(timeline.filter((item) => item.state === "running")).toHaveLength(1);
  });

  test("空指标显示短横线", () => {
    expect(formatMetric(null, 1)).toBe("-");
    expect(formatMetric(0.625, 1)).toBe("0.6");
  });

  test("按行统计版本差异", () => {
    const diff = buildLineDiff("a\\nb\\nc", "a\\nb2\\nc\\nd");
    expect(diff.added).toBe(2);
    expect(diff.removed).toBe(1);
    expect(diff.lines).toEqual(["- b", "+ b2", "+ d"]);
  });
});
```

- [ ] **步骤 2：确认测试失败**

```bash
cd web
npm test -- agentViewModel.test.js
```

预期：缺少模块。

- [ ] **步骤 3：实现纯函数**

时间线固定阶段：

```javascript
const PHASES = [
  "GENERATING",
  "COMPILING",
  "EVALUATING",
  "ANALYZING",
  "IMPROVING",
  "VALIDATING",
];
```

`displayVersions` 只返回 `compileStatus === "SUCCESS"` 且存在公开评测的版本，最多 3 个，按 iteration 升序。

`buildLineDiff` 使用最长公共子序列（LCS）按行比较父版本与当前版本，返回 `added`、`removed` 和最多 20 行变更；不引入 Diff 依赖。

- [ ] **步骤 4：实现 Promise API**

`agentApi.js` 只导入 jQuery，Token 由 Vuex Action 从 `rootState.user.token` 传入，避免 `store/index.js -> agent.js -> agentApi.js -> store/index.js` 循环依赖：

```javascript
import $ from "jquery";

function request({ method, url, data, token }) {
  return new Promise((resolve, reject) => {
    $.ajax({
      url,
      type: method,
      data: data == null ? undefined : JSON.stringify(data),
      contentType: data == null ? undefined : "application/json",
      headers: {
        Authorization: "Bearer " + (token || ""),
      },
      success: resolve,
      error: reject,
    });
  });
}
```

所有路径必须与阶段 3 API 完全一致。

- [ ] **步骤 5：运行纯函数测试**

```bash
cd web
npm test -- agentViewModel.test.js
```

预期：通过。

- [ ] **步骤 6：提交**

```bash
git add web/src/assets/scripts/agentApi.js \
  web/src/assets/scripts/agentViewModel.js \
  web/src/assets/scripts/agentViewModel.test.js
git commit -m "feat(Agent 前端): 建立独立 API 与展示模型"
```

### 任务 2：实现 Vuex 任务状态、轮询和取消

**文件：**
- 创建：`web/src/store/agent.js`
- 创建：`web/src/store/agent.test.js`
- 修改：`web/src/store/index.js`

**接口：**
- 产出 namespaced 模块：

```text
agent/createTask
agent/loadTasks
agent/openTask
agent/startPolling
agent/stopPolling
agent/cancelTask
agent/loadVersion
agent/loadReplay
agent/saveVersion
```

- [ ] **步骤 1：编写失败测试**

使用 Fake Timer 和 Mock API，断言：

```javascript
await store.dispatch("agent/startPolling", 42);
expect(api.getTask).toHaveBeenCalledTimes(1);
await vi.advanceTimersByTimeAsync(1000);
expect(api.getTask).toHaveBeenCalledTimes(2);
```

当响应状态为 `COMPLETED` 时：

```javascript
expect(store.state.agent.pollTimer).toBe(null);
```

重复 `startPolling(42)` 不创建第二个 Timer；组件卸载调用 `stopPolling` 后不再请求。

- [ ] **步骤 2：确认测试失败**

```bash
cd web
npm test -- agent.test.js
```

预期：缺少 Vuex 模块。

- [ ] **步骤 3：实现状态**

```javascript
state: () => ({
  tasks: [],
  currentTask: null,
  currentVersion: null,
  currentReplay: null,
  loading: false,
  creating: false,
  errorMessage: "",
  pollTimer: null,
  pollingTaskId: null,
})
```

运行中详情收到 `hiddenEvaluation` 字段时，在 commit 前删除该字段；终态详情允许保留。

- [ ] **步骤 4：实现轮询**

使用 `setTimeout` 链式调度，不使用 `setInterval`：

```text
请求完成
判断终态
非终态等待 1000 ms
再次请求
```

这样避免网络慢时请求重叠。`stopPolling` 必须清除 Timer 并重置 taskId。

每个 Action 从 `rootState.user.token` 读取 Token，再传给 `agentApi`；`agentApi` 不读取 Store。

- [ ] **步骤 5：运行 Store 测试**

```bash
cd web
npm test -- agent.test.js
```

预期：轮询、终态停止、重复启动、取消和错误摘要测试通过。

- [ ] **步骤 6：提交**

```bash
git add web/src/store/agent.js web/src/store/agent.test.js web/src/store/index.js
git commit -m "feat(Agent 前端): 管理任务轮询取消与详情状态"
```

### 任务 3：实现工作台页面、路由和导航

**文件：**
- 创建：`web/src/views/agent/AgentLabIndexView.vue`
- 创建：`web/src/components/agent/AgentTaskForm.vue`
- 创建：`web/src/components/agent/AgentWorkflowTimeline.vue`
- 创建：`web/src/components/agent/AgentVersionTable.vue`
- 创建：`web/src/components/agent/AgentTraceTable.vue`
- 修改：`web/src/router/index.js`
- 修改：`web/src/components/NavBar.vue`

**接口：**
- 消费：Vuex `agent` 模块。
- 产出：认证路由 `/agent-lab/`，路由名 `agent_lab_index`。

- [ ] **步骤 1：先增加路由并确认页面缺失**

在 Router 添加：

```javascript
{
  path: "/agent-lab/",
  name: "agent_lab_index",
  component: () => import("../views/agent/AgentLabIndexView.vue"),
  meta: { requestAuth: true },
}
```

运行：

```bash
cd web
npm run build
```

预期：构建失败，提示缺少 `AgentLabIndexView.vue`。

- [ ] **步骤 2：实现任务表单**

`AgentTaskForm.vue`：

- 策略目标使用 `textarea`，最大长度 1000。
- 最大迭代使用 `select`，选项 `1/2/3`，默认 `3`。
- 主命令按钮文案「开始进化」。
- creating 时禁用输入和按钮。
- 表单只 emit：

```javascript
emit("submit", { goal: goal.value.trim(), maxIterations: maxIterations.value });
```

- [ ] **步骤 3：实现稳定时间线**

时间线固定渲染 6 个阶段槽位，每个槽位最小宽度和高度固定。状态只改变颜色、图标字符和辅助文本，不增删槽位。

阶段文案：

```text
生成代码
沙箱编译
公开评测
失败分析
策略改进
隐藏验证
```

- [ ] **步骤 4：实现版本和 Trace 表**

版本表固定列：

```text
版本
策略摘要
公开得分
胜率
平均回合
P95 延迟
合法移动率
结果
操作
```

Trace 表固定列：

```text
序号
阶段
工具
状态
耗时
Token
摘要
```

窄屏允许横向滚动，不压缩到文字重叠。

- [ ] **步骤 5：实现主页面**

页面布局：

```text
标题和任务状态
任务创建/历史选择区
Workflow 时间线
版本对比
Trace
Replay
```

运行中显示取消按钮；终态显示「新建任务」。组件 `onMounted` 加载任务列表，`onBeforeUnmount` 停止轮询。

- [ ] **步骤 6：增加导航**

在登录用户可见的主导航中加入「Agent Lab」，active 判断使用：

```javascript
route_name == "agent_lab_index"
```

不修改用户下拉菜单和现有退出行为。

- [ ] **步骤 7：构建和 lint**

```bash
cd web
npm run lint
npm run build
```

预期：两条命令退出码均为 `0`。

- [ ] **步骤 8：提交**

```bash
git add web/src/views/agent web/src/components/agent \
  web/src/router/index.js web/src/components/NavBar.vue
git commit -m "feat(Agent 工作台): 展示任务时间线版本与 Trace"
```

### 任务 4：实现版本源码、Replay 和保存 Bot

**文件：**
- 创建：`web/src/components/agent/AgentReplayPanel.vue`
- 修改：`web/src/views/agent/AgentLabIndexView.vue`
- 修改：`web/src/components/agent/AgentVersionTable.vue`
- 测试：`web/src/assets/scripts/agentViewModel.test.js`

**接口：**
- 消费：版本详情、Replay API、保存 API。
- 产出：版本查看、代表性录像和保存为「我的 Bot」。

- [ ] **步骤 1：增加视图模型失败测试**

增加：

```javascript
test("只在终态展示隐藏指标", () => {
  expect(canShowHiddenMetrics({ status: "EVALUATING" })).toBe(false);
  expect(canShowHiddenMetrics({ status: "COMPLETED" })).toBe(true);
});

test("优先选取失败和成功各一条代表录像", () => {
  const selected = representativeReplays(runs);
  expect(selected.filter((run) => run.result === "LOSS")).toHaveLength(1);
  expect(selected.filter((run) => run.result === "WIN")).toHaveLength(1);
});
```

- [ ] **步骤 2：确认测试失败**

```bash
cd web
npm test -- agentViewModel.test.js
```

预期：缺少函数。

- [ ] **步骤 3：实现版本详情**

点击「查看代码」后加载当前版本和 `parentVersionId` 对应父版本，在页面下方使用只读 `<pre><code>` 展示源码、changeReason 与 `buildLineDiff` 结果。V1 没有父版本时显示「初始生成版本」，不伪造差异。

- [ ] **步骤 4：实现 Replay**

`AgentReplayPanel.vue` 复用现有 `GameMap.vue` 和 `populateRecordFromItem`。加载 Replay 后执行：

```javascript
populateRecordFromItem(store, replayResponse);
```

模板使用固定尺寸容器：

```vue
<div class="agent-replay-canvas">
  <GameMap v-if="replayLoaded" />
</div>
```

组件卸载时提交 `updateIsRecord(false)`；`GameMap.vue` 负责销毁动画、Timer 和游戏对象。不修改 `RecordContentView.vue`、`recordHelper.js` 或在线对战 Store。

Replay 区展示：

- 对手名称。
- 出生侧。
- 胜负和失败原因。
- 回合数。
- 播放/暂停和重新播放命令。

- [ ] **步骤 5：实现保存 Bot**

仅 `COMPLETED` 且版本 `compileStatus === "SUCCESS"` 时显示「保存为我的 Bot」。保存请求：

```javascript
{
  title: `Agent ${version.label}`,
}
```

成功后显示「已保存」，重复点击禁用；后端仍负责数量和长度校验。

- [ ] **步骤 6：运行测试、lint 和构建**

```bash
cd web
npm test
npm run lint
npm run build
```

预期：全部通过。

- [ ] **步骤 7：提交**

```bash
git add web/src/components/agent web/src/views/agent \
  web/src/assets/scripts/agentViewModel.js \
  web/src/assets/scripts/agentViewModel.test.js
git commit -m "feat(Agent 交付): 支持源码回放与正式 Bot 保存"
```

### 任务 5：增加 Agent Lab Playwright 闭环

**文件：**
- 创建：`scripts/tests/agent_lab_playwright.js`
- 修改：`scripts/dev.sh`

**接口：**
- 消费：本地 MySQL、3 个 Java 服务、Web 前端和 Fake LLM。
- 产出：浏览器完整闭环证据。

- [ ] **步骤 1：编写失败 E2E**

脚本流程固定为：

```text
1. 通过 API 注册唯一用户并登录。
2. 打开 /agent-lab/。
3. 输入策略目标并选择 3 轮。
4. 点击「开始进化」。
5. 监听每次任务详情响应，运行中断言不存在 hiddenEvaluation。
6. 等待任务进入 COMPLETED，超时 180 秒。
7. 断言展示 1～3 个公开完成版本。
8. 断言恰好一个版本标记为最佳。
9. 断言 Trace 至少包含生成、编译、公开评测和隐藏验证。
10. 打开一条 Replay。
11. 保存最佳版本为正式 Bot。
12. 调用 /api/user/bot/getlist/ 断言保存结果存在。
```

失败时输出任务详情 JSON 摘要和页面截图路径，不输出 Token。

- [ ] **步骤 2：先运行并确认失败**

```bash
./scripts/dev.sh start
node scripts/tests/agent_lab_playwright.js
```

预期：页面或后端尚有缺口时失败，错误定位到具体步骤。

- [ ] **步骤 3：修复 E2E 暴露的最小问题**

只修复 Agent Lab 功能和启动配置。不得借此重构现有页面或对战协议。

在 `scripts/dev.sh` 的服务启动前增加默认环境：

```bash
KOB_AGENT_LLM_PROVIDER=${KOB_AGENT_LLM_PROVIDER:-fake}
export KOB_AGENT_LLM_PROVIDER
```

保留现有命令和 PID 管理。

- [ ] **步骤 4：运行脚本测试与两个 Playwright**

```bash
bash scripts/tests/dev_test.sh
node scripts/tests/battle_playwright.js
node scripts/tests/agent_lab_playwright.js
```

预期：开发脚本测试、现有三类对战和 Agent Lab 闭环全部通过。

- [ ] **步骤 5：提交**

```bash
git add scripts/tests/agent_lab_playwright.js scripts/dev.sh
git commit -m "test(Agent Lab): 锁定浏览器三轮进化闭环"
```

### 任务 6：重写 README、实验协议和演示脚本

**文件：**
- 修改：`readme.md`
- 创建：`docs/agent-lab/demo-script.md`
- 创建：`docs/agent-lab/experiment-protocol.md`
- 创建：`docs/agent-lab/experiments/.gitkeep`

**接口：**
- 产出：面试官可快速理解和复现的项目材料。

- [ ] **步骤 1：重写 README 结构**

`readme.md` 固定章节：

```text
KOB Agent Lab
项目定位
90 秒体验路径
核心架构
Agent Workflow
确定性评测
沙箱与安全边界
并发、幂等与恢复
本地启动
配置真实模型
测试与验证
真实实验结果
项目取舍
面试讲法
目录结构
```

首屏必须直接说明：

```text
自然语言策略 -> Java Bot -> 沙箱编译 -> 每个最终验证版本 48 局公开评测 + 24 局隐藏验证 -> 失败分析 -> 最佳版本
```

- [ ] **步骤 2：写实验协议**

`experiment-protocol.md` 固定：

- 同一策略目标运行 3 次。
- 每次记录模型、Prompt 版本、V1～V3 公开/隐藏分数、P95、Token、总耗时和最终选择。
- 不删除失败实验。
- 只有 3 次中至少 2 次同方向改善，才描述为「可重复提升」。
- 指标文件命名为 `YYYYMMDD-HHmm-模型名.md`。

每份实验文件包含以下表格列：

```text
版本 | 公开得分 | 隐藏得分 | 胜率 | 合法移动率 | P95 | Token | 是否接受
```

- [ ] **步骤 3：写 90 秒演示脚本**

`demo-script.md` 时间轴：

```text
0～10 秒：项目定位和自然语言目标
10～25 秒：创建任务与 Workflow 时间线
25～45 秒：V1～V3 指标和失败原因
45～60 秒：Trace、Token、幂等和恢复
60～75 秒：隐藏验证与最佳版本
75～85 秒：保存 Bot 和播放 Replay
85～90 秒：安全边界与生产化方向
```

每段写出屏幕操作和口播，不使用尚未验证的数字。

- [ ] **步骤 4：核对 README 命令**

实际运行 README 中的：

```bash
./scripts/dev.sh start
./scripts/dev.sh status
cd backendcloud && mvn test
cd web && npm test && npm run lint && npm run build
node scripts/tests/battle_playwright.js
node scripts/tests/agent_lab_playwright.js
./scripts/dev.sh stop
```

预期：README 命令可直接执行。

- [ ] **步骤 5：提交**

```bash
git add readme.md docs/agent-lab
git commit -m "docs(Agent Lab): 提供可复现演示与实验口径"
```

### 任务 7：运行 3 次真实模型实验并完成最终验收

**文件：**
- 创建：`docs/agent-lab/experiments/YYYYMMDD-HHmm-模型名.md`，共至少 3 份。
- 修改：`readme.md`
- 修改：`KOB-Agent-Lab-面试报告.md`

**接口：**
- 产出：真实指标、简历 bullet 和最终面试叙事。

- [ ] **步骤 1：切换真实模型配置**

在未入库的本地配置或环境变量中设置：

```bash
export KOB_AGENT_LLM_PROVIDER=openai-compatible
export KOB_AGENT_LLM_BASE_URL=https://api.openai.com
export KOB_AGENT_LLM_MODEL=<实际可用模型>
export KOB_AGENT_LLM_API_KEY=<本地密钥>
```

密钥不得写入任何 Markdown、日志摘要或 Git 文件。

- [ ] **步骤 2：执行第 1 次实验**

使用固定目标：

```text
尽量扩大可活动区域，避免进入狭窄通道；在多个安全方向中优先保留后续选择。
```

完成后把任务详情中的聚合数据写入一份实验文件。

- [ ] **步骤 3：执行第 2、3 次实验**

保持目标、公开/隐藏数据集和评测配置不变。允许模型输出不同代码，不允许人工修改候选源码后继续评测。

- [ ] **步骤 4：更新 README 和面试报告**

只写实际结论：

- 有稳定提升：写中位数和波动范围。
- 没有稳定提升：写「系统保留旧版本，并通过隐藏集避免伪优化」。
- 某次失败：保留失败原因，作为可靠性案例。

- [ ] **步骤 5：运行最终全量验证**

```bash
cd backendcloud
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home mvn test

cd ../../web
npm test
npm run lint
npm run build

cd ..
bash scripts/tests/dev_test.sh
./scripts/dev.sh start
node scripts/tests/battle_playwright.js
node scripts/tests/agent_lab_playwright.js
./scripts/dev.sh stop
git diff --check
```

预期：全部退出码为 `0`。

- [ ] **步骤 6：检查安全与进程**

```bash
find "${TMPDIR:-/tmp}" -maxdepth 1 -name 'kob-evaluation-*' -print
pgrep -fl 'PersistentSandboxMain'
rg -n "KOB_AGENT_LLM_API_KEY|Authorization: Bearer [A-Za-z0-9]" \
  readme.md docs backendcloud web scripts
```

预期：无临时目录、无沙箱残留进程、无密钥泄漏。

- [ ] **步骤 7：提交真实实验与最终材料**

```bash
git add readme.md KOB-Agent-Lab-面试报告.md docs/agent-lab/experiments
git commit -m "docs(面试交付): 用真实实验校准 Agent Lab 结论"
```

最后提交必须包含：

```text
Tested: backendcloud 全测、web test/lint/build、开发脚本测试、现有对战 Playwright、Agent Lab Playwright、3 次真实模型实验
Not-tested: 公网部署与容器级生产沙箱
Directive: 简历和 README 指标必须继续引用 experiments 中的原始记录
```
