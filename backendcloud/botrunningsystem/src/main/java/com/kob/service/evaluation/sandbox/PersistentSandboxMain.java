package com.kob.service.evaluation.sandbox;

import org.joor.Reflect;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static com.kob.service.evaluation.protocol.SandboxProtocol.ERROR;
import static com.kob.service.evaluation.protocol.SandboxProtocol.MOVE;
import static com.kob.service.evaluation.protocol.SandboxProtocol.READY;
import static com.kob.service.evaluation.protocol.SandboxProtocol.RESULT;
import static com.kob.service.evaluation.protocol.SandboxProtocol.SOURCE;
import static com.kob.service.evaluation.protocol.SandboxProtocol.STOP;

/**
 * 持久沙箱子进程入口（阶段 2 任务 2）。
 *
 * <p>流程：
 * <ol>
 *   <li>保存原始 System.out 作为协议输出流。</li>
 *   <li>从 stdin 读取 {@code SOURCE\t<base64>}。</li>
 *   <li>使用 jOOR 编译 {@code com.kob.test.Bot}。</li>
 *   <li>安装 SecurityManager：禁网络、文件写/删/执行、读 application.properties、getenv。</li>
 *   <li>创建实例并验证 {@code Integer nextMove(String input)}。</li>
 *   <li>输出 READY。</li>
 *   <li>循环读取 MOVE/STOP；MOVE 时用受限流替换 System.out 再调用 nextMove。</li>
 *   <li>输出 {@code RESULT\t<dir>\t<nanos>} 或 {@code ERROR\t<code>\t<base64脱敏消息>}。</li>
 * </ol>
 *
 * <p>完整异常栈只写 stderr，协议 stdout 只输出协议行。
 */
public class PersistentSandboxMain {

    private static final Base64.Decoder DECODER = Base64.getDecoder();
    private static final Base64.Encoder ENCODER = Base64.getEncoder();

    public static void main(String[] args) {
        long outputLimit = parseOutputLimit(args);
        PrintStream protocolOut = System.out;
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

        try {
            // 1-2. 读取源码
            String sourceLine = stdin.readLine();
            if (sourceLine == null || !sourceLine.startsWith(SOURCE + "\t")) {
                writeError(protocolOut, SandboxErrorCode.PROTOCOL_ERROR, "缺少 SOURCE 行");
                return;
            }
            String sourceCode = new String(
                    DECODER.decode(sourceLine.substring(SOURCE.length() + 1)), StandardCharsets.UTF_8);

            // 3. 编译（安装 SM 之前）。用唯一 uid 重命名候选类，避免与 classpath 上
            //    已存在的样本 com.kob.test.Bot 碰撞而被父 ClassLoader 遮蔽。
            String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String renamed = sourceCode.replaceFirst("class\\s+Bot\\b", "class Bot" + uid);
            Class<?> botClass;
            try {
                botClass = Reflect.compile("com.kob.test.Bot" + uid, renamed).type();
            } catch (Throwable t) {
                writeError(protocolOut, SandboxErrorCode.COMPILE_FAILED, sanitize(t));
                return;
            }

            // 4. 安装 SecurityManager
            installSecurityManager();

            // 5. 创建实例并验证 nextMove
            Method nextMove;
            Object bot;
            try {
                nextMove = botClass.getMethod("nextMove", String.class);
                bot = botClass.getDeclaredConstructor().newInstance();
            } catch (Throwable t) {
                writeError(protocolOut, SandboxErrorCode.COMPILE_FAILED, sanitize(t));
                return;
            }

            // 6. 就绪
            protocolOut.println(READY);
            protocolOut.flush();

            // 7. 循环
            String line;
            while ((line = stdin.readLine()) != null) {
                if (STOP.equals(line)) {
                    return;
                }
                if (!line.startsWith(MOVE + "\t")) {
                    writeError(protocolOut, SandboxErrorCode.PROTOCOL_ERROR, "未知协议行: " + sanitize(line));
                    continue;
                }
                String snapshot = new String(
                        DECODER.decode(line.substring(MOVE.length() + 1)), StandardCharsets.UTF_8);
                handleMove(protocolOut, nextMove, bot, snapshot, outputLimit);
            }
        } catch (Throwable t) {
            t.printStackTrace();
            writeError(protocolOut, SandboxErrorCode.PROTOCOL_ERROR, sanitize(t));
        }
    }

