package com.kob.game.core;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultGameEngineTest {

    @Test
    void sameConfigAndStrategiesProduceDeterministicDraw() {
        Strategy down = snapshot -> 2;
        Strategy up = snapshot -> 0;
        GameConfig config = new GameConfig(13, 14, 0, 7L, 20);

        GameResult first = new DefaultGameEngine().play(config, down, up);
        GameResult second = new DefaultGameEngine().play(config, down, up);

        assertEquals(MatchOutcome.DRAW, first.getOutcome());
        assertEquals(first.getReplay(), second.getReplay());
        assertEquals(first.getRounds(), second.getRounds());
        assertEquals(1, first.getRounds());
    }

    @Test
    void invalidDirectionLosesAndIncrementsInvalidMoveCount() {
        Strategy invalid = snapshot -> -1;
        Strategy safeDown = snapshot -> 2;
        GameConfig config = new GameConfig(13, 14, 0, 7L, 20);

        GameResult result = new DefaultGameEngine().play(config, invalid, safeDown);

        assertEquals(MatchOutcome.B_WIN, result.getOutcome());
        assertEquals(1, result.getInvalidMoveCountA());
        assertEquals(0, result.getInvalidMoveCountB());
        assertEquals(FailureReason.INVALID_MOVE, result.getFailureReasonA());
    }

    @Test
    void onlyOneSideDiesGivesWinToSurvivor() {
        // A 撞墙（下移到边界），B 安全下移
        Strategy downToWall = snapshot -> 2;
        Strategy safeDown = snapshot -> 2;
        GameConfig config = new GameConfig(13, 14, 0, 7L, 20);

        GameResult result = new DefaultGameEngine().play(config, downToWall, safeDown);

        assertEquals(MatchOutcome.B_WIN, result.getOutcome());
        assertEquals(FailureReason.WALL, result.getFailureReasonA());
        assertEquals(FailureReason.NONE, result.getFailureReasonB());
    }

    @Test
    void reachingMaxRoundsIsTimeout() {
        Strategy goRight = snapshot -> 1;
        Strategy goLeft = snapshot -> 3;
        GameConfig config = new GameConfig(13, 14, 0, 7L, 3);

        GameResult result = new DefaultGameEngine().play(config, goRight, goLeft);

        assertEquals(MatchOutcome.TIMEOUT, result.getOutcome());
        assertEquals(3, result.getRounds());
        assertEquals(FailureReason.ROUND_LIMIT, result.getFailureReasonA());
        assertEquals(FailureReason.ROUND_LIMIT, result.getFailureReasonB());
    }

    @Test
    void strategyExecutionExceptionFailsThatSide() {
        Strategy crashing = snapshot -> {
            throw new StrategyExecutionException(FailureReason.STEP_TIMEOUT, "超时");
        };
        Strategy safeDown = snapshot -> 2;
        GameConfig config = new GameConfig(13, 14, 0, 7L, 20);

        GameResult result = new DefaultGameEngine().play(config, crashing, safeDown);

        assertEquals(MatchOutcome.B_WIN, result.getOutcome());
        assertEquals(FailureReason.STEP_TIMEOUT, result.getFailureReasonA());
    }

    @Test
    void gameSnapshotIsImmutable() {
        int[][] map = {{1, 1, 1}, {1, 0, 1}, {1, 1, 1}};
        SnakeState self = new SnakeState(new Position(1, 1), Arrays.asList(0, 1));
        SnakeState opp = new SnakeState(new Position(1, 1), Collections.singletonList(2));
        GameSnapshot snapshot = new GameSnapshot(2, map, self, opp);

        assertEquals(2, snapshot.getRound());

        // 修改原始数组不影响快照
        map[1][1] = 9;
        assertNotEquals(9, snapshot.getMap()[1][1]);

        // 返回的地图是拷贝，修改不影响后续读取
        snapshot.getMap()[1][1] = 8;
        assertNotEquals(8, snapshot.getMap()[1][1]);

        // 移动列表不可修改
        List<Integer> moves = snapshot.getSelf().getMoves();
        assertThrows(UnsupportedOperationException.class, () -> moves.add(3));
        assertEquals(2, snapshot.getSelf().getMoves().size());
    }

    @Test
    void replayRecordsBothDirectionsEachRound() {
        Strategy down = snapshot -> 2;
        Strategy up = snapshot -> 0;
        GameConfig config = new GameConfig(13, 14, 0, 7L, 20);

        GameResult result = new DefaultGameEngine().play(config, down, up);

        assertEquals(1, result.getReplay().size());
        ReplayFrame frame = result.getReplay().get(0);
        assertEquals(2, frame.getDirectionA());
        assertEquals(0, frame.getDirectionB());
        assertTrue(result.getDecisionNanosA().size() >= 1);
        assertTrue(result.getDecisionNanosB().size() >= 1);
    }
}
