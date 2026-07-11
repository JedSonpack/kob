package com.kob.backend.websocket.utils;

import java.util.List;

/**
 * 游戏规则纯函数（审计任务 0.2）。
 *
 * <p>从 Player/Game 抽出蛇增长与碰撞判定逻辑，便于单元测试锁定规则。
 */
public final class GameRules {

    private GameRules() {}

    /**
     * 蛇是否在本回合增长。
     * <p>step 从 1 起计：前 10 回合每回合增长；之后每 3 回合增长一次（step%3==1）。
     */
    public static boolean checkTailIncreasing(int step) {
        if (step <= 10) return true;
        return step % 3 == 1;
    }

    /**
     * 蛇头是否合法：不撞墙、不撞自身身体、不撞对手身体。
     * <p>selfCells/opponentCells 末尾为蛇头；不判定头对头（原规则语义）。
     */
    public static boolean checkValid(List<Cell> selfCells, List<Cell> opponentCells, int[][] g) {
        int n = selfCells.size();
        Cell head = selfCells.get(n - 1);
        if (g[head.x][head.y] == 1) return false;  // 撞墙
        for (int i = 0; i < n - 1; i++) {
            if (selfCells.get(i).x == head.x && selfCells.get(i).y == head.y) return false;  // 撞自身
        }
        for (int i = 0; i < n - 1; i++) {
            if (opponentCells.get(i).x == head.x && opponentCells.get(i).y == head.y) return false;  // 撞对手
        }
        return true;
    }
}
