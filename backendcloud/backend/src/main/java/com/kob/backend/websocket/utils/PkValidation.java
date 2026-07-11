package com.kob.backend.websocket.utils;

import com.kob.backend.pojo.Bot;

/**
 * PK 输入校验（审计任务 1.2）。
 *
 * <p>纯函数，便于单元测试；由 {@link Game} / {@code WebSocketServer} 调用。
 * 解决审计 P0-3（Bot 归属未校验）与 P1-1（移动方向未限制在 0-3）。
 */
public final class PkValidation {

    private PkValidation() {}

    /** 方向合法范围 0-3（上/右/下/左），对应 Game 的 dx/dy 数组下标。 */
    public static boolean isValidDirection(Integer direction) {
        return direction != null && direction >= 0 && direction <= 3;
    }

    /**
     * bot_id == -1 表示真人操作，始终允许；
     * 否则要求 bot 存在且属于该用户（防止运行他人 Bot，审计 P0-3）。
     */
    public static boolean isBotAllowed(Integer botId, Bot bot, Integer userId) {
        if (botId == null || botId < -1) return false;
        if (botId == -1) return true;
        return bot != null && userId != null && userId.equals(bot.getUserId());
    }
}
