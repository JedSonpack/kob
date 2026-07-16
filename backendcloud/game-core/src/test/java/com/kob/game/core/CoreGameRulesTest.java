package com.kob.game.core;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreGameRulesTest {
    @Test
    void keepsExistingGrowthSchedule() {
        assertTrue(CoreGameRules.isGrowing(1));
        assertTrue(CoreGameRules.isGrowing(10));
        assertFalse(CoreGameRules.isGrowing(11));
        assertFalse(CoreGameRules.isGrowing(12));
        assertTrue(CoreGameRules.isGrowing(13));
    }

    @Test
    void buildsSnakeCellsFromStartAndMoves() {
        SnakeState snake = new SnakeState(
                new Position(5, 1),
                Arrays.asList(0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2)
        );
        assertEquals(11, CoreGameRules.cells(snake).size());
    }

    @Test
    void ignoresOpponentHeadLikeOnlineRules() {
        int[][] map = new int[5][5];
        assertTrue(CoreGameRules.isAlive(
                Arrays.asList(new Position(2, 1), new Position(2, 2)),
                Arrays.asList(new Position(2, 3), new Position(2, 2)),
                map
        ));
        assertTrue(CoreGameRules.isAlive(
                Collections.singletonList(new Position(1, 1)),
                Collections.singletonList(new Position(3, 3)),
                map
        ));
    }

    @Test
    void classifiesWallSelfAndOpponentBody() {
        int[][] map = new int[5][5];
        map[0][2] = 1;
        assertEquals(FailureReason.WALL, CoreGameRules.failureReason(
                Arrays.asList(new Position(1, 2), new Position(0, 2)),
                Arrays.asList(new Position(4, 4), new Position(4, 3)),
                map
        ));
        assertEquals(FailureReason.SELF, CoreGameRules.failureReason(
                Arrays.asList(new Position(2, 2), new Position(2, 3), new Position(2, 2)),
                Arrays.asList(new Position(4, 4), new Position(4, 3)),
                new int[5][5]
        ));
        assertEquals(FailureReason.OPPONENT_BODY, CoreGameRules.failureReason(
                Arrays.asList(new Position(0, 0), new Position(1, 1)),
                Arrays.asList(new Position(1, 1), new Position(2, 2)),
                new int[5][5]
        ));
    }
}
