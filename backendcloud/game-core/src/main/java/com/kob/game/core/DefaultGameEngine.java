package com.kob.game.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 同步确定性单局引擎。
 *
 * <p>每回合顺序固定：
 * <ol>
 *   <li>为 A、B 分别创建自身视角的只读 {@link GameSnapshot}。</li>
 *   <li>顺序调用 A、B 策略，记录 {@code System.nanoTime()} 耗时。</li>
 *   <li>非法方向记入对应 invalidMoveCount 并判负；{@link StrategyExecutionException}
 *       按其失败原因判负。</li>
 *   <li>把两个方向同时追加到不可变 {@link SnakeState}。</li>
 *   <li>分别计算蛇身并调用 {@link CoreGameRules#isAlive} 判定碰撞。</li>
 *   <li>先追加 {@link ReplayFrame}，再生成胜负。</li>
 *   <li>双方同回合死亡为 DRAW；仅 A 死为 B_WIN；仅 B 死为 A_WIN；达到 maxRounds 为 TIMEOUT。</li>
 * </ol>
 */
public final class DefaultGameEngine implements GameEngine {

    @Override
    public GameResult play(GameConfig config, Strategy playerA, Strategy playerB) {
        int[][] map = new DeterministicMapGenerator().generate(config);
        SnakeState snakeA = new SnakeState(
                new Position(config.getRows() - 2, 1), Collections.<Integer>emptyList());
        SnakeState snakeB = new SnakeState(
                new Position(1, config.getCols() - 2), Collections.<Integer>emptyList());

        List<ReplayFrame> replay = new ArrayList<>();
        List<Long> nanosA = new ArrayList<>();
        List<Long> nanosB = new ArrayList<>();
        int invalidA = 0;
        int invalidB = 0;
        FailureReason failA = FailureReason.NONE;
        FailureReason failB = FailureReason.NONE;

        int round = 0;
        while (round < config.getMaxRounds()) {
            round++;
            GameSnapshot snapshotA = new GameSnapshot(round, map, snakeA, snakeB);
            GameSnapshot snapshotB = new GameSnapshot(round, map, snakeB, snakeA);

            MoveResult moveA = callStrategy(playerA, snapshotA);
            MoveResult moveB = callStrategy(playerB, snapshotB);
            nanosA.add(moveA.durationNanos);
            nanosB.add(moveB.durationNanos);

            if (moveA.exceptionReason != null) {
                failA = moveA.exceptionReason;
            } else if (!Direction.isValid(moveA.direction)) {
                failA = FailureReason.INVALID_MOVE;
                invalidA++;
            }
            if (moveB.exceptionReason != null) {
                failB = moveB.exceptionReason;
            } else if (!Direction.isValid(moveB.direction)) {
                failB = FailureReason.INVALID_MOVE;
                invalidB++;
            }

            boolean validA = Direction.isValid(moveA.direction);
            boolean validB = Direction.isValid(moveB.direction);
            SnakeState nextA = validA ? snakeA.append(moveA.direction) : snakeA;
            SnakeState nextB = validB ? snakeB.append(moveB.direction) : snakeB;

            List<Position> cellsA = CoreGameRules.cells(nextA);
            List<Position> cellsB = CoreGameRules.cells(nextB);

            if (failA == FailureReason.NONE
                    && !CoreGameRules.isAlive(cellsA, cellsB, map)) {
                failA = CoreGameRules.failureReason(cellsA, cellsB, map);
            }
            if (failB == FailureReason.NONE
                    && !CoreGameRules.isAlive(cellsB, cellsA, map)) {
                failB = CoreGameRules.failureReason(cellsB, cellsA, map);
            }

            replay.add(new ReplayFrame(moveA.direction, moveB.direction));
            snakeA = nextA;
            snakeB = nextB;

            boolean aDead = failA != FailureReason.NONE;
            boolean bDead = failB != FailureReason.NONE;
            if (aDead || bDead) {
                MatchOutcome outcome;
                if (aDead && bDead) {
                    outcome = MatchOutcome.DRAW;
                } else if (aDead) {
                    outcome = MatchOutcome.B_WIN;
                } else {
                    outcome = MatchOutcome.A_WIN;
                }
                return new GameResult(outcome, round, replay, nanosA, nanosB,
                        invalidA, invalidB, failA, failB);
            }
        }

        return new GameResult(MatchOutcome.TIMEOUT, round, replay, nanosA, nanosB,
                invalidA, invalidB, FailureReason.ROUND_LIMIT, FailureReason.ROUND_LIMIT);
    }

    private MoveResult callStrategy(Strategy strategy, GameSnapshot snapshot) {
        long start = System.nanoTime();
        try {
            int direction = strategy.nextMove(snapshot);
            return new MoveResult(direction, null, System.nanoTime() - start);
        } catch (StrategyExecutionException e) {
            return new MoveResult(-1, e.getFailureReason(), System.nanoTime() - start);
        }
    }

    private static final class MoveResult {
        final int direction;
        final FailureReason exceptionReason;
        final long durationNanos;

        MoveResult(int direction, FailureReason exceptionReason, long durationNanos) {
            this.direction = direction;
            this.exceptionReason = exceptionReason;
            this.durationNanos = durationNanos;
        }
    }
}
