package com.kob.service.evaluation.protocol;

import com.kob.game.core.GameSnapshot;
import com.kob.game.core.SnakeState;

import java.util.List;

/**
 * 将只读 {@link GameSnapshot} 编码为候选 Bot 可见的最小局面字符串。
 *
 * <p>格式与现有 Bot 模板兼容：
 * <pre>地图#我的出生行#我的出生列#(我的方向历史)#对手出生行#对手出生列#(对手方向历史)</pre>
 *
 * <p>仅暴露当前棋盘与双方蛇身，不含种子、数据集类型、对手名称或最终胜负。
 */
public final class SnapshotInputCodec {

    public String encode(GameSnapshot snapshot) {
        int[][] map = snapshot.getMap();
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < map.length; r++) {
            for (int c = 0; c < map[r].length; c++) {
                sb.append(map[r][c]);
            }
        }
        SnakeState self = snapshot.getSelf();
        SnakeState opponent = snapshot.getOpponent();
        sb.append('#')
                .append(self.getStart().getRow()).append('#')
                .append(self.getStart().getCol()).append('#')
                .append('(').append(movesToString(self.getMoves())).append(')')
                .append('#')
                .append(opponent.getStart().getRow()).append('#')
                .append(opponent.getStart().getCol()).append('#')
                .append('(').append(movesToString(opponent.getMoves())).append(')');
        return sb.toString();
    }

    private static String movesToString(List<Integer> moves) {
        StringBuilder sb = new StringBuilder(moves.size());
        for (Integer move : moves) {
            sb.append(move);
        }
        return sb.toString();
    }
}
