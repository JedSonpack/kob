package com.kob.backend.websocket;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线连接注册表（审计任务 4.2）。
 *
 * <p>从 WebSocketServer 分离在线连接（userId -> WebSocketServer）的管理，
 * 降低 WebSocketServer 的职责负担（审计 6.2）。
 */
@Component
public class OnlineUserRegistry {

    private final ConcurrentHashMap<Integer, WebSocketServer> users = new ConcurrentHashMap<>();

    public void register(Integer userId, WebSocketServer ws) {
        users.put(userId, ws);
    }

    public void remove(Integer userId) {
        users.remove(userId);
    }

    public void remove(Integer userId, WebSocketServer ws) {
        users.remove(userId, ws);
    }

    public WebSocketServer get(Integer userId) {
        return users.get(userId);
    }

    public boolean contains(Integer userId) {
        return users.containsKey(userId);
    }
}
