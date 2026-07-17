const test = require("node:test");
const assert = require("node:assert/strict");

const { taskTimeoutMs } = require("./agent_lab_playwright");

test("任务超时默认保持 180 秒", () => {
  assert.equal(taskTimeoutMs(undefined), 180_000);
  assert.equal(taskTimeoutMs(""), 180_000);
});

test("真实实验可以显式延长任务超时", () => {
  assert.equal(taskTimeoutMs("600000"), 600_000);
});

test("非法或过短的超时回退到 180 秒", () => {
  assert.equal(taskTimeoutMs("invalid"), 180_000);
  assert.equal(taskTimeoutMs("179999"), 180_000);
});
