import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import { createStore } from "vuex";

vi.mock("../assets/scripts/agentApi", () => {
  const api = {
    listTasks: vi.fn(),
    createTask: vi.fn(),
    getTask: vi.fn(),
    cancelTask: vi.fn(),
    getVersion: vi.fn(),
    getReplay: vi.fn(),
    saveVersion: vi.fn(),
  };
  return { agentApi: api, default: api };
});

import { agentApi } from "../assets/scripts/agentApi";
import agentModule from "./agent";

function newStore() {
  return createStore({
    state: () => ({ user: { token: "tok" } }),
    modules: { agent: agentModule },
  });
}

describe("agent store（阶段 4 任务 2）", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    agentApi.listTasks.mockReset();
    agentApi.createTask.mockReset();
    agentApi.getTask.mockReset();
    agentApi.cancelTask.mockReset();
    agentApi.getVersion.mockReset();
    agentApi.getReplay.mockReset();
    agentApi.saveVersion.mockReset();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  test("startPolling 立即拉取一次并每秒轮询", async () => {
    const store = newStore();
    agentApi.getTask.mockResolvedValue({ id: 42, status: "EVALUATING", versions: [] });
    await store.dispatch("agent/startPolling", 42);
    expect(agentApi.getTask).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1000);
    expect(agentApi.getTask).toHaveBeenCalledTimes(2);
    await store.dispatch("agent/stopPolling");
  });

  test("终态停止轮询并清空 pollTimer", async () => {
    const store = newStore();
    agentApi.getTask.mockResolvedValue({ id: 42, status: "COMPLETED", versions: [] });
    await store.dispatch("agent/startPolling", 42);
    expect(store.state.agent.pollTimer).toBe(null);
    await vi.advanceTimersByTimeAsync(2000);
    expect(agentApi.getTask).toHaveBeenCalledTimes(1);
  });

  test("重复 startPolling 同一任务不创建第二个 Timer", async () => {
    const store = newStore();
    agentApi.getTask.mockResolvedValue({ id: 42, status: "EVALUATING", versions: [] });
    await store.dispatch("agent/startPolling", 42);
    await store.dispatch("agent/startPolling", 42);
    expect(agentApi.getTask).toHaveBeenCalledTimes(1);
    await store.dispatch("agent/stopPolling");
  });

  test("切换到不同任务时停止旧轮询并启动新轮询", async () => {
    const store = newStore();
    agentApi.getTask.mockResolvedValue({ id: 42, status: "EVALUATING", versions: [] });
    await store.dispatch("agent/startPolling", 42);
    await store.dispatch("agent/startPolling", 43);
    expect(store.state.agent.pollingTaskId).toBe(43);
    await vi.advanceTimersByTimeAsync(1000);
    // 旧任务只拉一次，新任务拉一次后又轮询一次
    expect(agentApi.getTask).toHaveBeenCalledTimes(3);
    await store.dispatch("agent/stopPolling");
  });

  test("stopPolling 后不再请求", async () => {
    const store = newStore();
    agentApi.getTask.mockResolvedValue({ id: 42, status: "EVALUATING", versions: [] });
    await store.dispatch("agent/startPolling", 42);
    await store.dispatch("agent/stopPolling");
    await vi.advanceTimersByTimeAsync(3000);
    expect(agentApi.getTask).toHaveBeenCalledTimes(1);
    expect(store.state.agent.pollTimer).toBe(null);
    expect(store.state.agent.pollingTaskId).toBe(null);
  });

  test("运行中详情剥离 hiddenEvaluation，终态保留", async () => {
    const store = newStore();
    agentApi.getTask.mockResolvedValueOnce({
      id: 42,
      status: "EVALUATING",
      hiddenEvaluation: { gameCount: 24 },
      versions: [],
    });
    await store.dispatch("agent/startPolling", 42);
    expect(store.state.agent.currentTask.hiddenEvaluation).toBeUndefined();
    await store.dispatch("agent/stopPolling");

    agentApi.getTask.mockResolvedValueOnce({
      id: 42,
      status: "COMPLETED",
      hiddenEvaluation: { gameCount: 24 },
      versions: [],
    });
    await store.dispatch("agent/startPolling", 42);
    expect(store.state.agent.currentTask.hiddenEvaluation).toEqual({ gameCount: 24 });
  });

  test("createTask 传 token 与 payload，成功返回 task_id", async () => {
    const store = newStore();
    agentApi.createTask.mockResolvedValue({ error_message: "success", task_id: 7 });
    const id = await store.dispatch("agent/createTask", {
      goal: "g",
      maxIterations: 3,
    });
    expect(agentApi.createTask).toHaveBeenCalledWith({ goal: "g", maxIterations: 3 }, "tok");
    expect(id).toBe(7);
    expect(store.state.agent.creating).toBe(false);
  });

  test("createTask 失败设置错误摘要", async () => {
    const store = newStore();
    agentApi.createTask.mockRejectedValue({ message: "已有运行中的 Agent 任务", status: 409 });
    const id = await store.dispatch("agent/createTask", {
      goal: "g",
      maxIterations: 3,
    });
    expect(id).toBe(null);
    expect(store.state.agent.errorMessage).toBe("已有运行中的 Agent 任务");
  });

  test("loadTasks 写入任务列表", async () => {
    const store = newStore();
    agentApi.listTasks.mockResolvedValue([{ id: 1, status: "COMPLETED" }]);
    await store.dispatch("agent/loadTasks");
    expect(agentApi.listTasks).toHaveBeenCalledWith("tok");
    expect(store.state.agent.tasks).toHaveLength(1);
  });

  test("cancelTask 调用取消并刷新任务", async () => {
    const store = newStore();
    agentApi.cancelTask.mockResolvedValue({ error_message: "success" });
    agentApi.getTask.mockResolvedValue({ id: 9, status: "CANCELLED", versions: [] });
    await store.dispatch("agent/cancelTask", 9);
    expect(agentApi.cancelTask).toHaveBeenCalledWith(9, "tok");
    expect(store.state.agent.currentTask.status).toBe("CANCELLED");
  });

  test("loadVersion 与 loadReplay 写入对应状态", async () => {
    const store = newStore();
    agentApi.getVersion.mockResolvedValue({ id: 5, sourceCode: "x" });
    agentApi.getReplay.mockResolvedValue({ record: {}, opponentKey: "greedy" });
    await store.dispatch("agent/loadVersion", 5);
    expect(agentApi.getVersion).toHaveBeenCalledWith(5, "tok");
    expect(store.state.agent.currentVersion.id).toBe(5);
    await store.dispatch("agent/loadReplay", 11);
    expect(agentApi.getReplay).toHaveBeenCalledWith(11, "tok");
    expect(store.state.agent.currentReplay.opponentKey).toBe("greedy");
  });

  test("saveVersion 传 title 与 token", async () => {
    const store = newStore();
    agentApi.saveVersion.mockResolvedValue({ error_message: "success" });
    await store.dispatch("agent/saveVersion", {
      versionId: 5,
      payload: { title: "Agent V1" },
    });
    expect(agentApi.saveVersion).toHaveBeenCalledWith(5, { title: "Agent V1" }, "tok");
  });
});
