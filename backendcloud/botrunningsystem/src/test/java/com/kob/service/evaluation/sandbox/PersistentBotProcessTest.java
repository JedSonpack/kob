package com.kob.service.evaluation.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnJre;
import org.junit.jupiter.api.condition.JRE;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 持久沙箱进程测试（阶段 2 任务 2）。
 *
 * <p>仅 JDK 8：子进程用 JDK 8 跑 jOOR 编译候选 Bot。
 */
class PersistentBotProcessTest {

    private static final String JAVA_HOME =
            "/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home";

    private static final String COUNTING_BOT =
            "package com.kob.test;\n" +
            "public class Bot {\n" +
            "  private int calls = 0;\n" +
            "  public Integer nextMove(String input) { return calls++ % 4; }\n" +
            "}";

    private static final String COMPILE_ERROR_BOT =
            "package com.kob.test;\n" +
            "public class Bot {\n" +
            "  public Integer nextMove(String input) { return 0; \n" +  // 缺少右括号
            "}";

    @Test
    @EnabledOnJre(JRE.JAVA_8)
    void persistentProcessCompilesOnceAndReusesAcrossMoves() throws Exception {
        Path workDir;
        ProcessPersistentBotProcess process =
                new ProcessPersistentBotProcess(JAVA_HOME, 1000L, 8192L);
        try {
            process.start(COUNTING_BOT);
            assertEquals(0, process.decide("first").getDirection());
            assertEquals(1, process.decide("second").getDirection());
            assertEquals(2, process.decide("third").getDirection());
            assertTrue(process.isAlive());
            workDir = process.getWorkDir();
            assertTrue(Files.exists(workDir));
        } finally {
            process.close();
        }
        // 关闭后临时目录被递归删除
        assertFalse(Files.exists(workDir));
    }

    @Test
    @EnabledOnJre(JRE.JAVA_8)
    void compileErrorFailsStartWithCompileFailed() {
        ProcessPersistentBotProcess process =
                new ProcessPersistentBotProcess(JAVA_HOME, 1000L, 8192L);
        try {
            SandboxExecutionException ex = assertThrows(SandboxExecutionException.class,
                    () -> process.start(COMPILE_ERROR_BOT));
            assertEquals(SandboxErrorCode.COMPILE_FAILED, ex.getCode());
        } finally {
            process.close();
        }
    }

    @Test
    @EnabledOnJre(JRE.JAVA_8)
    void closeIsIdempotent() {
        ProcessPersistentBotProcess process =
                new ProcessPersistentBotProcess(JAVA_HOME, 1000L, 8192L);
        process.close();
        process.close();  // 重复关闭不抛异常
    }
}
