package com.kob.service.impl.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

/**
 * 进程级沙箱 Bot 执行器（审计任务 2.3）。
 *
 * <p>在独立子进程（默认 JDK 8）中执行用户 Bot，与主服务进程隔离；
 * 超时强杀子进程；input.txt 写入独立临时目录，隔离各次执行（审计 P1-2）。
 * 通过 {@code kob.bot.executor=sandbox} 启用，默认仍为 joor（向后兼容）。
 */
@Component
@ConditionalOnProperty(name = "kob.bot.executor", havingValue = "sandbox")
public class ProcessSandboxBotExecutor implements BotExecutor {

    @Value("${kob.bot.sandbox.java-home:/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home}")
    private String javaHome;

    @Value("${kob.bot.sandbox.timeout-ms:2000}")
    private long timeoutMs;

    @Override
    public Integer execute(String botCode, String input) throws Exception {
        Path workDir = Files.createTempDirectory("kob-sandbox-");
        Process process = null;
        try {
            // 独立临时目录写 input.txt，隔离各次执行（审计 P1-2）
            Files.write(workDir.resolve("input.txt"), input.getBytes());

            String classpath = System.getProperty("java.class.path");
            ProcessBuilder pb = new ProcessBuilder(
                    javaHome + File.separator + "bin" + File.separator + "java",
                    "-cp", classpath,
                    SandboxMain.class.getName()
            ).directory(workDir.toFile());
            pb.redirectErrorStream(true);
            process = pb.start();

            // Bot 源码经 stdin 传入
            try (OutputStream os = process.getOutputStream()) {
                os.write(botCode.getBytes());
                os.flush();
            }

            // 后台读 stdout，避免子进程输出撑爆管道死锁
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            final InputStream processStdout = process.getInputStream();
            Thread reader = new Thread(() -> {
                try {
                    byte[] buf = new byte[1024];
                    int n;
                    while ((n = processStdout.read(buf)) != -1) {
                        out.write(buf, 0, n);
                    }
                } catch (IOException ignored) {
                } finally {
                    try {
                        processStdout.close();
                    } catch (IOException ignored) {
                    }
                }
            });
            reader.setDaemon(true);
            reader.start();

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();  // 超时强杀（审计 P0-1：不可靠中断的替代）
                reader.join(500);
                throw new RuntimeException("Bot 执行超时（>" + timeoutMs + "ms），已终止");
            }
            reader.join(500);

            String output = out.toString().trim();
            return Integer.parseInt(output);
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
            cleanup(workDir);
        }
    }

    private static void cleanup(Path dir) {
        try {
            Files.walk(dir).sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
