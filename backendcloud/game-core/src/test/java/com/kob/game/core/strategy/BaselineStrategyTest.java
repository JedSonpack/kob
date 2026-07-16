package com.kob.game.core.strategy;

import com.kob.game.core.GameSnapshot;
import com.kob.game.core.Position;
import com.kob.game.core.SnakeState;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaselineStrategyTest {

    /** 只有方向 1（右）安全的局面：上下左均为墙。 */
    private GameSnapshot onlyRightSafe() {
        int[][] map = {
                {1, 1, 1, 1, 1},
                {1, 0, 1, 0, 1},
                {1, 1, 0, 0, 1},
                {1, 0, 1, 0, 1},
                {1, 1, 1, 1, 1}
        };
        SnakeState self = new SnakeState(new Position(2, 2), Collections.<Integer>emptyList());
        SnakeState opp = new SnakeState(new Position(1, 3), Collections.<Integer>emptyList());
        return new GameSnapshot(1, map, self, opp);
    }

    /** 上（0）与右（1）都安全，但上方是死胡同（flood=1），右侧空间大（flood=6）。 */
    private GameSnapshot upSmallRightLarge() {
        int[][] map = {
                {1, 1, 1, 1, 1, 1},
                {1, 0, 1, 0, 0, 1},
                {1, 0, 0, 0, 0, 1},
                {1, 1, 1, 0, 0, 1},
                {1, 1, 1, 1, 1, 1}
        };
        SnakeState self = new SnakeState(new Position(2, 1), Collections.<Integer>emptyList());
        SnakeState opp = new SnakeState(new Position(3, 4), Collections.<Integer>emptyList());
        return new GameSnapshot(1, map, self, opp);
    }

    @Test
    void allBotsReturnOnlySafeDirection() {
        GameSnapshot snapshot = onlyRightSafe();
        assertEquals(1, new SafeBot().nextMove(snapshot));
        assertEquals(1, new GreedyBot().nextMove(snapshot));
        assertEquals(1, new TerritoryBot().nextMove(snapshot));
    }

    @Test
    void safeBotPicksFirstSafeDirection() {
        GameSnapshot snapshot = upSmallRightLarge();
        // 上(0) 与右(1) 均安全，SafeBot 取序号最小者
        assertEquals(0, new SafeBot().nextMove(snapshot));
    }

    @Test
    void greedyBotPicksLargerSpace() {
        GameSnapshot snapshot = upSmallRightLarge();
        // 右侧可达空间远大于上方死胡同
        assertEquals(1, new GreedyBot().nextMove(snapshot));
    }

    @Test
    void territoryBotPicksTerritoryAdvantage() {
        GameSnapshot snapshot = upSmallRightLarge();
        // 右侧领土差值(0)优于上方(-5)
        assertEquals(1, new TerritoryBot().nextMove(snapshot));
    }

    @Test
    void strategiesAreDeterministicAcrossCalls() {
        GameSnapshot snapshot = upSmallRightLarge();
        SafeBot safeBot = new SafeBot();
        GreedyBot greedyBot = new GreedyBot();
        TerritoryBot territoryBot = new TerritoryBot();

        int safe = safeBot.nextMove(snapshot);
        int greedy = greedyBot.nextMove(snapshot);
        int territory = territoryBot.nextMove(snapshot);

        for (int i = 0; i < 20; i++) {
            assertEquals(safe, safeBot.nextMove(snapshot));
            assertEquals(greedy, greedyBot.nextMove(snapshot));
            assertEquals(territory, territoryBot.nextMove(snapshot));
        }
    }
}
