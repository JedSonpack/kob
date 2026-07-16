package com.kob.game.core;

/**
 * 策略执行失败的可信分类异常。
 *
 * <p>引擎捕获该异常并让对应一方失败，失败原因由 {@link #getFailureReason()} 决定。
 * 消息应脱敏，不得包含种子、基准策略或隐藏集信息。
 */
public final class StrategyExecutionException extends RuntimeException {
    private final FailureReason failureReason;

    public StrategyExecutionException(FailureReason failureReason, String message) {
        super(message);
        this.failureReason = failureReason;
    }

    public FailureReason getFailureReason() {
        return failureReason;
    }
}
