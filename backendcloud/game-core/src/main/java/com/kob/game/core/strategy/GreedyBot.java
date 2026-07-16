package com.kob.game.core.strategy;

import com.kob.game.core.GameSnapshot;
import com.kob.game.core.Position;
import com.kob.game.core.Strategy;

/**
 * 贪心基准策略：对每个安全方向执行 BFS，选择下一步可达空格数量最大的方向；
 * 同分选择方向数字较小者；无安全方向返回 0。
 */
public final class GreedyBot implements Strategy {

    @Override
    public int nextMove(GameSnapshot snapshot) {
        int bestDirection = 0;
        int bestSpace = -1;
        for (int direction = 0; direction < 4; direction++) {
            if (!BotSupport.isSafe(snapshot, direction)) continue;
            Position next = BotSupport.nextHead(snapshot, direction);
            int space = BotSupport.floodFillCount(snapshot, next);
            if (space > bestSpace) {
                bestSpace = space;
                bestDirection = direction;
            }
        }
        return bestDirection;
    }
}
