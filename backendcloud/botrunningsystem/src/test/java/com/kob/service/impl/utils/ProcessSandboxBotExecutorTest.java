package com.kob.service.impl.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ProcessSandboxBotExecutor 测试（审计任务 2.3）。
 *
 * <p>功能开关：默认 joor；kob.bot.executor=sandbox 切换为沙箱。
 * <p>端到端执行仅在 JDK 8 验证（沙箱子进程用 JDK 8 跑 jOOR）。
 */
class ProcessSandboxBotExecutorTest {

    private static final String BOT_RETURNING_1 =
            "package com.kob.test;\n" +
            "public class Bot implements java.util.function.Supplier<Integer> {\n" +
            "    public Integer get() { return 1; }\n" +
            "}";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(JooprBotExecutor.class, ProcessSandboxBotExecutor.class);

    @Test
    void defaultExecutor_isJoopr() {
        runner.run(context -> {
            assertTrue(context.containsBean("jooprBotExecutor"));
            assertFalse(context.containsBean("processSandboxBotExecutor"));
        });
    }

    @Test
    void sandboxExecutor_selectedWhenConfigured() {
        runner.withPropertyValues("kob.bot.executor=sandbox")
                .run(context -> {
                    assertTrue(context.containsBean("processSandboxBotExecutor"));
                    assertFalse(context.containsBean("jooprBotExecutor"));
                });
    }

    /** 仅 JDK 8：端到端验证沙箱子进程编译执行 Bot 返回方向。 */
    @Test
    @EnabledOnJre(JRE.JAVA_8)
    void execute_sandboxReturnsBotDirection_onJdk8() {
        runner.withPropertyValues("kob.bot.executor=sandbox")
                .run(context -> {
                    BotExecutor executor = context.getBean(BotExecutor.class);
                    Integer direction = executor.execute(BOT_RETURNING_1, "anyinput");
                    assertEquals(Integer.valueOf(1), direction);
                });
    }
}
