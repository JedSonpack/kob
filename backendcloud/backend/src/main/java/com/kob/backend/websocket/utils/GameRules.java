package com.kob.backend.websocket.utils;

import com.kob.game.core.CoreGameRules;
import com.kob.game.core.Position;

import java.util.ArrayList;
import java.util.List;

/**
 * 游戏规则纯函数（审计任务 0.2 / 阶段 1 任务 6）。
 *
 * <p>保留在线对战的既有签名，内部委托 {@link CoreGameRules}，使在线与离线评测共用同一份规则。
 */
public final class GameRules {

    private GameRules() {}

    /**
     * 蛇是否在本回合增长。
     * <p>step 从 1 起计：前 10 回合每回合增长；之后每 3 回合增长一次（step%3==1）。
     */
    public static boolean checkTailIncreasing(int step) {
        return CoreGameRules.isGrowing(step);
    }

    /**
     * 蛇头是否合法：不撞墙、不撞自身身体、不撞对手身体。
     * <p>selfCells/opponentCells 末尾为蛇头；不判定头对头（原规则语义）。
     */
    public static boolean checkValid(List<Cell> selfCells, List<Cell> opponentCells, int[][] g) {
        return CoreGameRules.isAlive(toPositions(selfCells), toPositions(opponentCells), g);
    }

    private static List<Position> toPositions(List<Cell> cells) {
        List<Position> positions = new ArrayList<>(cells.size());
        for (Cell cell : cells) {
            positions.add(new Position(cell.getX(), cell.getY()));
        }
        return positions;
    }
}
