package com.kob.service.impl.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnJre;
import org.junit.jupiter.api.condition.JRE;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JooprBotExecutor 测试（审计任务 2.2）。
 *
 * <p>addUid 为纯逻辑，全 JDK 可测。
 * <p>jOOR 编译执行仅在 JDK 8 下验证（joor-java-8 0.9.14 与 Java 17 模块系统不兼容，
 * 故用 @EnabledOnJre(JAVA_8) 门控；JDK 17 下跳过，不阻塞默认测试）。
 */
class JooprBotExecutorTest {

    private final JooprBotExecutor executor = new JooprBotExecutor();

    /** 一个返回固定方向 1 的 Bot（implements Supplier<Integer>，addUid 后可编译）。 */
    private static final String BOT_RETURNING_1 =
            "package com.kob.test;\n" +
            "public class Bot implements java.util.function.Supplier<Integer> {\n" +
            "    public Integer get() { return 1; }\n" +
            "}";

    @AfterEach
    void cleanup() {
        new File("input.txt").delete();  // 执行器会写 input.txt，测试后清理
    }

    @Test
    void addUid_insertsUidBeforeImplementsClause() {
        String code = "package com.kob.test;\n"
                + "public class Bot implements java.util.function.Supplier<Integer> { }";
        String result = JooprBotExecutor.addUid(code, "Ab12cd34");
        assertTrue(result.contains("public class BotAb12cd34 implements java.util.function.Supplier<Integer>"),
                "uid 应插入到类名与 implements 之间");
    }

    /** 仅 JDK 8 运行：验证 jOOR 编译执行契约（JDK 17 下跳过）。 */
    @Test
    @EnabledOnJre(JRE.JAVA_8)
    void execute_compilesAndReturnsBotDirection_onJdk8() throws Exception {
        Integer direction = executor.execute(BOT_RETURNING_1, "anyinput");
        assertEquals(Integer.valueOf(1), direction);
    }
}
