import { describe, test, expect, vi } from "vitest";
import { safeSend } from "./pkSocket";

describe("safeSend（审计 3.3）", () => {
  test("socket OPEN 时发送并返回 true", () => {
    const socket = { readyState: WebSocket.OPEN, send: vi.fn() };
    expect(safeSend(socket, "msg")).toBe(true);
    expect(socket.send).toHaveBeenCalledWith("msg");
  });

  test("socket 非 OPEN 时不发送、返回 false", () => {
    const socket = { readyState: WebSocket.CONNECTING, send: vi.fn() };
    expect(safeSend(socket, "msg")).toBe(false);
    expect(socket.send).not.toHaveBeenCalled();
  });

  test("socket 为 null 时不抛异常、返回 false", () => {
    expect(safeSend(null, "msg")).toBe(false);
  });
});
