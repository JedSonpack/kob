package com.kob.service.impl.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BotPool 并发测试（审计任务 0.3 / 1.3）。
 *
 * <p>覆盖：队列消费排空（功能护栏）、consume 期间锁已释放生产者不阻塞（锁泄漏复现）。
 * 通过覆盖 consume 为记录式/慢速实现，避开真实 Bot 编译执行。
 */
class BotPoolTest {

    /** 可观测的 BotPool：覆盖 consume 为记录式/可慢速，便于并发测试。 */
    static class RecordingBotPool extends BotPool {
        final List<Integer> consumed = Collections.synchronizedList(new ArrayList<>());
        volatile CountDownLatch consumeStarted;
        volatile long consumeSleepMs = 0;

        @Override
        protected void consume(Bot bot) {
            consumed.add(bot.getUserId());
            if (consumeStarted != null) consumeStarted.countDown();
            if (consumeSleepMs > 0) {
                try {
                    Thread.sleep(consumeSleepMs);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private RecordingBotPool pool;

    @AfterEach
    void tearDown() throws InterruptedException {
        if (pool != null) {
            pool.interrupt();
            pool.join(1000);
        }
    }

    /** 审计 0.3：多批任务能被依次消费、队列排空。 */
    @Test
    void addBot_thenConsumed_queueDrains() throws Exception {
        pool = new RecordingBotPool();
        pool.start();
        for (int i = 0; i < 5; i++) {
            pool.addBot(i, "code", "input", "g1", 0);
        }

        long deadline = System.currentTimeMillis() + 5000;
        while (pool.consumed.size() < 5 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }

        assertEquals(5, pool.consumed.size());
    }

    /**
     * 审计 1.3：consume 期间锁应已释放，生产者 addBot 不应阻塞。
     * 修复前：await() 返回后未 unlock，consume 期间持锁 -> addBot 阻塞 ~consumeSleepMs。
     * 修复后：consume 在锁外执行 -> addBot 立即返回。
     */
    @Test
    void addBot_doesNotBlockDuringConsume() throws Exception {
        pool = new RecordingBotPool();
        pool.consumeSleepMs = 1000;
        pool.consumeStarted = new CountDownLatch(1);
        pool.start();

        pool.addBot(1, "code", "input", "g1", 0);  // 触发慢消费
        assertTrue(pool.consumeStarted.await(2, TimeUnit.SECONDS), "consume 应启动");

        // consume 正在 sleep；若锁未释放，此 addBot 会被阻塞到 consume 结束
        long t0 = System.currentTimeMillis();
        pool.addBot(2, "code", "input", "g1", 0);
        long elapsed = System.currentTimeMillis() - t0;

        assertTrue(elapsed < 300, "生产者不应在 consume 期间阻塞，实际耗时 " + elapsed + "ms");
    }
}
