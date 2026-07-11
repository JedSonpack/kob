package com.kob.backend.websocket.utils;

import com.kob.backend.websocket.OnlineUserRegistry;
import com.kob.backend.websocket.WebSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 游戏消息发布器（审计任务 4.3）。
 *
 * <p>将 Game 对 WebSocket 连接（OnlineUserRegistry）的直接依赖抽离，
 * Game 不再直接访问连接表，仅通过发布器推送消息。
 */
@Component
public class GameMessagePublisher {

    @Autowired
    private OnlineUserRegistry onlineUserRegistry;

    /** 向单个用户推送消息（不在线则忽略）。 */
    public void sendToUser(Integer userId, String message) {
        WebSocketServer ws = onlineUserRegistry.get(userId);
        if (ws != null) {
            ws.sendMessage(message);
        }
    }

    /** 向对局双方广播消息。 */
    public void broadcast(Integer aId, Integer bId, String message) {
        sendToUser(aId, message);
        sendToUser(bId, message);
    }
}
