package com.kob.backend.websocket.utils;

import com.kob.backend.websocket.OnlineUserRegistry;
import com.kob.backend.websocket.WebSocketServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GameMessagePublisher 测试（审计任务 4.3）。
 * 用 RecordingWs 子类记录 sendMessage，避免 mock 具体类与 null session NPE。
 */
@ExtendWith(MockitoExtension.class)
class GameMessagePublisherTest {

    @Mock
    private OnlineUserRegistry onlineUserRegistry;

    @InjectMocks
    private GameMessagePublisher publisher;

    /** 记录型 WebSocketServer：覆写 sendMessage，避免访问 null session。 */
    static class RecordingWs extends WebSocketServer {
        final List<String> sent = new ArrayList<>();

        @Override
        public void sendMessage(String text) {
            sent.add(text);
        }
    }

    @Test
    void broadcast_sendsToBothOnline() {
        RecordingWs wsA = new RecordingWs();
        RecordingWs wsB = new RecordingWs();
        when(onlineUserRegistry.get(1)).thenReturn(wsA);
        when(onlineUserRegistry.get(2)).thenReturn(wsB);

        publisher.broadcast(1, 2, "msg");

        assertEquals(Collections.singletonList("msg"), wsA.sent);
        assertEquals(Collections.singletonList("msg"), wsB.sent);
    }

    @Test
    void sendToUser_offlineNoException() {
        when(onlineUserRegistry.get(1)).thenReturn(null);
        publisher.sendToUser(1, "msg");  // 离线不应抛异常
        verify(onlineUserRegistry).get(1);
    }
}