    private static void handleMove(PrintStream protocolOut, Method nextMove, Object bot,
                                   String snapshot, long outputLimit) {
        long start = System.nanoTime();
        BoundedOutputStream bounded = new BoundedOutputStream(outputLimit);
        PrintStream botOut = new PrintStream(bounded, true);
        PrintStream savedOut = System.out;
        System.setOut(botOut);
        try {
            Integer direction = (Integer) nextMove.invoke(bot, snapshot);
            long duration = System.nanoTime() - start;
            if (direction == null) {
                writeError(protocolOut, SandboxErrorCode.INVALID_MOVE, "nextMove 返回 null");
                return;
            }
            protocolOut.println(RESULT + "\t" + direction + "\t" + duration);
            protocolOut.flush();
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                cause.printStackTrace();
            }
            writeError(protocolOut, mapCause(cause), sanitize(cause));
        } catch (Throwable t) {
            t.printStackTrace();
            writeError(protocolOut, SandboxErrorCode.SANDBOX_VIOLATION, sanitize(t));
        } finally {
            System.setOut(savedOut);
        }
    }

    private static SandboxErrorCode mapCause(Throwable cause) {
        if (cause instanceof OutputLimitExceededException) {
            return SandboxErrorCode.OUTPUT_LIMIT;
        }
        return SandboxErrorCode.SANDBOX_VIOLATION;
    }

    private static void installSecurityManager() {
        System.setSecurityManager(new SecurityManager() {
            @Override
            public void checkPermission(java.security.Permission perm) {
                if (perm instanceof java.net.SocketPermission) {
                    throw new SecurityException("网络访问被禁止");
                }
                if (perm instanceof java.io.FilePermission) {
                    String actions = perm.getActions();
                    String name = perm.getName();
                    if (actions != null && (actions.contains("write")
                            || actions.contains("delete")
                            || actions.contains("execute"))) {
                        throw new SecurityException("文件写/删除/执行被禁止");
                    }
                    if (actions != null && actions.contains("read")
                            && name != null && name.contains("application.properties")) {
                        throw new SecurityException("读取应用配置被禁止");
                    }
                }
                if (perm instanceof java.lang.RuntimePermission) {
                    String n = perm.getName();
                    if ("exec".equals(n)) {
                        throw new SecurityException("执行外部命令被禁止");
                    }
                    if ("getenv".equals(n) || (n != null && n.startsWith("getenv."))) {
                        throw new SecurityException("读取环境变量被禁止");
                    }
                }
            }

            @Override
            public void checkPermission(java.security.Permission perm, Object context) {
                checkPermission(perm);
            }
        });
    }

    private static void writeError(PrintStream out, SandboxErrorCode code, String message) {
        String encoded = ENCODER.encodeToString(
                (message == null ? "" : message).getBytes(StandardCharsets.UTF_8));
        out.println(ERROR + "\t" + code.name() + "\t" + encoded);
        out.flush();
    }

    private static String sanitize(Throwable t) {
        if (t == null) return "未知异常";
        return sanitize(t.getClass().getName() + ": " + String.valueOf(t.getMessage()));
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\r\\n\\t]", " ");
    }

    private static long parseOutputLimit(String[] args) {
        if (args != null && args.length >= 1) {
            try {
                return Long.parseLong(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }
        return 8192L;
    }

    /** 受限输出流：累计字节数超限时抛 {@link OutputLimitExceededException}（PrintStream 不会吞掉 RuntimeException）。 */
    private static final class BoundedOutputStream extends OutputStream {
        private final long limit;
        private long count;

        BoundedOutputStream(long limit) {
            this.limit = limit;
        }

        @Override
        public void write(int b) {
            check(1);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) {
            check(len);
            count += len;
        }

        private void check(long inc) {
            if (count + inc > limit) {
                throw new OutputLimitExceededException();
            }
        }
    }

    private static final class OutputLimitExceededException extends RuntimeException {
        OutputLimitExceededException() {
            super("输出超出上限");
        }
    }
}
