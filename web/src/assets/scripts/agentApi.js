import $ from "jquery";

/**
 * Agent Lab 独立 API Client（阶段 4 任务 1）。
 *
 * <p>与现有 apiClient.js 分离：Token 由 Vuex Action 从 rootState.user.token 传入，
 * 避免出现 store/index.js -> agent.js -> agentApi.js -> store/index.js 的循环依赖。
 * 路径与阶段 3 后端 Controller 完全一致，相对路径走 vue.config.js 的 devServer 代理。
 */

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
      error: (jqXHR) => reject(extractError(jqXHR)),
    });
  });
}

/** 从 jQuery 错误响应中提取用户可理解的摘要，不暴露服务栈或模型原文。 */
export function extractError(jqXHR) {
  if (!jqXHR) return { message: "请求失败", status: 0 };
  const body = jqXHR.responseJSON || {};
  return {
    message: body.error_message || jqXHR.statusText || "请求失败",
    status: jqXHR.status || 0,
  };
}

export const agentApi = {
  createTask(payload, token) {
    return request({
      method: "POST",
      url: "/api/agent/tasks/",
      data: payload,
      token,
    });
  },
  listTasks(token) {
    return request({ method: "GET", url: "/api/agent/tasks/", token });
  },
  getTask(taskId, token) {
    return request({
      method: "GET",
      url: "/api/agent/tasks/" + taskId + "/",
      token,
    });
  },
  cancelTask(taskId, token) {
    return request({
      method: "POST",
      url: "/api/agent/tasks/" + taskId + "/cancel/",
      token,
    });
  },
  getVersion(versionId, token) {
    return request({
      method: "GET",
      url: "/api/agent/versions/" + versionId + "/",
      token,
    });
  },
  getReplay(runId, token) {
    return request({
      method: "GET",
      url: "/api/agent/evaluations/" + runId + "/replay/",
      token,
    });
  },
  saveVersion(versionId, payload, token) {
    return request({
      method: "POST",
      url: "/api/agent/versions/" + versionId + "/save-bot/",
      data: payload,
      token,
    });
  },
};

export default agentApi;
