package com.kob.game.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 蛇的出生点与历史移动序列，不可变。
 *
 * <p>蛇身由 {@link CoreGameRules#cells(SnakeState)} 按增长规则计算，不在状态中保存。
 */
public final class SnakeState {
    private final Position start;
    private final List<Integer> moves;

    public SnakeState(Position start, List<Integer> moves) {
        this.start = start;
        this.moves = Collections.unmodifiableList(new ArrayList<>(moves));
    }

    public Position getStart() { return start; }
    public List<Integer> getMoves() { return moves; }

    public SnakeState append(int direction) {
        List<Integer> copy = new ArrayList<>(moves);
        copy.add(direction);
        return new SnakeState(start, copy);
    }
}
