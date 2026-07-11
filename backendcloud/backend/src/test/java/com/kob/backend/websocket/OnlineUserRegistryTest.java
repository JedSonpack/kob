package com.kob.backend.websocket;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OnlineUserRegistry 测试（审计任务 4.2）。
 * 验证注册/查询/移除的在线连接管理语义。
 */
class OnlineUserRegistryTest {

    private final OnlineUserRegistry registry = new OnlineUserRegistry();

    @Test
    void register_thenGetAndContains() {
        WebSocketServer ws = new WebSocketServer();
        registry.register(1, ws);
        assertTrue(registry.contains(1));
        assertSame(ws, registry.get(1));
    }

    @Test
    void remove_clearsEntry() {
        registry.register(1, new WebSocketServer());
        registry.remove(1);
        assertFalse(registry.contains(1));
        assertNull(registry.get(1));
    }

    @Test
    void getAbsent_returnsNull() {
        assertNull(registry.get(999));
        assertFalse(registry.contains(999));
    }
}
