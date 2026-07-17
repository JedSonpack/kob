const WEB_URL = process.env.KOB_WEB_URL || "http://127.0.0.1:8080";
const API_URL = process.env.KOB_API_URL || "http://127.0.0.1:3000";
const PASSWORD = "Playwright123";
const GOAL =
  "尽量扩大可活动区域，避免进入狭窄通道；在多个安全方向中优先保留后续选择。";
const TERMINAL = new Set(["COMPLETED", "FAILED", "CANCELLED"]);
const REQUIRED_PHASES = ["GENERATING", "COMPILING", "EVALUATING", "VALIDATING"];

function taskTimeoutMs(value) {
  if (value == null || value === "") return 180_000;
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 180_000 ? parsed : 180_000;
}

function browserLaunchOptions(executablePath) {
  return executablePath
    ? { headless: true, executablePath }
    : { headless: true };
}

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
  if (!response.ok) fail("API_HTTP_ERROR");
  try {
    return await response.json();
  } catch (_) {
    fail("API_RESPONSE_INVALID");
  }
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
  if (!response.ok) fail("API_HTTP_ERROR");
  try {
    return await response.json();
  } catch (_) {
    fail("API_RESPONSE_INVALID");
  }
}

async function createUser() {
  const name = `agent_${suffix()}`;
  const reg = await postForm("/api/user/account/register/", {
    username: name,
    password: PASSWORD,
    confirmedPassword: PASSWORD,
  });
  ensure(reg && reg.error_message === "success", "REGISTER_FAILED");
  const login = await postForm("/api/user/account/token/", {
    username: name,
    password: PASSWORD,
  });
  ensure(login && login.error_message === "success", "LOGIN_FAILED");
  ensure(typeof login.token === "string" && login.token.length > 0, "LOGIN_FAILED");
  return { name, token: login.token };
}

class SafeE2eError extends Error {
  constructor(code) {
    super(code);
    this.name = "SafeE2eError";
    this.code = code;
  }
}

function fail(code) {
  throw new SafeE2eError(code);
}

function ensure(condition, code) {
  if (!condition) fail(code);
}

function runningDetailFailureCode(detail) {
  if (!detail || typeof detail !== "object" || typeof detail.status !== "string") {
    return "DETAIL_RESPONSE_INVALID";
  }
  if (
    !TERMINAL.has(detail.status) &&
    Object.prototype.hasOwnProperty.call(detail, "hiddenEvaluation")
  ) {
    return "HIDDEN_EVALUATION_EXPOSED";
  }
  return null;
}

function assertNoHiddenIfRunning(detail) {
  const code = runningDetailFailureCode(detail);
  if (code) fail(code);
}

function isValidPublicGameCount(value) {
  return Number.isFinite(value) && Number.isInteger(value) && value > 0;
}

function isAcceptedBestVersion(versions, bestVersionId) {
  if (!Array.isArray(versions) || bestVersionId == null) return false;
  const accepted = versions.filter((version) => version && version.accepted === true);
  return accepted.length === 1 && accepted[0].id === bestVersionId;
}

async function drainPending(pending) {
  while (pending.size > 0) {
    await Promise.allSettled([...pending]);
  }
}

async function runControlledCreateInteraction(waitForResponse, click) {
  const [responseResult, clickResult] = await Promise.allSettled([
    Promise.resolve().then(waitForResponse),
    Promise.resolve().then(click),
  ]);
  if (clickResult.status === "rejected") fail("TASK_CREATE_CLICK_FAILED");
  if (responseResult.status === "rejected") fail("TASK_CREATE_RESPONSE_TIMEOUT");
  return responseResult.value;
}

function isTaskDetailRequest(method, url) {
  if (method !== "GET") return false;
  try {
    return /^\/api\/agent\/tasks\/\d+\/$/.test(new URL(url).pathname);
  } catch (_) {
    return false;
  }
}

function safeSummaryValue(value, fallback) {
  return typeof value === "string" && /^[A-Z][A-Z0-9_]{0,63}$/.test(value)
    ? value
    : fallback;
}

