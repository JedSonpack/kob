package com.kob.service.impl.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JooprBotExecutor 测试（审计任务 2.2）。
 *
 * <p>jOOR（joor-java-8 0.9.14）在 Java 17 测试 JVM 下与模块系统不兼容，编译路径无法单测
 * （生产运行于 Java 8 不受影响；2.3 的进程沙箱将提供可测的执行实现）。
 * 此处覆盖可测的 addUid 纯逻辑（Bot 类名加 uid 去重）。
 */
class JooprBotExecutorTest {

    @Test
    void addUid_insertsUidBeforeImplementsClause() {
        String code = "package com.kob.test;\n"
                + "public class Bot implements java.util.function.Supplier<Integer> { }";
        String result = JooprBotExecutor.addUid(code, "Ab12cd34");
        assertTrue(result.contains("public class BotAb12cd34 implements java.util.function.Supplier<Integer>"),
                "uid 应插入到类名与 implements 之间");
    }
}
