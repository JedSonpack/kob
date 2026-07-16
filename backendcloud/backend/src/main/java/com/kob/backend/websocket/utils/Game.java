package com.kob.backend.websocket.utils;

import com.alibaba.fastjson.JSONObject;

import com.kob.backend.pojo.Bot;
import com.kob.backend.websocket.WebSocketServer;
import com.kob.backend.websocket.utils.Player;
import com.kob.game.core.DeterministicMapGenerator;
import com.kob.game.core.GameConfig;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public class Game extends Thread {
    private final Integer rows;
    private final Integer cols;
    private final Integer inner_walls_count;
    private final int[][] g;
    private final long seed;
    private final Player playerA, playerB;
    private Integer nextStepA = null;
    private Integer nextStepB = null;
    private ReentrantLock lock = new ReentrantLock();
    private String status = "playing";  // playing -> finished
    private String loser = "";  // all: 平局，A: A输，B: B输
    private final String gameId = UUID.randomUUID().toString().replaceAll("-", "");  // 审计 2.1：对局唯一标识
    private volatile Integer currentRoundId = 0;  // 审计 2.1：当前回合，Bot 回调须匹配

    public Game(Integer rows, Integer cols, Integer inner_walls_count, Integer idA, Bot botA, Integer idB, Bot botB) {
        this(rows, cols, inner_walls_count, idA, botA, idB, botB, new Random().nextLong());
    }

    /** 阶段 1 任务 6：带固定种子的确定性构造器，用于可复现地图与离线评测适配。 */
    public Game(Integer rows, Integer cols, Integer inner_walls_count, Integer idA, Bot botA, Integer idB, Bot botB, long seed) {
        this.rows = rows;
        this.cols = cols;
        this.inner_walls_count = inner_walls_count;
        this.seed = seed;
        this.g = new int[rows][cols];
        Integer botIdA = -1, botIdB = -1;
        String botCodeA = "", botCodeB = "";

        if (botA != null) {
            botIdA = botA.getId();
            botCodeA = botA.getContent();
        }
        if (botB != null) {
            botIdB = botB.getId();
            botCodeB = botB.getContent();
        }


        playerA = new Player(idA, botIdA, botCodeA, rows - 2, 1, new ArrayList<>());
        playerB = new Player(idB, botIdB, botCodeB, 1, cols - 2, new ArrayList<>());

    }

    public Player getPlayerA() {
        return playerA;
    }

    public Player getPlayerB() {
        return playerB;
    }

    public void setNextStepA(Integer nextStepA) {
        if (!PkValidation.isValidDirection(nextStepA)) return;  // 非法方向忽略（审计 1.2，防止越界崩线程）
        lock.lock();
        try {
            this.nextStepA = nextStepA;
        } finally {
            lock.unlock();
        }
    }

    public void setNextStepB(Integer nextStepB) {
        if (!PkValidation.isValidDirection(nextStepB)) return;  // 非法方向忽略（审计 1.2，防止越界崩线程）
        lock.lock();
        try {
            this.nextStepB = nextStepB;
        } finally {
            lock.unlock();
        }
    }

    /** 审计 2.1：Bot 回调校验并应用移动。gameId/roundId 不匹配则忽略（防串局/迟到/乱序）。 */
    public boolean applyBotMove(Integer userId, Integer direction, String callbackGameId, Integer callbackRoundId) {
        lock.lock();
        try {
            if (!PkValidation.isMoveForCurrentRound(callbackGameId, callbackRoundId, gameId, currentRoundId)) {
                return false;
            }
            if (playerA.getId().equals(userId)) {
                setNextStepA(direction);
                return true;
            } else if (playerB.getId().equals(userId)) {
                setNextStepB(direction);
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public int[][] getG() {
        return g;
    }

    public void createMap() {
        // 阶段 1 任务 6：委托 game-core 确定性生成器，删除旧 Random 画图与递归连通检查。
        GameConfig config = new GameConfig(rows, cols, inner_walls_count, seed, 1000);
        int[][] generated = new DeterministicMapGenerator().generate(config);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                g[i][j] = generated[i][j];
            }
        }
    }

    private String getInput(Player player) {  // 将当前的局面信息，编码成字符串 发送给BotRunningSystem
        Player me, you;
        if (playerA.getId().equals(player.getId())) {
            me = playerA;
            you = playerB;
        } else {
            me = playerB;
            you = playerA;
        }

        return getMapString() + "#" +
                me.getSx() + "#" +
                me.getSy() + "#(" +
                me.getStepsString() + ")#" +  //左右加上小括号 防止出错
                you.getSx() + "#" +
                you.getSy() + "#(" +
                you.getStepsString() + ")";
    }


    private void sendBotCode(Player player) { // 判断是否需要执行bot代码
        if (player.getBotId().equals(-1)) return;  // 亲自出马，不需要执行代码
        MultiValueMap<String, String> data = new LinkedMultiValueMap<>();

        data.add("user_id", player.getId().toString());

        data.add("bot_code", player.getBotCode());

        data.add("input", getInput(player));

        data.add("game_id", gameId);  // 审计 2.1：关联对局与回合，防串局
        data.add("round_id", currentRoundId.toString());

        WebSocketServer.restTemplate.postForObject(WebSocketServer.addBotUrl, data, String.class);

    }


    private boolean nextStep() {  // 等待两名玩家的下一步操作
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }


        sendBotCode(playerA);
        sendBotCode(playerB);

        for (int i = 0; i < 50; i++) {
            try {
                Thread.sleep(100);
                lock.lock();
                try {
                    if (nextStepA != null && nextStepB != null) {
                        playerA.getSteps().add(nextStepA);
                        playerB.getSteps().add(nextStepB);
                        return true;
                    }
                } finally {
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return false;
    }


    private boolean check_valid(List<Cell> cellsA, List<Cell> cellsB) {
        return GameRules.checkValid(cellsA, cellsB, g);  // 审计 0.2：委托纯函数
    }

    private void judge() {  // 判断两名玩家下一步操作是否合法
        List<Cell> cellsA = playerA.getCells();
        List<Cell> cellsB = playerB.getCells();

        boolean validA = check_valid(cellsA, cellsB);

        boolean validB = check_valid(cellsB, cellsA);

        if (!validA || !validB) {
            status = "finished";

            if (!validA && !validB) {
                loser = "all";
            } else if (!validA) {
                loser = "A";
            } else {
                loser = "B";
            }
        }
    }


    private void saveToDatabase() {
        // 审计 2.4：委托事务化持久化服务，原子保存双方积分与战绩
        WebSocketServer.gameResultService.saveResult(
                playerA.getId(), playerB.getId(),
                playerA.getSx(), playerA.getSy(),
                playerB.getSx(), playerB.getSy(),
                playerA.getStepsString(), playerB.getStepsString(),
                getMapString(), loser, new Date()
        );
    }


    private void sendResult() {  // 向两个Client公布结果
        JSONObject resp = new JSONObject();
        resp.put("event", "result");
        resp.put("loser", loser);
        saveToDatabase();
        sendAllMessage(resp.toJSONString());
    }


    private void sendAllMessage(String message) {
        // 审计 4.3：委托消息发布器，不再直接访问连接表
        WebSocketServer.gameMessagePublisher.broadcast(playerA.getId(), playerB.getId(), message);
    }

    private void sendMove() {  // 向两个Client传递移动信息
        lock.lock();
        try {
            JSONObject resp = new JSONObject();
            resp.put("event", "move");
            resp.put("a_direction", nextStepA);
            resp.put("b_direction", nextStepB);
            sendAllMessage(resp.toJSONString());
            nextStepA = nextStepB = null;
        } finally {
            lock.unlock();
        }
    }

    private String getMapString() {
        StringBuilder res = new StringBuilder();
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                res.append(g[i][j]);
            }
        }
        return res.toString();
    }


    public void run() {
        for (int i = 0; i < 1000; i++) {
            currentRoundId = i;  // 审计 2.1：当前回合
            if (nextStep()) {  // 是否获取了两条蛇的下一步操作
                judge();
                sendMove();
                if (!status.equals("playing")) {
                    sendResult();
                    break;
                }
            } else {
                status = "finished";
                lock.lock();
                try {
                    if (nextStepA == null && nextStepB == null) {
                        loser = "all";
                    } else if (nextStepA == null) {
                        loser = "A";
                    } else {
                        loser = "B";
                    }
                } finally {
                    lock.unlock();
                }
                sendResult();
                break;
            }
        }
    }

}
