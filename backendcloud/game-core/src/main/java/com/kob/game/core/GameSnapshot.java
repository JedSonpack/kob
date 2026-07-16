package com.kob.game.core;

/**
 * 只读比赛快照，提供给 {@link Strategy} 决策。
 *
 * <p>快照不可变：地图通过防御性复制返回，移动列表不可修改。
 * 快照不暴露种子、基准策略名称或最终胜负。
 */
public final class GameSnapshot {
    private final int round;
    private final int[][] map;
    private final SnakeState self;
    private final SnakeState opponent;

    public GameSnapshot(int round, int[][] map, SnakeState self, SnakeState opponent) {
        this.round = round;
        this.map = deepCopy(map);
        this.self = self;
        this.opponent = opponent;
    }

    public int getRound() { return round; }

    public int[][] getMap() {
        return deepCopy(map);
    }

    public SnakeState getSelf() { return self; }
    public SnakeState getOpponent() { return opponent; }

    private static int[][] deepCopy(int[][] source) {
        int[][] copy = new int[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i].clone();
        }
        return copy;
    }
}
