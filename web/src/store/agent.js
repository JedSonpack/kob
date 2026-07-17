import { agentApi } from "../assets/scripts/agentApi";
import { isTerminal } from "../assets/scripts/agentViewModel";

/**
 * Agent Lab Vuex 模块（阶段 4 任务 2）。
 *
 * <p>namespaced 模块，管理任务列表、当前任务、版本、录像与轮询。
 * 轮询使用 setTimeout 链式调度（非 setInterval），避免网络慢时请求重叠：
 * 请求完成 -> 判断终态 -> 非终态等待 1000ms -> 再次请求。
 * Token 由各 Action 从 rootState.user.token 读取后传给 agentApi，agentApi 不读 Store。
 * 运行中详情收到 hiddenEvaluation 时在 commit 前删除，终态详情允许保留。
 */
export default {
  namespaced: true,
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
  }),
  mutations: {
    setTasks(state, tasks) {
      state.tasks = tasks;
    },
    setCurrentTask(state, task) {
      state.currentTask = task;
    },
    setCurrentVersion(state, version) {
      state.currentVersion = version;
    },
    setCurrentReplay(state, replay) {
      state.currentReplay = replay;
    },
    setLoading(state, value) {
      state.loading = value;
    },
    setCreating(state, value) {
      state.creating = value;
    },
    setError(state, message) {
      state.errorMessage = message;
    },
    setPollTimer(state, timer) {
      state.pollTimer = timer;
    },
    setPollingTaskId(state, taskId) {
      state.pollingTaskId = taskId;
    },
    reset(state) {
      state.currentTask = null;
      state.currentVersion = null;
      state.currentReplay = null;
      state.errorMessage = "";
      state.pollTimer = null;
      state.pollingTaskId = null;
    },
  },
  actions: {
    async createTask({ commit, rootState }, payload) {
      commit("setCreating", true);
      commit("setError", "");
      try {
        const resp = await agentApi.createTask(payload, rootState.user.token);
        return resp ? resp.task_id : null;
      } catch (err) {
        commit("setError", (err && err.message) || "创建任务失败");
        return null;
      } finally {
        commit("setCreating", false);
      }
    },

    async loadTasks({ commit, rootState }) {
      commit("setLoading", true);
      commit("setError", "");
      try {
        const tasks = await agentApi.listTasks(rootState.user.token);
        commit("setTasks", tasks || []);
      } catch (err) {
        commit("setError", (err && err.message) || "加载任务列表失败");
      } finally {
        commit("setLoading", false);
      }
    },

    async openTask({ commit, dispatch }, taskId) {
      commit("setError", "");
      commit("setCurrentVersion", null);
      commit("setCurrentReplay", null);
      await dispatch("startPolling", taskId);
    },

    async startPolling({ state, commit, dispatch }, taskId) {
      // 同一任务已在轮询则不重复启动，避免创建第二个 Timer。
      if (state.pollingTaskId === taskId && state.pollTimer != null) return;
      if (state.pollTimer != null) {
        clearTimeout(state.pollTimer);
        commit("setPollTimer", null);
      }
      commit("setPollingTaskId", taskId);
      await dispatch("fetchOnce", taskId);
    },

    async fetchOnce({ commit, dispatch, rootState }, taskId) {
      try {
        const task = await agentApi.getTask(taskId, rootState.user.token);
        // 运行中详情不得缓存隐藏集指标，终态允许保留。
        if (!isTerminal(task.status) && task.hiddenEvaluation != null) {
          const safe = { ...task };
          delete safe.hiddenEvaluation;
          commit("setCurrentTask", safe);
        } else {
          commit("setCurrentTask", task);
        }
        if (isTerminal(task.status)) {
          commit("setPollTimer", null);
          commit("setPollingTaskId", null);
          return;
        }
        const timer = setTimeout(() => {
          dispatch("fetchOnce", taskId);
        }, 1000);
        commit("setPollTimer", timer);
      } catch (err) {
        commit("setError", (err && err.message) || "查询任务失败");
        commit("setPollTimer", null);
        commit("setPollingTaskId", null);
      }
    },

    stopPolling({ state, commit }) {
      if (state.pollTimer != null) {
        clearTimeout(state.pollTimer);
      }
      commit("setPollTimer", null);
      commit("setPollingTaskId", null);
    },

    async cancelTask({ commit, dispatch, rootState }, taskId) {
      commit("setError", "");
      try {
        await agentApi.cancelTask(taskId, rootState.user.token);
      } catch (err) {
        commit("setError", (err && err.message) || "取消任务失败");
      }
      // 强制停止旧轮询再拉取一次，确保刷新到取消后的状态。
      await dispatch("stopPolling");
      await dispatch("startPolling", taskId);
    },

    async loadVersion({ commit, rootState }, versionId) {
      commit("setError", "");
      try {
        const version = await agentApi.getVersion(versionId, rootState.user.token);
        commit("setCurrentVersion", version);
        return version;
      } catch (err) {
        commit("setError", (err && err.message) || "加载版本失败");
        return null;
      }
    },

    async loadReplay({ commit, rootState }, runId) {
      commit("setError", "");
      try {
        const replay = await agentApi.getReplay(runId, rootState.user.token);
        commit("setCurrentReplay", replay);
        return replay;
      } catch (err) {
        commit("setError", (err && err.message) || "加载录像失败");
        return null;
      }
    },

    async saveVersion({ commit, rootState }, { versionId, payload }) {
      commit("setError", "");
      try {
        return await agentApi.saveVersion(versionId, payload, rootState.user.token);
      } catch (err) {
        commit("setError", (err && err.message) || "保存 Bot 失败");
        return null;
      }
    },
  },
};