async function runE2e() {
  const { chromium } = require("playwright");
  let browser = null;
  let context = null;
  let page = null;
  let taskId = null;
  let detail = null;
  let listenerFailureCode = null;
  let detailRequestCount = 0;
  const pendingDetailResponses = new Set();

  function recordListenerFailure(code) {
    if (listenerFailureCode == null) listenerFailureCode = code;
  }

  async function drainAndAssertDetailResponses() {
    await drainPending(pendingDetailResponses);
    if (listenerFailureCode) fail(listenerFailureCode);
  }

  try {
    const user = await createUser();
    browser = await chromium.launch(
      browserLaunchOptions(process.env.KOB_PLAYWRIGHT_EXECUTABLE_PATH)
    );
    context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
    page = await context.newPage();

    // 统计实际发出的详情 GET 请求，用于证明终态后 UI 不再轮询。
    page.on("request", (request) => {
      if (isTaskDetailRequest(request.method(), request.url())) {
        detailRequestCount += 1;
      }
    });

    // 监听 UI 的每次任务详情响应；任何 HTTP、JSON 或契约错误都转换为脱敏错误码。
    page.on("response", (resp) => {
      let path;
      try {
        path = new URL(resp.url()).pathname;
      } catch (_) {
        recordListenerFailure("DETAIL_RESPONSE_URL_INVALID");
        return;
      }
      if (
        resp.request().method() !== "GET" ||
        !/^\/api\/agent\/tasks\/\d+\/$/.test(path)
      ) {
        return;
      }
      const check = (async () => {
        if (!resp.ok()) {
          recordListenerFailure("DETAIL_RESPONSE_HTTP_ERROR");
          return;
        }
        let responseDetail;
        try {
          responseDetail = await resp.json();
        } catch (_) {
          recordListenerFailure("DETAIL_RESPONSE_INVALID");
          return;
        }
        const code = runningDetailFailureCode(responseDetail);
        if (code) recordListenerFailure(code);
      })()
        .catch(() => recordListenerFailure("DETAIL_LISTENER_FAILED"))
        .finally(() => pendingDetailResponses.delete(check));
      pendingDetailResponses.add(check);
    });

    // 1-2. 登录并打开 Agent Lab
    await page.goto(`${WEB_URL}/user/account/login/`);
    await page.locator("#username").fill(user.name);
    await page.locator("#password").fill(PASSWORD);
    await page.locator('form button[type="submit"]').click();
    await page.waitForURL(/\/pk\//, { timeout: 10_000 });
    await page.getByRole("link", { name: "Agent Lab" }).click();
    await page.waitForURL(/\/agent-lab\//, { timeout: 10_000 });
    await page.locator(".agent-task-form textarea").waitFor({ timeout: 10_000 });

    // 3-4. 输入策略目标、选 3 轮、开始进化
    await page.locator(".agent-task-form textarea").fill(GOAL);
    await page.locator(".agent-task-form select").selectOption("3");
    const createResponse = await runControlledCreateInteraction(
      () =>
        page.waitForResponse(
          (resp) =>
            resp.request().method() === "POST" &&
            new URL(resp.url()).pathname === "/api/agent/tasks/",
          { timeout: 10_000 }
        ),
      () => page.getByRole("button", { name: "开始进化" }).click()
    );
    ensure(createResponse.ok(), "TASK_CREATE_HTTP_ERROR");
    let createData;
    try {
      createData = await createResponse.json();
    } catch (_) {
      fail("TASK_CREATE_RESPONSE_INVALID");
    }
    taskId = createData && createData.task_id;
    ensure(Number.isInteger(taskId) && taskId > 0, "TASK_ID_INVALID");

    // 5-6. 默认等待 180s；真实模型实验可显式延长，不放宽自动化默认门禁。
    const deadline = Date.now() + taskTimeoutMs(process.env.KOB_AGENT_E2E_TIMEOUT_MS);
    while (Date.now() < deadline) {
      detail = await requestJson("GET", `/api/agent/tasks/${taskId}/`, null, user.token);
      assertNoHiddenIfRunning(detail);
      await drainAndAssertDetailResponses();
      if (TERMINAL.has(detail.status)) break;
      await page.waitForTimeout(1000);
    }
    ensure(detail && TERMINAL.has(detail.status), "TASK_TERMINAL_TIMEOUT");
    ensure(detail.status === "COMPLETED", "TASK_NOT_COMPLETED");

    // 7. 展示 1～3 个公开完成版本
    const versions = Array.isArray(detail.versions) ? detail.versions : [];
    ensure(versions.length >= 1 && versions.length <= 3, "PUBLIC_VERSION_COUNT_INVALID");
    for (const version of versions) {
      ensure(version && version.compileStatus === "SUCCESS", "VERSION_COMPILE_INCOMPLETE");
      ensure(
        isValidPublicGameCount(version.publicGameCount),
        "PUBLIC_EVALUATION_INVALID"
      );
    }
    await page.locator(".agent-task-status .badge", { hasText: "已完成" }).waitFor({
      timeout: 10_000,
    });
    await drainAndAssertDetailResponses();
    const requestCountAtTerminal = detailRequestCount;
    await page.waitForTimeout(1100);
    await drainAndAssertDetailResponses();
    ensure(
      detailRequestCount === requestCountAtTerminal,
      "DETAIL_POLLING_DID_NOT_STOP"
    );
    const versionSection = page.getByRole("heading", { name: "版本对比" }).locator("..");
    const displayedRows = versionSection.locator("tbody tr");
    ensure(
      (await displayedRows.count()) === versions.length,
      "DISPLAYED_VERSION_COUNT_MISMATCH"
    );

    // 8. 恰好一个版本标记为最佳
    ensure(
      isAcceptedBestVersion(versions, detail.bestVersionId),
      "BEST_VERSION_MISMATCH"
    );
    ensure(
      (await versionSection.locator(".badge", { hasText: "最佳" }).count()) === 1,
      "BEST_VERSION_BADGE_INVALID"
    );

    // 9. Trace 至少包含生成、编译、公开评测、隐藏验证
    const phases = new Set(
      (Array.isArray(detail.steps) ? detail.steps : []).map((step) => step && step.phase)
    );
    for (const phase of REQUIRED_PHASES) {
      ensure(phases.has(phase), `TRACE_PHASE_MISSING_${phase}`);
    }

    // 10. 打开一条 Replay
    ensure(
      Array.isArray(detail.representativeRuns) && detail.representativeRuns.length > 0,
      "REPLAY_MISSING"
    );
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
    ensure(saved, "SAVED_BOT_MISSING");

    console.log(
      `PASS: agent-lab 任务 #${taskId} 完成，${versions.length} 个版本，` +
        `Trace 阶段 ${[...phases].join("/")}，保存 Bot「${saved.title}」`
    );
  } catch (error) {
    // 失败时只输出白名单状态、标准错误码和截图路径，不打印任意异常对象。
    await drainPending(pendingDetailResponses);
    const errorCode = safeSummaryValue(
      listenerFailureCode || (error instanceof SafeE2eError ? error.code : null),
      "E2E_UNEXPECTED_FAILURE"
    );
    const status = safeSummaryValue(detail && detail.status, "UNKNOWN");
    console.error("任务详情摘要：", JSON.stringify({ status, errorCode }));
    if (page) {
      const shot = `/tmp/agent-lab-fail-${suffix()}.png`;
      try {
        await page.screenshot({ path: shot, fullPage: true });
        console.error("截图：", shot);
      } catch (_) {
        console.error("截图： unavailable");
      }
    } else {
      console.error("截图： unavailable");
    }
    process.exitCode = 1;
  } finally {
    if (context) await context.close().catch(() => {});
    if (browser) await browser.close().catch(() => {});
  }
}

if (require.main === module) {
  runE2e().catch(() => {
    console.error(
      "任务详情摘要：",
      JSON.stringify({ status: "UNKNOWN", errorCode: "E2E_UNEXPECTED_FAILURE" })
    );
    console.error("截图： unavailable");
    process.exitCode = 1;
  });
}

module.exports = {
  assertNoHiddenIfRunning,
  browserLaunchOptions,
  drainPending,
  isAcceptedBestVersion,
  isTaskDetailRequest,
  isValidPublicGameCount,
  runControlledCreateInteraction,
  runningDetailFailureCode,
  taskTimeoutMs,
};
