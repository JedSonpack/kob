package com.kob.game.core;

/**
 * 方向编码与在线对战保持一致：0=上、1=右、2=下、3=左。
 *
 * <p>该模块不依赖 Spring、WebSocket、数据库或 HTTP，仅提供纯函数位移。
 */
public final class Direction {
    private static final int[] DX = {-1, 0, 1, 0};
    private static final int[] DY = {0, 1, 0, -1};

    private Direction() {}

    public static boolean isValid(int direction) {
        return direction >= 0 && direction < 4;
    }

    public static Position move(Position position, int direction) {
        if (!isValid(direction)) {
            throw new IllegalArgumentException("direction must be between 0 and 3");
        }
        return new Position(
                position.getRow() + DX[direction],
                position.getCol() + DY[direction]
        );
    }
}
