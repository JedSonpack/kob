package com.kob.service.evaluation.sandbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 持久 Bot 子进程工厂，从配置读取运行 JDK、单步超时与输出上限。
 *
 * <p>配置项：
 * <ul>
 *   <li>{@code kob.bot.evaluation.java-home}：运行 JDK（默认 JDK 1.8）。</li>
 *   <li>{@code kob.bot.evaluation.step-timeout-ms}：单步决策超时（默认 200ms）。</li>
 *   <li>{@code kob.bot.evaluation.output-limit-bytes}：候选输出上限（默认 8192 字节）。</li>
 * </ul>
 */
@Component
public class PersistentBotProcessFactory {

    private final String javaHome;
    private final long stepTimeoutMs;
    private final long outputLimitBytes;

    public PersistentBotProcessFactory(
            @Value("${kob.bot.evaluation.java-home:/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home}")
                    String javaHome,
            @Value("${kob.bot.evaluation.step-timeout-ms:200}")
                    long stepTimeoutMs,
            @Value("${kob.bot.evaluation.output-limit-bytes:8192}")
                    long outputLimitBytes) {
        this.javaHome = javaHome;
        this.stepTimeoutMs = stepTimeoutMs;
        this.outputLimitBytes = outputLimitBytes;
    }

    public PersistentBotProcess create() {
        return new ProcessPersistentBotProcess(javaHome, stepTimeoutMs, outputLimitBytes);
    }
}
