package com.kob.backend.websocket;

import com.alibaba.fastjson.JSONObject;
import com.kob.backend.mapper.BotMapper;
import com.kob.backend.mapper.RecordMapper;
import com.kob.backend.mapper.UserMapper;
import com.kob.backend.pojo.Bot;
import com.kob.backend.pojo.Record;
import com.kob.backend.pojo.User;
import com.kob.backend.service.pk.GameResultService;
import com.kob.backend.websocket.utils.Game;
import com.kob.backend.websocket.utils.JwtAuthentication;
import com.kob.backend.websocket.utils.PkValidation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@ServerEndpoint("/websocket/{token}")  // 注意不要以'/'结尾
public class WebSocketServer {

    // 后端向前端发送消息
    private Session session = null;

    // 在本类中维护一个静态表，将用户id和websocket对应起来，不需要初始化实例就可以有这个表  对所有实例都可见且只能在本类中使用
    final public static ConcurrentHashMap<Integer, WebSocketServer> users = new ConcurrentHashMap<>();

    // 这个连接用的是谁(用户)
    private User user;

    // 需要注入usermapper 单是由于是多线程，所以注入有区别
    public static UserMapper userMapper;

    // 存储游戏对象
    public Game game = null;

    // 路径（审计 4.1：外置到配置，默认本地地址，可由 kob.service.* 覆盖）
    private static String addPlayerUrl;
    private static String removePlayerurl;
    public static String addBotUrl;  // Game 非 Spring bean，借此注入

    @Value("${kob.service.matching.add-player-url:http://127.0.0.1:3001/player/add/}")
    public void setAddPlayerUrl(String addPlayerUrl) { WebSocketServer.addPlayerUrl = addPlayerUrl; }
    @Value("${kob.service.matching.remove-player-url:http://127.0.0.1:3001/player/remove/}")
    public void setRemovePlayerurl(String removePlayerurl) { WebSocketServer.removePlayerurl = removePlayerurl; }
    @Value("${kob.service.botrunning.add-bot-url:http://127.0.0.1:3002/bot/add/}")
    public void setAddBotUrl(String addBotUrl) { WebSocketServer.addBotUrl = addBotUrl; }


    public static RecordMapper recordMapper;
    public static RestTemplate restTemplate;
    private static BotMapper botMapper;
    public static GameResultService gameResultService;

    @Autowired
    public void setBotMapper(BotMapper botMapper) {
        WebSocketServer.botMapper = botMapper;
    }


    @Autowired
    public void setRecordMapper(RecordMapper recordMapper) {
        WebSocketServer.recordMapper = recordMapper;
    }

    @Autowired
    public void setUserMapper(UserMapper userMapper) {
        WebSocketServer.userMapper = userMapper;
    }

    @Autowired
    public void setRestTemplate(RestTemplate restTemplate) {
        WebSocketServer.restTemplate = restTemplate;
    }

    @Autowired
    public void setGameResultService(GameResultService gameResultService) {
        WebSocketServer.gameResultService = gameResultService;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("token") String token) throws IOException {
        // 建立连接
        // 保存以下session
        this.session = session;
        System.out.println("Connected!");

        int userId = JwtAuthentication.getUserId(token);

        this.user = userMapper.selectById(userId);

        if (this.user != null) {
            users.put(userId, this);
        } else {
            this.session.close();
        }


        System.out.println(users);

    }

    @OnClose
    public void onClose() {
        System.out.println("disconnected!");
        if (this.user != null) {
            users.remove(this.user.getId());
        }
    }

