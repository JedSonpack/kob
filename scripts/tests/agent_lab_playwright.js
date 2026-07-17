const assert = require("node:assert/strict");
const { chromium } = require("playwright");

const WEB_URL = process.env.KOB_WEB_URL || "http://127.0.0.1:8080";
const API_URL = process.env.KOB_API_URL || "http://127.0.0.1:3000";
const PASSWORD = "Playwright123";
const GOAL =
  "尽量扩大可活动区域，避免进入狭窄通道；在多个安全方向中优先保留后续选择。";
const TERMINAL = new Set(["COMPLETED", "FAILED", "CANCELLED"]);
const REQUIRED_PHASES = ["GENERATING", "COMPILING", "EVALUATING", "VALIDATING"];

function suffix() {
  return `${Date.now()}_${Math.random().toString(16).slice(2, 8)}`;
}

async function postForm(path, data, token) {
  const response = await fetch(`${API_URL}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: new URLSearchParams(data),
  });
  assert.ok(response.ok, `${path} returned ${response.status}`);
  return response.json();
}

async function requestJson(method, path, data, token) {
  const response = await fetch(`${API_URL}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: data == null ? undefined : JSON.stringify(data),
  });
  assert.ok(response.ok, `${method} ${path} returned ${response.status}`);
  return response.json();
}

async function createUser() {
  const name = `agent_${suffix()}`;
  const reg = await postForm("/api/user/account/register/", {
    username: name,
    password: PASSWORD,
    confirmedPassword: PASSWORD,
  });
  assert.equal(reg.error_message, "success");
  const login = await postForm("/api/user/account/token/", {
    username: name,
    password: PASSWORD,
  });
  assert.equal(login.error_message, "success");
  return { name, token: login.token };
}

function assertNoHiddenIfRunning(detail) {
  if (!detail || TERMINAL.has(detail.status)) return;
  assert.equal(
    detail.hiddenEvaluation,
    undefined,
    `运行中详情（status=${detail.status}）不得包含 hiddenEvaluation`
  );
}

