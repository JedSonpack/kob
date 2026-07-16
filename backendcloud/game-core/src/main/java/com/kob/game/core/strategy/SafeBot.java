package com.kob.game.core.strategy;

import com.kob.game.core.Strategy;

/**
 * 保守基准策略：按 0、1、2、3 顺序返回第一个不会立即碰撞的方向；无安全方向返回 0。
 */
public final class SafeBot implements Strategy {

    @Override
    public int nextMove(com.kob.game.core.GameSnapshot snapshot) {
        for (int direction = 0; direction < 4; direction++) {
            if (BotSupport.isSafe(snapshot, direction)) {
                return direction;
            }
        }
        return 0;
    }
}
