/**
 * Agent Lab 纯视图模型（阶段 4 任务 1）。
 *
 * <p>不依赖 Vue、Vuex 或 jQuery，仅做展示数据派生，便于 Vitest 直接覆盖。
 * 时间线固定六个阶段槽位，状态只改变颜色与文案，不增删槽位，避免轮询时布局跳动。
 */

const PHASES = [
  "GENERATING",
  "COMPILING",
  "EVALUATING",
  "ANALYZING",
  "IMPROVING",
  "VALIDATING",
];

const PHASE_LABELS = [
  "生成代码",
  "沙箱编译",
  "公开评测",
  "失败分析",
  "策略改进",
  "隐藏验证",
];

// 运行中状态映射到时间线上唯一的「运行中」阶段；REPAIRING 复用 COMPILING 槽位。
const STATUS_TO_PHASE = {
  CREATED: "GENERATING",
  GENERATING: "GENERATING",
  COMPILING: "COMPILING",
  REPAIRING: "COMPILING",
  EVALUATING: "EVALUATING",
  ANALYZING: "ANALYZING",
  IMPROVING: "IMPROVING",
  VALIDATING: "VALIDATING",
};

const TERMINAL_STATUSES = ["COMPLETED", "FAILED", "CANCELLED"];

/** 任务是否进入终态（轮询应停止）。 */
export function isTerminal(status) {
  return TERMINAL_STATUSES.includes(status);
}

function hasPublicEvaluation(version) {
  if (version.publicEvaluation != null) return true;
  // 阶段 3 真实 DTO 将公开评测扁平化为 publicGameCount/publicScore/publicP95Ms。
  return (
    version.publicGameCount != null ||
    version.publicScore != null ||
    version.publicP95Ms != null
  );
}

/**
 * 只返回编译成功且存在公开评测的版本，最多 3 个，按 iteration 升序。
 * 后端已做同样裁剪，前端再防御一次，避免历史任务或异常响应导致越界。
 */
export function displayVersions(task) {
  if (!task || !Array.isArray(task.versions)) return [];
  return task.versions
    .filter(
      (version) =>
        version && version.compileStatus === "SUCCESS" && hasPublicEvaluation(version)
    )
    .slice()
    .sort((a, b) => (a.iteration || 0) - (b.iteration || 0))
    .slice(0, 3);
}

/**
 * 构建固定六槽位时间线。非终态恰好一个 running；COMPLETED 全部 done；
 * FAILED/CANCELLED 用 steps 推断最后一个到达阶段并标记 failed。
 */
export function buildTimeline(status, steps) {
  const runningPhase = isTerminal(status) ? null : STATUS_TO_PHASE[status] || null;
  const runningIndex = runningPhase ? PHASES.indexOf(runningPhase) : -1;

  let failedIndex = -1;
  if (
    (status === "FAILED" || status === "CANCELLED") &&
    Array.isArray(steps) &&
    steps.length
  ) {
    failedIndex = steps
      .map((step) => (step && step.phase ? PHASES.indexOf(step.phase) : -1))
      .filter((index) => index >= 0)
      .reduce((acc, index) => Math.max(acc, index), -1);
  }

  return PHASES.map((phase, index) => {
    let state = "pending";
    if (runningIndex >= 0) {
      state = index < runningIndex ? "done" : index === runningIndex ? "running" : "pending";
    } else if (status === "COMPLETED") {
      state = "done";
    } else if (failedIndex >= 0) {
      state = index < failedIndex ? "done" : index === failedIndex ? "failed" : "pending";
    }
    return { phase, label: PHASE_LABELS[index], state };
  });
}

/** 空指标统一显示短横线，避免渲染 null/undefined。 */
export function formatMetric(value, digits) {
  if (value == null) return "-";
  const number = Number(value);
  if (Number.isNaN(number)) return "-";
  return number.toFixed(digits);
}

function splitLines(source) {
  if (source == null || source === "") return [];
  return source.split("\n");
}

/**
 * 基于最长公共子序列按行比较父版本与当前版本源码。
 * 返回 added/removed 计数与最多 20 行变更（"- " 删除、"+ " 新增），不引入 Diff 依赖。
 */
export function buildLineDiff(parentSource, currentSource) {
  const a = splitLines(parentSource);
  const b = splitLines(currentSource);
  const m = a.length;
  const n = b.length;

  // dp[i][j] = a[i..] 与 b[j..] 的最长公共子序列长度。
  const dp = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0));
  for (let i = m - 1; i >= 0; i--) {
    for (let j = n - 1; j >= 0; j--) {
      if (a[i] === b[j]) {
        dp[i][j] = dp[i + 1][j + 1] + 1;
      } else {
        dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
      }
    }
  }

  const lines = [];
  let added = 0;
  let removed = 0;
  let i = 0;
  let j = 0;
  while (i < m && j < n) {
    if (a[i] === b[j]) {
      i++;
      j++;
    } else if (dp[i + 1][j] >= dp[i][j + 1]) {
      lines.push("- " + a[i]);
      removed++;
      i++;
    } else {
      lines.push("+ " + b[j]);
      added++;
      j++;
    }
  }
  while (i < m) {
    lines.push("- " + a[i]);
    removed++;
    i++;
  }
  while (j < n) {
    lines.push("+ " + b[j]);
    added++;
    j++;
  }

  return { added, removed, lines: lines.slice(0, 20) };
}
