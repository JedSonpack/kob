package com.kob.game.core;

/**
 * 失败原因分类，覆盖规则判定与沙箱执行异常。
 */
public enum FailureReason {
    NONE,
    WALL,
    SELF,
    OPPONENT_BODY,
    INVALID_MOVE,
    STEP_TIMEOUT,
    SANDBOX_VIOLATION,
    OUTPUT_LIMIT,
    ROUND_LIMIT
}
