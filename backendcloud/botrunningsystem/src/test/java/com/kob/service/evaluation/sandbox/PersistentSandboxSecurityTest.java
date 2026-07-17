package com.kob.service.evaluation.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnJre;
import org.junit.jupiter.api.condition.JRE;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 持久沙箱安全与资源上限测试（阶段 2 任务 3）。
 *
 * <p>每种恶意/越权 Bot 必须被拒绝为标准错误码，且关闭后无残留子进程与临时目录。
 * 仅 JDK 8：子进程用 JDK 8 跑 jOOR。
 */
class PersistentSandboxSecurityTest {

    private static final String JAVA_HOME =
            "/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home";

    private static final String INFINITE_LOOP_BOT =
            "package com.kob.test;\n" +
            "public class Bot {\n" +
            "  public Integer nextMove(String input) { while (true) {} }\n" +
            "}";

    private static final String SOCKET_BOT =
            "package com.kob.test;\n" +
            "public class Bot {\n" +
            "  public Integer nextMove(String input) throws Exception {\n" +
            "    new java.net.Socket(\"127.0.0.1\", 3000);\n" +
            "    return 0;\n" +
            "  }\n" +
            "}";

    private static final String FILE_WRITE_BOT =
            "package com.kob.test;\n" +
            "public class Bot {\n" +
            "  public Integer nextMove(String input) throws Exception {\n" +
            "    new java.io.FileOutputStream(\"owned.txt\").write(1);\n" +
            "    return 0;\n" +
            "  }\n" +
            "}";

    private static final String EXEC_BOT =
            "package com.kob.test;\n" +
            "public class Bot {\n" +
            "  public Integer nextMove(String input) throws Exception {\n" +
            "    Runtime.getRuntime().exec(\"echo owned\");\n" +
            "    return 0;\n" +
            "  }\n" +
            "}";

    private static final String OUTPUT_LIMIT_BOT =
            "package com.kob.test;\n" +
            "public class Bot {\n" +
            "  public Integer nextMove(String input) {\n" +
            "    for (int i = 0; i < 100000; i++) System.out.print(\"x\");\n" +
            "    return 0;\n" +
            "  }\n" +
            "}";

    private static final String TRUSTED_READ_BOT =
            "package com.kob.test;\n" +
            "public class Bot {\n" +
            "  public Integer nextMove(String input) throws Exception {\n" +
            "    java.io.InputStream stream =\n" +
            "        getClass().getClassLoader().getResourceAsStream(\"application.properties\");\n" +
            "    if (stream != null) return 1;\n" +
            "    return System.getenv(\"KOB_BOT_HIDDEN_SEEDS\") == null ? 0 : 2;\n" +
            "  }\n" +
            "}";

    @Test
    @EnabledOnJre(JRE.JAVA_8)
    void infiniteLoopTimesOut() {
        assertViolation(INFINITE_LOOP_BOT, SandboxErrorCode.STEP_TIMEOUT);
    }

    @Test
    @EnabledOnJre(JRE.JAVA_8)
    void socketAccessBlocked() {
        assertViolation(SOCKET_BOT, SandboxErrorCode.SANDBOX_VIOLATION);
    }

    @Test
    @EnabledOnJre(JRE.JAVA_8)
    void fileWriteBlocked() {
        assertViolation(FILE_WRITE_BOT, SandboxErrorCode.SANDBOX_VIOLATION);
    }

    @Test
    @EnabledOnJre(JRE.JAVA_8)
    void execBlocked() {
        assertViolation(EXEC_BOT, SandboxErrorCode.SANDBOX_VIOLATION);
    }

    @Test
    @EnabledOnJre(JRE.JAVA_8)
    void outputLimitExceeded() {
        assertViolation(OUTPUT_LIMIT_BOT, SandboxErrorCode.OUTPUT_LIMIT);
    }

    @Test
    @EnabledOnJre(JRE.JAVA_8)
    void trustedResourceAndEnvBlocked() {
        assertViolation(TRUSTED_READ_BOT, SandboxErrorCode.SANDBOX_VIOLATION);
    }

    private void assertViolation(String bot, SandboxErrorCode expectedCode) {
        ProcessPersistentBotProcess process =
                new ProcessPersistentBotProcess(JAVA_HOME, 500L, 8192L);
        Path workDir;
        try {
            process.start(bot);
            SandboxExecutionException ex = assertThrows(SandboxExecutionException.class,
                    () -> process.decide("any"));
            assertEquals(expectedCode, ex.getCode());
            workDir = process.getWorkDir();
        } finally {
            process.close();
        }
        // 关闭后无残留子进程与临时目录
        assertFalse(process.isAlive());
        assertFalse(Files.exists(workDir));
    }
}
