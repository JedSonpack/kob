package com.kob.game.core;

/**
 * 单局比赛引擎：推进一局确定性比赛并返回结果。
 */
public interface GameEngine {
    GameResult play(GameConfig config, Strategy playerA, Strategy playerB);
}
