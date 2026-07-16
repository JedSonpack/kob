package com.kob.game.core;

import java.util.ArrayDeque;
import java.util.Random;

/**
 * 固定种子对称地图生成器。
 *
 * <p>生成规则：
 * <ol>
 *   <li>四周边界为墙。</li>
 *   <li>使用 {@code new Random(seed)}，每次同时放置 (r,c) 与中心对称位置。</li>
 *   <li>跳过出生点、已有墙和同一中心点。</li>
 *   <li>最多尝试 1000 次完整生成，每次用队列 BFS 检查两个出生点连通。</li>
 *   <li>全部失败时抛 {@link IllegalStateException}（消息含 seed）。</li>
 *   <li>返回深拷贝，不暴露内部数组。</li>
 * </ol>
 */
public final class DeterministicMapGenerator {

    private static final int[] DX = {-1, 0, 1, 0};
    private static final int[] DY = {0, 1, 0, -1};
    private static final int MAX_ATTEMPTS = 1000;

    public int[][] generate(GameConfig config) {
        Random random = new Random(config.getSeed());
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int[][] map = freshMapWithBoundary(config);
            placeInnerWalls(map, config, random);
            Position birthA = new Position(config.getRows() - 2, 1);
            Position birthB = new Position(1, config.getCols() - 2);
            if (isConnected(map, birthA, birthB)) {
                return deepCopy(map);
            }
        }
        throw new IllegalStateException("无法为 seed=" + config.getSeed() + " 生成连通地图");
    }

    public boolean isConnected(int[][] map, Position a, Position b) {
        int rows = map.length, cols = map[0].length;
        if (map[a.getRow()][a.getCol()] == 1) return false;
        if (map[b.getRow()][b.getCol()] == 1) return false;
        boolean[][] visited = new boolean[rows][cols];
        ArrayDeque<Position> queue = new ArrayDeque<>();
        queue.add(a);
        visited[a.getRow()][a.getCol()] = true;
        while (!queue.isEmpty()) {
            Position cur = queue.poll();
            if (cur.equals(b)) return true;
            for (int i = 0; i < 4; i++) {
                int nr = cur.getRow() + DX[i];
                int nc = cur.getCol() + DY[i];
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols
                        && !visited[nr][nc] && map[nr][nc] == 0) {
                    visited[nr][nc] = true;
                    queue.add(new Position(nr, nc));
                }
            }
        }
        return false;
    }

    private int[][] freshMapWithBoundary(GameConfig config) {
        int rows = config.getRows(), cols = config.getCols();
        int[][] map = new int[rows][cols];
        for (int c = 0; c < cols; c++) {
            map[0][c] = 1;
            map[rows - 1][c] = 1;
        }
        for (int r = 0; r < rows; r++) {
            map[r][0] = 1;
            map[r][cols - 1] = 1;
        }
        return map;
    }

    private void placeInnerWalls(int[][] map, GameConfig config, Random random) {
        int rows = config.getRows(), cols = config.getCols();
        int pairs = config.getInnerWalls() / 2;
        int birthARow = rows - 2, birthACol = 1;
        int birthBRow = 1, birthBCol = cols - 2;
        for (int i = 0; i < pairs; i++) {
            for (int j = 0; j < 1000; j++) {
                int r = random.nextInt(rows);
                int c = random.nextInt(cols);
                int sr = rows - 1 - r;
                int sc = cols - 1 - c;
                if (isBirthPoint(r, c, birthARow, birthACol, birthBRow, birthBCol)) continue;
                if (isBirthPoint(sr, sc, birthARow, birthACol, birthBRow, birthBCol)) continue;
                if (map[r][c] == 1 || map[sr][sc] == 1) continue;
                if (r == sr && c == sc) continue;  // 同一中心点
                map[r][c] = 1;
                map[sr][sc] = 1;
                break;
            }
        }
    }

    private boolean isBirthPoint(int r, int c, int ar, int ac, int br, int bc) {
        return (r == ar && c == ac) || (r == br && c == bc);
    }

    private int[][] deepCopy(int[][] map) {
        int[][] copy = new int[map.length][];
        for (int i = 0; i < map.length; i++) {
            copy[i] = map[i].clone();
        }
        return copy;
    }
}
