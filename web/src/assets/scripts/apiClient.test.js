import { describe, test, expect } from "vitest";
import { buildAjaxConfig, BASE_URL } from "./apiClient";

describe("buildAjaxConfig（审计 5.2）", () => {
  test("拼接 BASE_URL 与 Bearer 认证头", () => {
    const cfg = buildAjaxConfig({
      url: "/api/ranklist/getlist/",
      data: { page: 1 },
      type: "get",
      token: "abc123",
    });
    expect(cfg.url).toBe(BASE_URL + "/api/ranklist/getlist/");
    expect(cfg.type).toBe("get");
    expect(cfg.data).toEqual({ page: 1 });
    expect(cfg.headers.Authorization).toBe("Bearer abc123");
  });

  test("token 为 null 时 Bearer 后为空串（不抛异常）", () => {
    const cfg = buildAjaxConfig({ url: "/x", data: {}, type: "get", token: null });
    expect(cfg.headers.Authorization).toBe("Bearer ");
  });
});
