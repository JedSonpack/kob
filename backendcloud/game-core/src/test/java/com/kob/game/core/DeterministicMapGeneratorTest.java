package com.kob.game.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicMapGeneratorTest {
    private final DeterministicMapGenerator generator = new DeterministicMapGenerator();
    private static final Position BIRTH_A = new Position(11, 1);
    private static final Position BIRTH_B = new Position(1, 12);

    @Test
    void sameSeedProducesSameMap() {
        GameConfig config = new GameConfig(13, 14, 20, 2026071601L, 1000);
        int[][] first = generator.generate(config);
        int[][] second = generator.generate(config);
        assertArrayEquals(first, second);
    }

    @Test
    void mapIsCenterSymmetric() {
        GameConfig config = new GameConfig(13, 14, 20, 2026071601L, 1000);
        int[][] map = generator.generate(config);
        int rows = map.length, cols = map[0].length;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                assertEquals(map[r][c], map[rows - 1 - r][cols - 1 - c],
                        "不对称: (" + r + "," + c + ")");
            }
        }
    }

    @Test
    void birthPointsAreClearAndConnected() {
        GameConfig config = new GameConfig(13, 14, 20, 2026071601L, 1000);
        int[][] map = generator.generate(config);
        assertEquals(0, map[11][1]);
        assertEquals(0, map[1][12]);
        assertTrue(generator.isConnected(map, BIRTH_A, BIRTH_B));
    }

    @Test
    void differentSeedsProduceDifferentMaps() {
        int[][] a = generator.generate(new GameConfig(13, 14, 20, 2026071601L, 1000));
        int[][] b = generator.generate(new GameConfig(13, 14, 20, 99999999L, 1000));
        boolean differs = false;
        for (int r = 0; r < a.length && !differs; r++) {
            for (int c = 0; c < a[0].length; c++) {
                if (a[r][c] != b[r][c]) {
                    differs = true;
                    break;
                }
            }
        }
        assertTrue(differs, "不同种子应至少有一个单元格不同");
    }

    @Test
    void returnsDefensiveCopy() {
        GameConfig config = new GameConfig(13, 14, 20, 2026071601L, 1000);
        int[][] first = generator.generate(config);
        first[0][0] = 9;
        int[][] second = generator.generate(config);
        assertNotEquals(9, second[0][0]);
    }

    @Test
    void boundaryWallsPresent() {
        int[][] map = generator.generate(new GameConfig(13, 14, 20, 2026071601L, 1000));
        int rows = map.length, cols = map[0].length;
        for (int c = 0; c < cols; c++) {
            assertEquals(1, map[0][c]);
            assertEquals(1, map[rows - 1][c]);
        }
        for (int r = 0; r < rows; r++) {
            assertEquals(1, map[r][0]);
            assertEquals(1, map[r][cols - 1]);
        }
    }
}
