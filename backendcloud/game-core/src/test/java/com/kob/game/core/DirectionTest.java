package com.kob.game.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectionTest {
    @Test
    void validatesOnlyZeroToThree() {
        assertFalse(Direction.isValid(-1));
        for (int direction = 0; direction < 4; direction++) {
            assertTrue(Direction.isValid(direction));
        }
        assertFalse(Direction.isValid(4));
    }

    @Test
    void movesWithExistingDirectionEncoding() {
        Position start = new Position(5, 6);
        assertEquals(new Position(4, 6), Direction.move(start, 0));
        assertEquals(new Position(5, 7), Direction.move(start, 1));
        assertEquals(new Position(6, 6), Direction.move(start, 2));
        assertEquals(new Position(5, 5), Direction.move(start, 3));
    }
}
