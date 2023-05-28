package com.kob.backend.websocket;

import com.alibaba.fastjson.JSONObject;
import com.kob.backend.mapper.UserMapper;
import com.kob.backend.pojo.User;
import com.kob.backend.websocket.utils.Game;
import com.kob.backend.websocket.utils.JwtAuthentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
    final private static ConcurrentHashMap<Integer, WebSocketServer> users = new ConcurrentHashMap<>();

    // 这个连接用的是谁(用户)
    private User user;

    // 匹配池
    final private static CopyOnWriteArraySet<User> matchpool = new CopyOnWriteArraySet<>();

    // 需要注入usermapper 单是由于是多线程，所以注入有区别
    private static UserMapper userMapper;

    @Autowired
    public void setUserMapper(UserMapper userMapper) {
        WebSocketServer.userMapper = userMapper;
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
            matchpool.remove(this.user);
        }
    }

    private void startMatching() {
        System.out.println("start matching!");
        matchpool.add(this.user);

        while (matchpool.size() >= 2) {
            Iterator<User> it = matchpool.iterator();
            User a = it.next(), b = it.next();
            matchpool.remove(a);
            matchpool.remove(b);

            Game game = new Game(13, 14, 20);
            game.createMap();

            JSONObject respA = new JSONObject();
            respA.put("event", "start-matching");
            respA.put("opponent_username", b.getUsername());
            respA.put("opponent_photo", b.getPhoto());
            respA.put("gamemap", game.getG());
            users.get(a.getId()).sendMessage(respA.toJSONString());

            JSONObject respB = new JSONObject();
            respB.put("event", "start-matching");
            respB.put("opponent_username", a.getUsername());
            respB.put("opponent_photo", a.getPhoto());
            respB.put("gamemap", game.getG());
            users.get(b.getId()).sendMessage(respB.toJSONString());

        }


    }

    private void stopMatching() {
        System.out.println("stop matching");
        matchpool.remove(this.user);
    }


    @OnMessage
    public void onMessage(String message, Session session) { //当作路由 判断把任务交给谁
        // 从Client接收消息
        System.out.println("receive message!");
        JSONObject data = JSONObject.parseObject(message);
        String event = data.getString("event");
        if ("start-matching".equals(event)) {
            startMatching();
        } else if ("stop-matching".equals(event)) {
            stopMatching();
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