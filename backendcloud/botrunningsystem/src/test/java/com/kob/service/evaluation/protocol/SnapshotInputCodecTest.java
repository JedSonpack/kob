package com.kob.service.evaluation.protocol;

import com.kob.game.core.GameSnapshot;
import com.kob.game.core.Position;
import com.kob.game.core.SnakeState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SnapshotInputCodecTest {
    @Test
    void encodesOnlyCurrentBoardAndTwoSnakes() {
        int[][] map = {{1, 1, 1}, {1, 0, 1}, {1, 1, 1}};
        GameSnapshot snapshot = new GameSnapshot(
                2,
                map,
                new SnakeState(new Position(1, 1), Arrays.asList(0, 1)),
                new SnakeState(new Position(1, 1), Collections.singletonList(2))
        );

        String encoded = new SnapshotInputCodec().encode(snapshot);

        assertEquals("111101111#1#1#(01)#1#1#(2)", encoded);
        assertFalse(encoded.contains("PUBLIC"));
        assertFalse(encoded.contains("HIDDEN"));
        assertFalse(encoded.contains("seed"));
    }

    @Test
    void emptyMovesEncodeAsEmptyParentheses() {
        int[][] map = {{1, 1, 1}, {1, 0, 1}, {1, 1, 1}};
        GameSnapshot snapshot = new GameSnapshot(
                1,
                map,
                new SnakeState(new Position(1, 1), Collections.<Integer>emptyList()),
                new SnakeState(new Position(1, 1), Collections.<Integer>emptyList())
        );

        String encoded = new SnapshotInputCodec().encode(snapshot);

        assertEquals("111101111#1#1#()#1#1#()", encoded);
    }
}
