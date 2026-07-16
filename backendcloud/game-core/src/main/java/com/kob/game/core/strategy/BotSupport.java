package com.kob.game.core.strategy;

import com.kob.game.core.CoreGameRules;
import com.kob.game.core.Direction;
import com.kob.game.core.GameSnapshot;
import com.kob.game.core.Position;
import com.kob.game.core.SnakeState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 基准策略共享的只读局面分析工具。
 *
 * <p>所有方法不修改快照，不使用随机数或系统时间，保证确定性。
 */
final class BotSupport {
    private static final int[] DX = {-1, 0, 1, 0};
    private static final int[] DY = {0, 1, 0, -1};

    private BotSupport() {}

    static List<Position> selfCells(GameSnapshot snapshot) {
        return CoreGameRules.cells(snapshot.getSelf());
    }

    static List<Position> opponentCells(GameSnapshot snapshot) {
        return CoreGameRules.cells(snapshot.getOpponent());
    }

    static Position head(List<Position> cells) {
        return cells.get(cells.size() - 1);
    }

    static boolean inBounds(int[][] map, int row, int col) {
        return row >= 0 && row < map.length && col >= 0 && col < map[0].length;
    }

    /** 方向是否不会立即碰撞：下一格在界内、非墙、非自身身体、非对手身体（排除对手蛇头）。 */
    static boolean isSafe(GameSnapshot snapshot, int direction) {
        if (!Direction.isValid(direction)) return false;
        int[][] map = snapshot.getMap();
        List<Position> self = selfCells(snapshot);
        List<Position> opp = opponentCells(snapshot);
        Position next = Direction.move(head(self), direction);
        if (!inBounds(map, next.getRow(), next.getCol())) return false;
        if (map[next.getRow()][next.getCol()] == 1) return false;
        for (Position cell : self) {
            if (cell.equals(next)) return false;
        }
        for (int i = 0; i < opp.size() - 1; i++) {
            if (opp.get(i).equals(next)) return false;
        }
        return true;
    }

    static Position nextHead(GameSnapshot snapshot, int direction) {
        return Direction.move(head(selfCells(snapshot)), direction);
    }

    /** 从 start 出发可达的空格数量（含 start），自身与对手身体视为阻挡。 */
    static int floodFillCount(GameSnapshot snapshot, Position start) {
        int[][] map = snapshot.getMap();
        Set<Position> blocked = blockedCells(snapshot);
        if (!inBounds(map, start.getRow(), start.getCol())) return 0;
        if (map[start.getRow()][start.getCol()] == 1) return 0;
        if (blocked.contains(start)) return 0;

        Set<Position> visited = new HashSet<>();
        ArrayDeque<Position> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            Position cur = queue.poll();
            for (int i = 0; i < 4; i++) {
                int nr = cur.getRow() + DX[i];
                int nc = cur.getCol() + DY[i];
                Position np = new Position(nr, nc);
                if (inBounds(map, nr, nc) && map[nr][nc] == 0
                        && !blocked.contains(np) && !visited.contains(np)) {
                    visited.add(np);
                    queue.add(np);
                }
            }
        }
        return visited.size();
    }

    /** 领土差值：更接近己方源点的空格数减去更接近对手源点的空格数。 */
    static int territoryDifference(GameSnapshot snapshot, Position mySource) {
        int[][] map = snapshot.getMap();
        Set<Position> blocked = blockedCells(snapshot);
        Position oppSource = head(opponentCells(snapshot));

        int[][] distMe = bfsDistances(map, blocked, mySource);
        int[][] distOpp = bfsDistances(map, blocked, oppSource);

        int mine = 0;
        int theirs = 0;
        for (int r = 0; r < map.length; r++) {
            for (int c = 0; c < map[0].length; c++) {
                if (map[r][c] == 1) continue;
                Position p = new Position(r, c);
                if (blocked.contains(p)) continue;
                int dm = distMe[r][c];
                int dl = distOpp[r][c];
                if (dm < 0 && dl < 0) continue;
                if (dl < 0) {
                    mine++;
                } else if (dm < 0) {
                    theirs++;
                } else if (dm < dl) {
                    mine++;
                } else if (dl < dm) {
                    theirs++;
                }
            }
        }
        return mine - theirs;
    }

    private static Set<Position> blockedCells(GameSnapshot snapshot) {
        Set<Position> blocked = new HashSet<>();
        blocked.addAll(selfCells(snapshot));
        blocked.addAll(opponentCells(snapshot));
        return blocked;
    }

    private static int[][] bfsDistances(int[][] map, Set<Position> blocked, Position source) {
        int rows = map.length, cols = map[0].length;
        int[][] dist = new int[rows][cols];
        for (int[] row : dist) {
            java.util.Arrays.fill(row, -1);
        }
        if (!inBounds(map, source.getRow(), source.getCol())) return dist;
        ArrayDeque<Position> queue = new ArrayDeque<>();
        queue.add(source);
        dist[source.getRow()][source.getCol()] = 0;
        while (!queue.isEmpty()) {
            Position cur = queue.poll();
            int cd = dist[cur.getRow()][cur.getCol()];
            for (int i = 0; i < 4; i++) {
                int nr = cur.getRow() + DX[i];
                int nc = cur.getCol() + DY[i];
                if (inBounds(map, nr, nc) && map[nr][nc] == 0
                        && !blocked.contains(new Position(nr, nc))
                        && dist[nr][nc] == -1) {
                    dist[nr][nc] = cd + 1;
                    queue.add(new Position(nr, nc));
                }
            }
        }
        return dist;
    }
}
