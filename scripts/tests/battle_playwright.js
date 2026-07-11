const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const { chromium } = require("playwright");

const WEB_URL = process.env.KOB_WEB_URL || "http://127.0.0.1:8080";
const API_URL = process.env.KOB_API_URL || "http://127.0.0.1:3000";
const PASSWORD = "Playwright123";
const BOT_CODE = `package com.kob.test;
import java.io.File;
import java.util.Scanner;

public class Bot implements java.util.function.Supplier<Integer> {
    public Integer get() {
        try (Scanner scanner = new Scanner(new File("input.txt"))) {
            String[] parts = scanner.nextLine().split("#");
            return Integer.parseInt(parts[1]) > 6 ? 0 : 2;
        } catch (Exception e) {
            return 0;
        }
    }
}`;

async function postForm(path, data, token) {
  const response = await fetch(`${API_URL}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: new URLSearchParams(data),
  });
  assert.equal(response.ok, true, `${path} returned ${response.status}`);
  return response.json();
}

async function createUser(name, withBot) {
  const registration = await postForm("/api/user/account/register/", {
    username: name,
    password: PASSWORD,
    confirmedPassword: PASSWORD,
  });
  assert.equal(registration.error_message, "success");
  const login = await postForm("/api/user/account/token/", {
    username: name,
    password: PASSWORD,
  });
  assert.equal(login.error_message, "success");

  if (withBot) {
    const bot = await postForm(
      "/api/user/bot/add/",
      { title: "playwright-bot", description: "E2E bot", content: BOT_CODE },
      login.token
    );
    assert.equal(bot.error_message, "success");
  }
  return { name, token: login.token };
}

async function openPlayer(browser, user) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  await page.addInitScript(() => {
    window.__battleEvents = [];
    const NativeWebSocket = window.WebSocket;
    window.WebSocket = class TrackingWebSocket extends NativeWebSocket {
      constructor(...args) {
        super(...args);
        this.addEventListener("message", (message) => {
          try {
            window.__battleEvents.push(JSON.parse(message.data));
          } catch (_) {
            // Ignore non-protocol frames.
          }
        });
      }
    };
  });
  await page.goto(`${WEB_URL}/user/account/login/`);
  await page.locator("#username").fill(user.name);
  await page.locator("#password").fill(PASSWORD);
  await page.locator('form button[type="submit"]').click();
  await page.waitForURL(/\/pk\//, { timeout: 10_000 });
  await page.getByRole("button", { name: "开始匹配" }).waitFor();
  return { context, page };
}

function digest(buffer) {
  return crypto.createHash("sha256").update(buffer).digest("hex");
}

async function chooseBot(page) {
  await page.locator("select.form-select option").nth(1).waitFor({ state: "attached" });
  const value = await page.locator("select.form-select option").nth(1).getAttribute("value");
  assert.ok(value && value !== "-1", "Bot option was not loaded");
  await page.locator("select.form-select").selectOption(value);
}

async function driveHuman(page, forceCrash) {
  const isA = await page.locator(".user-color1").isVisible();
  const safeDirection = isA ? "ArrowUp" : "ArrowDown";
  const crashDirection = isA ? "ArrowDown" : "ArrowUp";
  await page.keyboard.press(forceCrash ? crashDirection : safeDirection);
}

async function runScenario(browser, label, leftBot, rightBot) {
  const suffix = `${Date.now()}_${Math.random().toString(16).slice(2, 8)}`;
  const [leftUser, rightUser] = await Promise.all([
    createUser(`pw_${label}_a_${suffix}`, leftBot),
    createUser(`pw_${label}_b_${suffix}`, rightBot),
  ]);
  const [left, right] = await Promise.all([
    openPlayer(browser, leftUser),
    openPlayer(browser, rightUser),
  ]);

  try {
    if (leftBot) await chooseBot(left.page);
    if (rightBot) await chooseBot(right.page);
    await Promise.all([
      left.page.getByRole("button", { name: "开始匹配" }).click(),
      right.page.getByRole("button", { name: "开始匹配" }).click(),
    ]);
    await Promise.all([
      left.page.locator("canvas").waitFor({ timeout: 10_000 }),
      right.page.locator("canvas").waitFor({ timeout: 10_000 }),
    ]);

    const initial = await Promise.all([
      left.page.locator("canvas").screenshot(),
      right.page.locator("canvas").screenshot(),
    ]);

    const deadline = Date.now() + 18_000;
    let inputRound = 0;
    while (Date.now() < deadline) {
      const forceCrash = inputRound >= 6;
      if (!leftBot) await driveHuman(left.page, forceCrash);
      if (!rightBot) await driveHuman(right.page, forceCrash);
      const finished = await Promise.all([
        left.page.locator(".result-board-text").isVisible(),
        right.page.locator(".result-board-text").isVisible(),
      ]);
      if (finished[0] && finished[1]) break;
      inputRound++;
      await left.page.waitForTimeout(350);
    }

    const resultVisibility = await Promise.all([
      left.page.locator(".result-board-text").isVisible(),
      right.page.locator(".result-board-text").isVisible(),
    ]);
    if (!resultVisibility.every(Boolean)) {
      const diagnostics = await Promise.all([
        left.page.evaluate(() => window.__battleEvents.map((event) => event.event)),
        right.page.evaluate(() => window.__battleEvents.map((event) => event.event)),
      ]);
      throw new Error(`${label}: no synchronized result; events=${JSON.stringify(diagnostics)}`);
    }
    const final = await Promise.all([
      left.page.locator("canvas").screenshot(),
      right.page.locator("canvas").screenshot(),
    ]);
    const [leftEvents, rightEvents] = await Promise.all([
      left.page.evaluate(() => window.__battleEvents),
      right.page.evaluate(() => window.__battleEvents),
    ]);
    const leftMoves = leftEvents.filter((event) => event.event === "move");
    const rightMoves = rightEvents.filter((event) => event.event === "move");
    const leftResult = leftEvents.find((event) => event.event === "result");
    const rightResult = rightEvents.find((event) => event.event === "result");

    assert.ok(leftMoves.length > 0, `${label}: left received no moves`);
    assert.deepEqual(leftMoves, rightMoves, `${label}: player move streams differ`);
    assert.deepEqual(leftResult, rightResult, `${label}: player results differ`);
    assert.notEqual(digest(initial[0]), digest(final[0]), `${label}: left canvas did not change`);
    assert.notEqual(digest(initial[1]), digest(final[1]), `${label}: right canvas did not change`);

    return { label, moves: leftMoves.length, loser: leftResult.loser };
  } finally {
    await Promise.all([left.context.close(), right.context.close()]);
  }
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  try {
    const results = [];
    results.push(await runScenario(browser, "human-human", false, false));
    results.push(await runScenario(browser, "human-bot", false, true));
    results.push(await runScenario(browser, "bot-bot", true, true));
    for (const result of results) {
      console.log(`${result.label}: ${result.moves} moves, loser=${result.loser}`);
    }
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
