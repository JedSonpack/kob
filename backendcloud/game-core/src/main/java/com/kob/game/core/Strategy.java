package com.kob.game.core;

/**
 * 策略接口：根据当前只读局面决定下一步方向（0=上、1=右、2=下、3=左）。
 *
 * <p>实现必须是确定性的：不使用随机数、系统时间或外部状态，保证相同快照返回相同方向。
 */
public interface Strategy {
    int nextMove(GameSnapshot snapshot);
}
