package com.kob.game.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 游戏规则纯函数：蛇增长、蛇身计算与碰撞判定。
 *
 * <p>语义与在线对战 {@code backend.websocket.utils.GameRules} 完全一致：
 * <ul>
 *   <li>增长：step 从 1 起计，前 10 回合每回合增长；之后 step%3==1 时增长。</li>
 *   <li>碰撞：不判定头对头，对手循环只检查对手身体（排除对手蛇头）。</li>
 * </ul>
 */
public final class CoreGameRules {
    private CoreGameRules() {}

    public static boolean isGrowing(int step) {
        return step <= 10 || step % 3 == 1;
    }

    public static List<Position> cells(SnakeState snake) {
        List<Position> result = new ArrayList<>();
        Position current = snake.getStart();
        result.add(current);
        int step = 0;
        for (Integer direction : snake.getMoves()) {
            current = Direction.move(current, direction);
            result.add(current);
            if (!isGrowing(++step)) {
                result.remove(0);
            }
        }
        return result;
    }

    public static boolean isAlive(
            List<Position> self,
            List<Position> opponent,
            int[][] map
    ) {
        return failureReason(self, opponent, map) == FailureReason.NONE;
    }

    public static FailureReason failureReason(
            List<Position> self,
            List<Position> opponent,
            int[][] map
    ) {
        Position head = self.get(self.size() - 1);
        if (map[head.getRow()][head.getCol()] == 1) return FailureReason.WALL;
        for (int i = 0; i < self.size() - 1; i++) {
            if (self.get(i).equals(head)) return FailureReason.SELF;
        }
        for (int i = 0; i < opponent.size() - 1; i++) {
            if (opponent.get(i).equals(head)) return FailureReason.OPPONENT_BODY;
        }
        return FailureReason.NONE;
    }
}
