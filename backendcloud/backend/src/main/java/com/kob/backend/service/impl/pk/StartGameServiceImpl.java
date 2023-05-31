package com.kob.backend.service.impl.pk;

import com.kob.backend.service.pk.StartGameService;
import com.kob.backend.websocket.WebSocketServer;
import org.springframework.stereotype.Service;

@Service
public class StartGameServiceImpl implements StartGameService {

    @Override
    public String startGame(Integer aId, Integer aBotId, Integer bId, Integer bBotId) {
        //a 和 b 可以开始游戏了
        System.out.println("现在:" + aId + " " + bId + "可以进行游戏");
        WebSocketServer.startGame(aId, aBotId, bId, bBotId);
        return "success Satrt!";
    }
}
