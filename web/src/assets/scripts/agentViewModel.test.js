import { describe, expect, test } from "vitest";
import {
  buildLineDiff,
  buildTimeline,
  canShowHiddenMetrics,
  displayVersions,
  formatMetric,
  isTerminal,
  representativeReplays,
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

  test("真实 DTO 形状按 publicGameCount 判定公开评测", () => {
    const versions = displayVersions({
      versions: [
        { id: 1, iteration: 1, compileStatus: "SUCCESS", publicGameCount: null },
        {
          id: 2,
          iteration: 2,
          compileStatus: "SUCCESS",
          publicGameCount: 48,
          publicScore: 0.6,
        },
      ],
    });
    expect(versions.map((version) => version.id)).toEqual([2]);
  });

  test("最多展示三个版本且按 iteration 升序", () => {
    const versions = displayVersions({
      versions: [
        { id: 3, iteration: 3, compileStatus: "SUCCESS", publicEvaluation: { gameCount: 48 } },
        { id: 1, iteration: 1, compileStatus: "SUCCESS", publicEvaluation: { gameCount: 48 } },
        { id: 2, iteration: 2, compileStatus: "SUCCESS", publicEvaluation: { gameCount: 48 } },
        { id: 4, iteration: 4, compileStatus: "SUCCESS", publicEvaluation: { gameCount: 48 } },
      ],
    });
    expect(versions.map((version) => version.id)).toEqual([1, 2, 3]);
  });

  test("运行中时间线只标记一个当前阶段", () => {
    const timeline = buildTimeline("EVALUATING", []);
    expect(timeline.filter((item) => item.state === "running")).toHaveLength(1);
  });

  test("时间线固定六个阶段槽位", () => {
    expect(buildTimeline("EVALUATING", [])).toHaveLength(6);
  });

  test("终态时间线没有运行中阶段", () => {
    expect(
      buildTimeline("COMPLETED", []).filter((item) => item.state === "running")
    ).toHaveLength(0);
    expect(
      buildTimeline("FAILED", []).filter((item) => item.state === "running")
    ).toHaveLength(0);
  });

  test("空指标显示短横线", () => {
    expect(formatMetric(null, 1)).toBe("-");
    expect(formatMetric(undefined, 0)).toBe("-");
    expect(formatMetric(0.625, 1)).toBe("0.6");
  });

  test("按行统计版本差异", () => {
    const diff = buildLineDiff("a\nb\nc", "a\nb2\nc\nd");
    expect(diff.added).toBe(2);
    expect(diff.removed).toBe(1);
    expect(diff.lines).toEqual(["- b", "+ b2", "+ d"]);
  });

  test("无父版本时全部为新增", () => {
    const diff = buildLineDiff("", "a\nb");
    expect(diff.added).toBe(2);
    expect(diff.removed).toBe(0);
  });

  test("只在终态展示隐藏指标", () => {
    expect(canShowHiddenMetrics({ status: "EVALUATING" })).toBe(false);
    expect(canShowHiddenMetrics({ status: "COMPLETED" })).toBe(true);
    expect(canShowHiddenMetrics(null)).toBe(false);
  });

  test("优先选取失败和成功各一条代表录像", () => {
    const runs = [
      { id: 1, result: "WIN", opponentKey: "greedy" },
      { id: 2, result: "WIN", opponentKey: "territory" },
      { id: 3, result: "LOSS", opponentKey: "safe" },
      { id: 4, result: "DRAW", opponentKey: "greedy" },
    ];
    const selected = representativeReplays(runs);
    expect(selected.filter((run) => run.result === "LOSS")).toHaveLength(1);
    expect(selected.filter((run) => run.result === "WIN")).toHaveLength(1);
    expect(selected).toHaveLength(2);
  });

  test("没有胜负录像时返回空", () => {
    expect(representativeReplays([{ id: 1, result: "DRAW" }])).toEqual([]);
    expect(representativeReplays([])).toEqual([]);
    expect(representativeReplays(null)).toEqual([]);
  });
});