    public static void startGame(Integer aId, Integer aBotId, Integer bId, Integer bBotId) {
        User a = userMapper.selectById(aId), b = userMapper.selectById(bId);

        Bot botA = (aBotId != null && aBotId != -1) ? botMapper.selectById(aBotId) : null;
        Bot botB = (bBotId != null && bBotId != -1) ? botMapper.selectById(bBotId) : null;
        if (!PkValidation.isBotAllowed(aBotId, botA, aId) || !PkValidation.isBotAllowed(bBotId, botB, bId)) {
            throw new RuntimeException("Bot 不存在或不属于该用户");
        }

        Game game = new Game(13,
                14,
                15,
                a.getId(),
                botA,
                b.getId(),
                botB
        );
        game.createMap();
        if (users.get(a.getId()) != null)
            users.get(a.getId()).game = game;
        if (users.get(b.getId()) != null)
            users.get(b.getId()).game = game;

        game.start();

        JSONObject respGame = new JSONObject();
        respGame.put("a_id", game.getPlayerA().getId());
        respGame.put("a_sx", game.getPlayerA().getSx());
        respGame.put("a_sy", game.getPlayerA().getSy());
        respGame.put("b_id", game.getPlayerB().getId());
        respGame.put("b_sx", game.getPlayerB().getSx());
        respGame.put("b_sy", game.getPlayerB().getSy());
        respGame.put("map", game.getG());

        JSONObject respA = new JSONObject();
        respA.put("event", "start-matching");
        respA.put("opponent_username", b.getUsername());
        respA.put("opponent_photo", b.getPhoto());
        respA.put("game", respGame);
        if (users.get(a.getId()) != null)
            users.get(a.getId()).sendMessage(respA.toJSONString());

        JSONObject respB = new JSONObject();
        respB.put("event", "start-matching");
        respB.put("opponent_username", a.getUsername());
        respB.put("opponent_photo", a.getPhoto());
        respB.put("game", respGame);
        if (users.get(b.getId()) != null)
            users.get(b.getId()).sendMessage(respB.toJSONString());


    }


    private void startMatching(Integer bot_id) {

        System.out.println("start matching!");
        // 校验 Bot 归属（审计 1.2，防止运行他人 Bot）
        Bot bot = (bot_id != null && bot_id != -1) ? botMapper.selectById(bot_id) : null;
        if (!PkValidation.isBotAllowed(bot_id, bot, this.user.getId())) {
            JSONObject resp = new JSONObject();
            resp.put("event", "error");
            resp.put("message", "Bot 不存在或不属于当前用户");
            sendMessage(resp.toJSONString());
            return;
        }

        //像后端发请求
        MultiValueMap<String, String> data = new LinkedMultiValueMap<>();

        data.add("user_id", this.user.getId().toString());

        data.add("rating", this.user.getRating().toString());

        data.add("bot_id", bot_id.toString());


        restTemplate.postForObject(addPlayerUrl, data, String.class); //返回值的class

    }

    private void stopMatching() {
        System.out.println("stop matching");
        MultiValueMap<String, String> data = new LinkedMultiValueMap<>();
        data.add("user_id", this.user.getId().toString());
        restTemplate.postForObject(removePlayerurl, data, String.class);

    }

    private void move(int d) { //收到消息后进行移动
        if (game.getPlayerA().getId().equals(user.getId())) {
            if (game.getPlayerA().getBotId().equals(-1))  // 亲自出马 否则屏蔽输入
                game.setNextStepA(d);
        } else if (game.getPlayerB().getId().equals(user.getId())) {
            if (game.getPlayerB().getBotId().equals(-1))  // 亲自出马
                game.setNextStepB(d);
        }

    }


    @OnMessage
    public void onMessage(String message, Session session) { //当作路由 判断把任务交给谁
        // 从Client接收消息
        System.out.println("receive message!");
        JSONObject data = JSONObject.parseObject(message);
        String event = data.getString("event");
        if ("start-matching".equals(event)) {
            startMatching(Integer.parseInt(data.getString("bot_id")));
        } else if ("stop-matching".equals(event)) {
            stopMatching();
        } else if ("move".equals(event)) {
            move(data.getInteger("direction"));
        }

    }

    @OnError
    public void onError(Session session, Throwable error) {
        error.printStackTrace();
    }

    // 辅助函数 后端向前端发送消息
    public void sendMessage(String text) {
        synchronized (this.session) {
            try {
                this.session.getBasicRemote().sendText(text);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}