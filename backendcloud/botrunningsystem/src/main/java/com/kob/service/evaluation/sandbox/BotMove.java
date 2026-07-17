package com.kob.service.evaluation.sandbox;

/**
 * 候选 Bot 一步决策结果：方向与决策耗时（纳秒）。
 */
public final class BotMove {
    private final int direction;
    private final long durationNanos;

    public BotMove(int direction, long durationNanos) {
        this.direction = direction;
        this.durationNanos = durationNanos;
    }

    public int getDirection() { return direction; }
    public long getDurationNanos() { return durationNanos; }
}
