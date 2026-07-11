package com.kob.backend.websocket.utils;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 游戏规则测试（审计任务 0.2）。
 * 锁定蛇增长（checkTailIncreasing）与碰撞判定（checkValid）规则。
 * 方向规则见 PkValidationTest；积分规则见 GameResultServiceImplTest。
 */
class GameRulesTest {

    // ---------- 蛇增长 ----------

    @Test
    void checkTailIncreasing_growsFirstTenSteps() {
        for (int step = 1; step <= 10; step++) {
            assertTrue(GameRules.checkTailIncreasing(step), "step " + step + " 应增长");
        }
    }

    @Test
    void checkTailIncreasing_afterTenEveryThreeSteps() {
        // 11、12 不增；13 增；14、15 不增；16 增
        assertFalse(GameRules.checkTailIncreasing(11));
        assertFalse(GameRules.checkTailIncreasing(12));
        assertTrue(GameRules.checkTailIncreasing(13));
        assertFalse(GameRules.checkTailIncreasing(14));
        assertTrue(GameRules.checkTailIncreasing(16));
    }

    // ---------- 碰撞判定 ----------

    private static int[][] emptyMap(int rows, int cols) {
        return new int[rows][cols];  // 全 0（可走）
    }

    @Test
    void checkValid_headOnWall_invalid() {
        int[][] g = emptyMap(5, 5);
        g[2][3] = 1;  // 墙
        List<Cell> self = Arrays.asList(new Cell(0, 0), new Cell(2, 3));  // 蛇头撞墙
        List<Cell> opp = Arrays.asList(new Cell(4, 4), new Cell(4, 3));
        assertFalse(GameRules.checkValid(self, opp, g));
    }

    @Test
    void checkValid_headOnSelfBody_invalid() {
        int[][] g = emptyMap(5, 5);
        // 蛇身含 (1,1)，蛇头回到 (1,1)
        List<Cell> self = Arrays.asList(new Cell(1, 1), new Cell(1, 2), new Cell(1, 1));
        List<Cell> opp = Arrays.asList(new Cell(4, 4), new Cell(4, 3));
        assertFalse(GameRules.checkValid(self, opp, g));
    }

    @Test
    void checkValid_headOnOpponentBody_invalid() {
        int[][] g = emptyMap(5, 5);
        List<Cell> self = Arrays.asList(new Cell(0, 0), new Cell(1, 1));  // 蛇头 (1,1)
        List<Cell> opp = Arrays.asList(new Cell(1, 1), new Cell(2, 2));  // 对手身体含 (1,1)
        assertFalse(GameRules.checkValid(self, opp, g));
    }

    @Test
    void checkValid_headOnEmpty_valid() {
        int[][] g = emptyMap(5, 5);
        List<Cell> self = Arrays.asList(new Cell(0, 0), new Cell(0, 1));
        List<Cell> opp = Arrays.asList(new Cell(4, 4), new Cell(4, 3));
        assertTrue(GameRules.checkValid(self, opp, g));
    }
}
