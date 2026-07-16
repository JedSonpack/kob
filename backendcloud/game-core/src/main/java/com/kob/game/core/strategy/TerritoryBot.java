package com.kob.game.core.strategy;

import com.kob.game.core.GameSnapshot;
import com.kob.game.core.Position;
import com.kob.game.core.Strategy;

/**
 * 领土基准策略：对每个安全方向，从己方下一步蛇头与对手蛇头执行多源距离 BFS，
 * 选择领土差值（更接近己方的空格数 - 更接近对手的空格数）最大的方向；
 * 同分依次比较己方可达空间、方向数字；无安全方向返回 0。
 */
public final class TerritoryBot implements Strategy {

    @Override
    public int nextMove(GameSnapshot snapshot) {
        int bestDirection = 0;
        int bestDiff = Integer.MIN_VALUE;
        int bestSpace = -1;
        for (int direction = 0; direction < 4; direction++) {
            if (!BotSupport.isSafe(snapshot, direction)) continue;
            Position next = BotSupport.nextHead(snapshot, direction);
            int diff = BotSupport.territoryDifference(snapshot, next);
            int space = BotSupport.floodFillCount(snapshot, next);
            if (diff > bestDiff
                    || (diff == bestDiff && space > bestSpace)) {
                bestDiff = diff;
                bestSpace = space;
                bestDirection = direction;
            }
        }
        return bestDirection;
    }
}