(async () => {
  const user = await createUser();
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();

  let taskId = null;
  let detail = null;
  let responseAssertionError = null;
  const pendingDetailResponses = new Set();
  // 监听 UI 轮询的任务详情响应，运行中断言不存在 hiddenEvaluation。
  page.on("response", (resp) => {
    const url = resp.url();
    if (
      resp.request().method() === "GET" &&
      /\/api\/agent\/tasks\/(\d+)\/$/.test(url) &&
      !url.includes("/cancel/")
    ) {
      const check = resp
        .json()
        .then(assertNoHiddenIfRunning)
        .catch((e) => {
          if (e.name === "AssertionError" && responseAssertionError == null) {
            responseAssertionError = e;
          }
        })
        .finally(() => pendingDetailResponses.delete(check));
      pendingDetailResponses.add(check);
    }
  });

  async function assertListenedDetailResponses() {
    await Promise.all([...pendingDetailResponses]);
    if (responseAssertionError) throw responseAssertionError;
  }

  try {
    // 1-2. 登录并打开 Agent Lab
    await page.goto(`${WEB_URL}/user/account/login/`);
    await page.locator("#username").fill(user.name);
    await page.locator("#password").fill(PASSWORD);
    await page.locator('form button[type="submit"]').click();
    await page.waitForURL(/\/pk\//, { timeout: 10_000 });
    await page.getByRole("link", { name: "Agent Lab" }).click();
    await page.waitForURL(/\/agent-lab\//, { timeout: 10_000 });
    await page.locator(".agent-task-form textarea").waitFor({ timeout: 10_000 });

    // 捕获创建任务响应拿 taskId
    page.on("response", async (resp) => {
      if (
        resp.request().method() === "POST" &&
        resp.url().endsWith("/api/agent/tasks/")
      ) {
        try {
          const data = await resp.json();
          if (data && data.task_id != null) taskId = data.task_id;
        } catch (_) {}
      }
    });

    // 3-4. 输入策略目标、选 3 轮、开始进化
    await page.locator(".agent-task-form textarea").fill(GOAL);
    await page.locator(".agent-task-form select").selectOption("3");
    await page.getByRole("button", { name: "开始进化" }).click();

    // 等待 taskId
    const taskIdDeadline = Date.now() + 10_000;
    while (taskId == null && Date.now() < taskIdDeadline) {
      await page.waitForTimeout(200);
    }
    assert.ok(taskId != null, "未能从创建任务响应获取 taskId");

    // 5-6. 轮询任务详情，运行中断言无 hiddenEvaluation，等待终态（180s）
    const deadline = Date.now() + 180_000;
    while (Date.now() < deadline) {
      detail = await requestJson("GET", `/api/agent/tasks/${taskId}/`, null, user.token);
      assertNoHiddenIfRunning(detail);
      await assertListenedDetailResponses();
      if (TERMINAL.has(detail.status)) break;
      await page.waitForTimeout(1000);
    }
    assert.ok(detail && TERMINAL.has(detail.status), `任务未在 180s 内进入终态：${detail && detail.status}`);
    assert.equal(
      detail.status,
      "COMPLETED",
      `任务应成功完成，实际 ${detail.status}（${detail.errorCode || "UNKNOWN_ERROR"}）`
    );

    // 7. 展示 1～3 个公开完成版本
    const versions = detail.versions || [];
    assert.ok(versions.length >= 1 && versions.length <= 3, `展示版本数应在 1..3，实际 ${versions.length}`);
    for (const version of versions) {
      assert.equal(version.compileStatus, "SUCCESS", `V${version.iteration} 编译未成功`);
      assert.ok(version.publicGameCount != null, `V${version.iteration} 缺少公开评测`);
    }
    await page.locator(".agent-task-status .badge", { hasText: "已完成" }).waitFor({
      timeout: 10_000,
    });
    const versionSection = page.getByRole("heading", { name: "版本对比" }).locator("..");
    const displayedRows = versionSection.locator("tbody tr");
    assert.equal(await displayedRows.count(), versions.length, "页面展示版本数与任务详情不一致");

    // 8. 恰好一个版本标记为最佳
    const accepted = versions.filter((v) => v.accepted);
    assert.equal(accepted.length, 1, `应恰好一个最佳版本，实际 ${accepted.length}`);
    assert.ok(detail.bestVersionId != null, "bestVersionId 应非空");
    assert.equal(
      await versionSection.locator(".badge", { hasText: "最佳" }).count(),
      1,
      "页面应恰好标记一个最佳版本"
    );

    // 9. Trace 至少包含生成、编译、公开评测、隐藏验证
    const phases = new Set((detail.steps || []).map((s) => s.phase));
    for (const phase of REQUIRED_PHASES) {
      assert.ok(phases.has(phase), `Trace 缺少阶段 ${phase}，已有 ${[...phases].join(",")}`);
    }

    // 10. 打开一条 Replay
    assert.ok((detail.representativeRuns || []).length > 0, "终态应有代表性录像");
    await page.locator(".agent-section >> text=代表性录像").waitFor({ timeout: 10_000 });
    const replayButtons = page.locator(".agent-lab button", { hasText: /vs/ });
    await replayButtons.first().waitFor({ timeout: 10_000 });
    await replayButtons.first().click();
    await page.getByRole("button", { name: "播放录像" }).click();
    await page.locator(".agent-replay-canvas canvas").waitFor({ timeout: 15_000 });

    // 11. 保存最佳版本为正式 Bot
    await page.getByRole("button", { name: "保存为我的Bot" }).first().click();
    await page.getByRole("button", { name: "已保存" }).waitFor({ timeout: 10_000 });

    // 12. 校验保存结果出现在 Bot 列表
    const bots = await requestJson("GET", "/api/user/bot/getlist/", null, user.token);
    const saved = (Array.isArray(bots) ? bots : []).find((b) =>
      (b.title || "").startsWith("Agent V")
    );
    assert.ok(saved, "Bot 列表中未找到保存的 Agent 版本");

    console.log(
      `PASS: agent-lab 任务 #${taskId} 完成，${versions.length} 个版本，` +
        `Trace 阶段 ${[...phases].join("/")}，保存 Bot「${saved.title}」`
    );
  } catch (error) {
    // 失败时输出任务详情摘要与截图，不输出 Token
    try {
      const summary =
        detail != null
          ? JSON.stringify({
              taskId,
              status: detail.status,
              versions: (detail.versions || []).length,
              steps: (detail.steps || []).map((s) => `${s.phase}:${s.status}`),
              errorCode: detail.errorCode,
            })
          : `taskId=${taskId}`;
      console.error("任务详情摘要：", summary);
    } catch (_) {}
    try {
      const shot = `/tmp/agent-lab-fail-${suffix()}.png`;
      await page.screenshot({ path: shot, fullPage: true });
      console.error("截图：", shot);
    } catch (_) {}
    throw error;
  } finally {
    await context.close();
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
