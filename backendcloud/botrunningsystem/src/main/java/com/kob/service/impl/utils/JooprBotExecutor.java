package com.kob.service.impl.utils;

import org.joor.Reflect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 基于 jOOR 的 Bot 执行器（审计任务 2.2）。
 * 沿用原 Consumer 的编译执行方式：在当前 JVM 内编译运行 Bot 代码。
 * 注意：在主 JVM 内执行用户代码存在安全风险，仅作为默认/回退实现；沙箱实现见 2.3。
 */
@Component
@ConditionalOnProperty(name = "kob.bot.executor", havingValue = "joor", matchIfMissing = true)
public class JooprBotExecutor implements BotExecutor {

    @Override
    public Integer execute(String botCode, String input) throws Exception {
        UUID uuid = UUID.randomUUID();
        String uid = uuid.toString().substring(0, 8);

        Supplier<Integer> botInterface = Reflect.compile(
                "com.kob.test.Bot" + uid,
                addUid(botCode, uid)
        ).create().get();

        File file = new File("input.txt");
        try (PrintWriter fout = new PrintWriter(file)) {
            fout.println(input);  // 结果放到 input.txt 中
            fout.flush();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        return botInterface.get();
    }

    static String addUid(String code, String uid) {  // package-private static，便于测试
        int k = code.indexOf(" implements java.util.function.Supplier<Integer>");
        return code.substring(0, k) + uid + code.substring(k);
    }
}
