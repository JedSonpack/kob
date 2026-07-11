package com.kob.backend.service.impl.pk;

import com.kob.backend.service.pk.ReceiveBotMoveService;
import com.kob.backend.websocket.WebSocketServer;
import com.kob.backend.websocket.utils.Game;
import org.springframework.stereotype.Service;

@Service
public class ReceiveBotMoveServiceImpl implements ReceiveBotMoveService {
    @Override
    public String receiveBotMove(Integer userId, Integer direction, String gameId, Integer roundId) {
        System.out.println("receive bot move: " + userId + " " + direction + " game=" + gameId + " round=" + roundId);
        // 审计 2.1：校验 gameId/roundId，防止串局/迟到/乱序回调
        if (WebSocketServer.onlineUserRegistry.get(userId) != null) {
            Game game = WebSocketServer.onlineUserRegistry.get(userId).game;
            if (game != null) {
                game.applyBotMove(userId, direction, gameId, roundId);
            }
        }

        return "receive bot move success";
    }
}

