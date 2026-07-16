package com.kob.backend.websocket.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁定在线 Game 委托 game-core 后的地图确定性（审计任务 0.3 / 阶段 1 任务 6）。
 */
class GameMapCompatibilityTest {

    @Test
    void sameSeedProducesSameMap() {
        Game first = new Game(13, 14, 20, 1, null, 2, null, 99L);
        Game second = new Game(13, 14, 20, 1, null, 2, null, 99L);
        first.createMap();
        second.createMap();

        int[][] a = first.getG();
        int[][] b = second.getG();
        assertEquals(a.length, b.length);
        for (int i = 0; i < a.length; i++) {
            assertEquals(a[i].length, b[i].length, "row " + i + " 长度不一致");
            for (int j = 0; j < a[i].length; j++) {
                assertEquals(a[i][j], b[i][j], "cell (" + i + "," + j + ") 不一致");
            }
        }
    }

    @Test
    void differentSeedsUsuallyProduceDifferentMaps() {
        Game first = new Game(13, 14, 20, 1, null, 2, null, 99L);
        Game second = new Game(13, 14, 20, 1, null, 2, null, 2026071601L);
        first.createMap();
        second.createMap();
        assertNotEquals(java.util.Arrays.deepToString(first.getG()),
                java.util.Arrays.deepToString(second.getG()));
    }

    @Test
    void mapHasBoundaryWallsAndClearBirthPoints() {
        Game game = new Game(13, 14, 20, 1, null, 2, null, 99L);
        game.createMap();
        int[][] g = game.getG();
        int rows = g.length, cols = g[0].length;
        for (int c = 0; c < cols; c++) {
            assertEquals(1, g[0][c]);
            assertEquals(1, g[rows - 1][c]);
        }
        for (int r = 0; r < rows; r++) {
            assertEquals(1, g[r][0]);
            assertEquals(1, g[r][cols - 1]);
        }
        // 出生点 A=(rows-2,1)、B=(1,cols-2) 必须可用
        assertEquals(0, g[rows - 2][1]);
        assertEquals(0, g[1][cols - 2]);
        assertTrue(game.getPlayerA().getSx() == rows - 2 && game.getPlayerA().getSy() == 1);
        assertTrue(game.getPlayerB().getSx() == 1 && game.getPlayerB().getSy() == cols - 2);
    }
}
