package com.kob.backend.websocket.utils;

import com.kob.backend.websocket.WebSocketServer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class GameProtocolTest {

    @Test
    void fatalRound_broadcastsMoveBeforeResult() throws Exception {
        GameMessagePublisher previousPublisher = WebSocketServer.gameMessagePublisher;
        com.kob.backend.service.pk.GameResultService previousResultService = WebSocketServer.gameResultService;
        GameMessagePublisher publisher = mock(GameMessagePublisher.class);
        try {
            WebSocketServer.gameMessagePublisher = publisher;
            WebSocketServer.gameResultService = mock(com.kob.backend.service.pk.GameResultService.class);
            Game game = new Game(13, 14, 0, 1, null, 2, null);
            game.createMap();
            game.setNextStepA(2);
            game.setNextStepB(0);

            game.start();
            game.join(2_000);

            ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
            verify(publisher, times(2)).broadcast(org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(2), messages.capture());
            List<String> values = messages.getAllValues();
            assertEquals("move", com.alibaba.fastjson.JSONObject.parseObject(values.get(0)).getString("event"));
            assertEquals("result", com.alibaba.fastjson.JSONObject.parseObject(values.get(1)).getString("event"));
        } finally {
            WebSocketServer.gameMessagePublisher = previousPublisher;
            WebSocketServer.gameResultService = previousResultService;
        }
    }
}
