import { describe, test, expect, beforeEach, vi } from "vitest";
import { AC_GAME_OBJECTS, AcGameObject } from "./AcGameObject";
import { Snake } from "./Snake";

describe("AcGameObject destroy（审计 3.1）", () => {
  beforeEach(() => {
    AC_GAME_OBJECTS.length = 0; // 清空全局对象表
  });

  test("destroy 仅移除自身，不影响其后对象（原 splice(i) 会删其后全部）", () => {
    const a = new AcGameObject();
    const b = new AcGameObject();
    const c = new AcGameObject();
    expect(AC_GAME_OBJECTS).toHaveLength(3);

    b.destroy(); // 删除中间的 b

    expect(AC_GAME_OBJECTS).toHaveLength(2);
    expect(AC_GAME_OBJECTS).toContain(a);
    expect(AC_GAME_OBJECTS).toContain(c);
    expect(AC_GAME_OBJECTS).not.toContain(b);
  });

  test("destroy 调用 on_destroy 钩子", () => {
    const obj = new AcGameObject();
    obj.on_destroy = vi.fn();
    obj.destroy();
    expect(obj.on_destroy).toHaveBeenCalled();
  });
});

describe("Snake 后台恢复", () => {
  test("超大 timedelta 不会越过当前目标格", () => {
    const gamemap = { L: 10, ctx: {} };
    const snake = new Snake({ id: 0, color: "blue", r: 5, c: 5 }, gamemap);
    snake.set_direction(1);
    snake.next_step();
    snake.timedelta = 10_000;

    snake.update_move();

    expect(snake.cells[0].r).toBe(5);
    expect(snake.cells[0].c).toBe(6);
    expect(snake.status).toBe("idle");
  });
});
