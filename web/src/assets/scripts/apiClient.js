import $ from "jquery";
import store from "../../store/index";

/**
 * 前端统一 API Client（审计任务 5.2）。
 *
 * <p>集中 API 基址、认证头与错误处理，替代散落在各页面的 $.ajax 直调。
 */
// 本地开发走 vue.config.js 的 devServer 代理（相对路径），无需后端配置 CORS。
export const BASE_URL = "";

/** 构造带认证头的 ajax 配置（纯函数，便于测试）。 */
export function buildAjaxConfig({ url, data, type, token }) {
  return {
    url: BASE_URL + url,
    data,
    type,
    headers: { Authorization: "Bearer " + (token != null ? token : "") },
  };
}

function request({ url, data, type, success, error }) {
  const config = buildAjaxConfig({
    url,
    data,
    type,
    token: store.state.user.token,
  });
  config.success = (resp) => success && success(resp);
  config.error = (resp) => {
    console.log("API error:", url, resp);
    error && error(resp);
  };
  $.ajax(config);
}

export const api = {
  get: (url, data, success, error) =>
    request({ url, data, type: "get", success, error }),
  post: (url, data, success, error) =>
    request({ url, data, type: "post", success, error }),
};
