package com.kob.game.core;

import java.util.Objects;

/**
 * 不可变坐标，(row, col) 与在线对战的 Cell(x, y) 一一对应。
 */
public final class Position {
    private final int row;
    private final int col;

    public Position(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public int getRow() { return row; }
    public int getCol() { return col; }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof Position)) return false;
        Position other = (Position) value;
        return row == other.row && col == other.col;
    }

    @Override
    public int hashCode() {
        return Objects.hash(row, col);
    }

    @Override
    public String toString() {
        return "(" + row + "," + col + ")";
    }
}
