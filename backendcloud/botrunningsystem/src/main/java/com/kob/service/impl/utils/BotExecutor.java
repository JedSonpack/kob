package com.kob.service.impl.utils;

/**
 * Bot 执行器接口（审计任务 2.2）。
 * 在现有执行方式外建立边界，便于后续切换为沙箱实现（2.3）。
 */
public interface BotExecutor {

    /**
     * 编译并执行 Bot 代码，返回移动方向（0-3）。
     *
     * @param botCode Java 源码（含 implements Supplier&lt;Integer&gt;）
     * @param input   当前局面字符串
     */
    Integer execute(String botCode, String input) throws Exception;
}
