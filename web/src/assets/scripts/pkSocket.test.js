import { describe, test, expect, vi } from "vitest";
import { createGameEventDispatcher, safeSend } from "./pkSocket";

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

describe("createGameEventDispatcher", () => {
  test("gameObject 未就绪时保留 move，并在就绪后应用", () => {
    let game = null;
    const snakes = [
      { set_direction: vi.fn(), direction: -1, status: "idle" },
      { set_direction: vi.fn(), direction: -1, status: "idle" },
    ];
    const updateLoser = vi.fn();
    const dispatcher = createGameEventDispatcher(() => game, updateLoser);

    expect(
      dispatcher.dispatch({
        event: "move",
        a_direction: 1,
        b_direction: 3,
      })
    ).toBe(false);
    expect(snakes[0].set_direction).not.toHaveBeenCalled();

    game = { snakes };
    dispatcher.flush();

    expect(snakes[0].set_direction).toHaveBeenCalledWith(1);
    expect(snakes[1].set_direction).toHaveBeenCalledWith(3);
  });

  test("gameObject 未就绪时保留 result，并在就绪后更新胜负", () => {
    let game = null;
    const snakes = [
      { direction: -1, status: "idle" },
      { direction: -1, status: "idle" },
    ];
    const updateLoser = vi.fn();
    const dispatcher = createGameEventDispatcher(() => game, updateLoser);

    dispatcher.dispatch({ event: "result", loser: "B" });
    game = { snakes };
    dispatcher.flush();

    expect(snakes[0].status).toBe("idle");
    expect(snakes[1].status).toBe("die");
    expect(updateLoser).toHaveBeenCalledWith("B");
  });

  test("动画暂停期间收到 result 时快进积压回合并立即结算", () => {
    const snakes = [
      { set_direction: vi.fn(), direction: -1, status: "idle" },
      { set_direction: vi.fn(), direction: -1, status: "idle" },
    ];
    const updateLoser = vi.fn();
    const game = {
      snakes,
      finishCurrentMove: vi.fn(() => {
        snakes.forEach((snake) => {
          snake.direction = -1;
          snake.status = "idle";
        });
      }),
      renderCurrentState: vi.fn(),
    };
    const dispatcher = createGameEventDispatcher(() => game, updateLoser);

    dispatcher.dispatch({ event: "move", a_direction: 0, b_direction: 2 });
    snakes[0].direction = 0;
    snakes[1].direction = 2;
    dispatcher.dispatch({ event: "move", a_direction: 1, b_direction: 3 });
    dispatcher.dispatch({ event: "result", loser: "A" });

    expect(snakes[0].set_direction).toHaveBeenCalledTimes(2);
    expect(snakes[0].set_direction).toHaveBeenLastCalledWith(1);
    expect(game.finishCurrentMove).toHaveBeenCalledTimes(2);
    expect(game.renderCurrentState).toHaveBeenCalledOnce();
    expect(snakes[0].status).toBe("die");
    expect(updateLoser).toHaveBeenCalledWith("A");
  });
});
