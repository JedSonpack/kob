package com.kob.game.core;

import java.util.Objects;

/**
 * 单回合回放帧：记录双方本回合选择的方向。
 */
public final class ReplayFrame {
    private final int directionA;
    private final int directionB;

    public ReplayFrame(int directionA, int directionB) {
        this.directionA = directionA;
        this.directionB = directionB;
    }

    public int getDirectionA() { return directionA; }
    public int getDirectionB() { return directionB; }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof ReplayFrame)) return false;
        ReplayFrame other = (ReplayFrame) value;
        return directionA == other.directionA && directionB == other.directionB;
    }

    @Override
    public int hashCode() {
        return Objects.hash(directionA, directionB);
    }

    @Override
    public String toString() {
        return directionA + "," + directionB;
    }
}
