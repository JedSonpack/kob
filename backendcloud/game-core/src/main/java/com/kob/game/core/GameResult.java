package com.kob.game.core;

import java.util.Collections;
import java.util.List;

/**
 * 单局比赛结果。
 *
 * <p>确定性部分（outcome、rounds、replay、invalidMoveCount、failureReason）在相同
 * GameConfig、种子与策略下完全一致；decisionNanos 为耗时遥测，不属于确定性契约。
 */
public final class GameResult {
    private final MatchOutcome outcome;
    private final int rounds;
    private final List<ReplayFrame> replay;
    private final List<Long> decisionNanosA;
    private final List<Long> decisionNanosB;
    private final int invalidMoveCountA;
    private final int invalidMoveCountB;
    private final FailureReason failureReasonA;
    private final FailureReason failureReasonB;

    public GameResult(
            MatchOutcome outcome,
            int rounds,
            List<ReplayFrame> replay,
            List<Long> decisionNanosA,
            List<Long> decisionNanosB,
            int invalidMoveCountA,
            int invalidMoveCountB,
            FailureReason failureReasonA,
            FailureReason failureReasonB
    ) {
        this.outcome = outcome;
        this.rounds = rounds;
        this.replay = replay;
        this.decisionNanosA = decisionNanosA;
        this.decisionNanosB = decisionNanosB;
        this.invalidMoveCountA = invalidMoveCountA;
        this.invalidMoveCountB = invalidMoveCountB;
        this.failureReasonA = failureReasonA;
        this.failureReasonB = failureReasonB;
    }

    public MatchOutcome getOutcome() { return outcome; }
    public int getRounds() { return rounds; }

    public List<ReplayFrame> getReplay() {
        return Collections.unmodifiableList(replay);
    }

    public List<Long> getDecisionNanosA() {
        return Collections.unmodifiableList(decisionNanosA);
    }

    public List<Long> getDecisionNanosB() {
        return Collections.unmodifiableList(decisionNanosB);
    }

    public int getInvalidMoveCountA() { return invalidMoveCountA; }
    public int getInvalidMoveCountB() { return invalidMoveCountB; }
    public FailureReason getFailureReasonA() { return failureReasonA; }
    public FailureReason getFailureReasonB() { return failureReasonB; }
}
