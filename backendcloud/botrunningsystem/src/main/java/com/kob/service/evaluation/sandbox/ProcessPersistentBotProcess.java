package com.kob.service.evaluation.sandbox;

import static com.kob.service.evaluation.protocol.SandboxProtocol.ERROR;
import static com.kob.service.evaluation.protocol.SandboxProtocol.MOVE;
import static com.kob.service.evaluation.protocol.SandboxProtocol.READY;
import static com.kob.service.evaluation.protocol.SandboxProtocol.RESULT;
import static com.kob.service.evaluation.protocol.SandboxProtocol.SOURCE;
import static com.kob.service.evaluation.protocol.SandboxProtocol.STOP;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Base64;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 基于进程的持久 Bot 子进程实现（阶段 2 任务 2）。
 *
 * <p>每个版本创建独立 {@code kob-evaluation-*} 临时目录，启动 {@link PersistentSandboxMain}，
 * 清空继承环境只保留运行 JDK 所需变量，单线程 Executor + Future 限时读取协议行，
 * 超时/越权/协议错误调用 {@code destroyForcibly()}，{@link #close()} 幂等并递归清理临时目录。
 */
public class ProcessPersistentBotProcess implements PersistentBotProcess {

    private static final long READY_TIMEOUT_MS = 30_000L;
    private static final long DESTROY_WAIT_MS = 1_000L;
    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private final String javaHome;
    private final long stepTimeoutMs;
    private final long outputLimitBytes;

    private Path workDir;
    private Process process;
    private BufferedWriter stdin;
    private BufferedReader stdout;
    private ExecutorService readExecutor;
    private Thread stderrDrain;
    private volatile boolean closed;

    public ProcessPersistentBotProcess(String javaHome, long stepTimeoutMs, long outputLimitBytes) {
        this.javaHome = javaHome;
        this.stepTimeoutMs = stepTimeoutMs;
        this.outputLimitBytes = outputLimitBytes;
    }

    @Override
    public void start(String sourceCode) {
        if (closed || process != null) {
            throw new SandboxExecutionException(SandboxErrorCode.PROTOCOL_ERROR, "进程已启动或已关闭");
        }
        try {
            workDir = Files.createTempDirectory("kob-evaluation-");
        } catch (IOException e) {
            throw new SandboxExecutionException(SandboxErrorCode.PROTOCOL_ERROR, "无法创建临时目录: " + e.getMessage());
        }

        String classpath = System.getProperty("java.class.path");
        ProcessBuilder pb = new ProcessBuilder(
                javaHome + File.separator + "bin" + File.separator + "java",
                "-cp", classpath,
                PersistentSandboxMain.class.getName(),
                Long.toString(outputLimitBytes)
        ).directory(workDir.toFile());
        // 清空继承环境，只写入运行 JDK 所需变量；不传入数据库、JWT 或 LLM 密钥
        pb.environment().clear();
        pb.environment().put("JAVA_HOME", javaHome);
        pb.environment().put("PATH", javaHome + File.separator + "bin");
        pb.environment().put("TMPDIR", System.getProperty("java.io.tmpdir"));
        pb.environment().put("LANG", "en_US.UTF-8");
        pb.redirectErrorStream(false);

        try {
            process = pb.start();
        } catch (IOException e) {
            cleanupDir();
            throw new SandboxExecutionException(SandboxErrorCode.PROTOCOL_ERROR, "无法启动子进程: " + e.getMessage());
        }
        stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        readExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "kob-eval-reader");
            t.setDaemon(true);
            return t;
        });
        startStderrDrain();

        String b64 = ENCODER.encodeToString(sourceCode.getBytes(StandardCharsets.UTF_8));
        try {
            stdin.write(SOURCE + "\t" + b64);
            stdin.newLine();
            stdin.flush();
        } catch (IOException e) {
            throw new SandboxExecutionException(SandboxErrorCode.PROTOCOL_ERROR, "发送 SOURCE 失败: " + e.getMessage());
        }

        String line = readLine(READY_TIMEOUT_MS);
        if (line == null) {
            throw new SandboxExecutionException(SandboxErrorCode.PROTOCOL_ERROR, "子进程未就绪");
        }
        if (line.startsWith(ERROR + "\t")) {
            throw parseError(line);
        }
        if (!READY.equals(line)) {
            throw new SandboxExecutionException(SandboxErrorCode.PROTOCOL_ERROR, "期望 READY 实际: " + line);
        }
    }

    @Override
    public BotMove decide(String encodedSnapshot) {
        if (closed || process == null) {
            throw new SandboxExecutionException(SandboxErrorCode.PROTOCOL_ERROR, "进程未启动或已关闭");
        }
        String b64 = ENCODER.encodeToString(encodedSnapshot.getBytes(StandardCharsets.UTF_8));
        try {
            stdin.write(MOVE + "\t" + b64);
            stdin.newLine();
            stdin.flush();
        } catch (IOException e) {
            cancel();
            throw new SandboxExecutionException(SandboxErrorCode.PROTOCOL_ERROR, "发送 MOVE 失败: " + e.getMessage());
        }

        String line = readLine(stepTimeoutMs);
        if (line == null) {
            if (process.isAlive()) {
                cancel();
                throw new SandboxExecutionException(SandboxErrorCode.STEP_TIMEOUT,
                        "单步决策超时(>" + stepTimeoutMs + "ms)");
            }
            throw new SandboxExecutionException(SandboxErrorCode.PROTOCOL_ERROR, "子进程意外退出");
        }
        if (line.startsWith(ERROR + "\t")) {
            throw parseError(line);
        }
        String[] parts = line.split("\t", 3);
        if (parts.length < 3 || !RESULT.equals(parts[0])) {
            throw new SandboxExecutionException(SandboxErrorCode.PROTOCOL_ERROR, "协议错误: " + line);
        }
        int direction;
        try {
            direction = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new SandboxExecutionException(SandboxErrorCode.PROTOCOL_ERROR, "方向非数字: " + parts[1]);
        }
        if (direction < 0 || direction > 3) {
            throw new SandboxExecutionException(SandboxErrorCode.INVALID_MOVE, "非法方向: " + direction);
        }
        long nanos;
        try {
            nanos = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            nanos = 0L;
        }
        return new BotMove(direction, nanos);
    }

    @Override
    public boolean isAlive() {
        return process != null && process.isAlive();
    }

    @Override
    public Path getWorkDir() {
        return workDir;
    }

    @Override
    public void cancel() {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            try {
                process.waitFor(DESTROY_WAIT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        cancel();
        try {
            if (stdin != null) {
                stdin.write(STOP);
                stdin.newLine();
                stdin.flush();
            }
        } catch (IOException ignored) {
        }
        if (readExecutor != null) {
            readExecutor.shutdownNow();
        }
        if (stderrDrain != null) {
            stderrDrain.interrupt();
        }
        cleanupDir();
    }

    private String readLine(long timeoutMs) {
        Future<String> future = readExecutor.submit(() -> stdout.readLine());
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            return null;
        }
    }

    private void startStderrDrain() {
        final InputStream err = process.getErrorStream();
        stderrDrain = new Thread(() -> {
            byte[] buf = new byte[1024];
            try {
                while (err.read(buf) != -1) {
                    // 丢弃 stderr，避免管道阻塞；完整栈只写 stderr 供排障
                }
            } catch (IOException ignored) {
            }
        }, "kob-eval-stderr");
        stderrDrain.setDaemon(true);
        stderrDrain.start();
    }

    private SandboxExecutionException parseError(String line) {
        String[] parts = line.split("\t", 3);
        if (parts.length < 2) {
            return new SandboxExecutionException(SandboxErrorCode.PROTOCOL_ERROR, "错误行格式非法: " + line);
        }
        SandboxErrorCode code;
        try {
            code = SandboxErrorCode.valueOf(parts[1]);
        } catch (IllegalArgumentException e) {
            code = SandboxErrorCode.PROTOCOL_ERROR;
        }
        String message = "";
        if (parts.length >= 3) {
            try {
                message = new String(DECODER.decode(parts[2]), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                message = parts[2];
            }
        }
        return new SandboxExecutionException(code, message);
    }

    private void cleanupDir() {
        if (workDir == null) return;
        try {
            Files.walk(workDir).sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
