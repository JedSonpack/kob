package com.kob.game.core;

/**
 * 单局比赛配置：地图尺寸、内墙数量、固定种子与最大回合数。
 *
 * <p>种子决定地图生成，相同 {@link GameConfig} 与策略必须产生完全一致的结果。
 */
public final class GameConfig {
    private final int rows;
    private final int cols;
    private final int innerWalls;
    private final long seed;
    private final int maxRounds;

    public GameConfig(int rows, int cols, int innerWalls, long seed, int maxRounds) {
        this.rows = rows;
        this.cols = cols;
        this.innerWalls = innerWalls;
        this.seed = seed;
        this.maxRounds = maxRounds;
    }

    public int getRows() { return rows; }
    public int getCols() { return cols; }
    public int getInnerWalls() { return innerWalls; }
    public long getSeed() { return seed; }
    public int getMaxRounds() { return maxRounds; }
}
