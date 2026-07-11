import { describe, test, expect, vi } from "vitest";
import { populateRecordFromItem } from "./recordHelper";

describe("populateRecordFromItem（审计 3.2）", () => {
  test("将录像 item 写入 store 的各 mutation", () => {
    const store = { commit: vi.fn() };
    const item = {
      record: {
        map: "0".repeat(13 * 14),
        aid: 1,
        asx: 11,
        asy: 1,
        bid: 2,
        bsx: 1,
        bsy: 12,
        asteps: "0123",
        bsteps: "3210",
        loser: "A",
      },
    };

    populateRecordFromItem(store, item);

    const calls = store.commit.mock.calls;
    expect(calls).toContainEqual(["updateIsRecord", true]);
    expect(calls).toContainEqual(["updateRecordLoser", "A"]);
    const gameCall = calls.find((c) => c[0] === "updateGame");
    expect(gameCall[1].a_id).toBe(1);
    expect(gameCall[1].map).toHaveLength(13); // 13 行
    expect(gameCall[1].map[0]).toHaveLength(14); // 14 列
    const stepsCall = calls.find((c) => c[0] === "updateSteps");
    expect(stepsCall[1].a_steps).toBe("0123");
    expect(stepsCall[1].b_steps).toBe("3210");
  });
});
