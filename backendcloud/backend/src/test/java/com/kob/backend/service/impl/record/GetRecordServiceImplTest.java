package com.kob.backend.service.impl.record;

import com.alibaba.fastjson.JSONObject;
import com.kob.backend.mapper.RecordMapper;
import com.kob.backend.mapper.UserMapper;
import com.kob.backend.pojo.Record;
import com.kob.backend.pojo.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

/**
 * GetRecordServiceImpl 测试（审计任务 3.2）。
 * 验证按 ID 取录像：返回含双方用户的 item；不存在时返回错误。
 */
@ExtendWith(MockitoExtension.class)
class GetRecordServiceImplTest {

    @Mock
    private RecordMapper recordMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private GetRecordServiceImpl service;

    @Test
    void getRecord_returnsItemWithUsers() {
        Record record = new Record(1, 10, 11, 1, 20, 1, 12, "0123", "3210", "mapstr", "A", new Date(1000));
        when(recordMapper.selectById(1)).thenReturn(record);
        when(userMapper.selectById(10)).thenReturn(new User(10, "a", "p", "photo-a", 1500));
        when(userMapper.selectById(20)).thenReturn(new User(20, "b", "p", "photo-b", 1600));

        JSONObject result = service.getRecord(1);

        assertEquals("success", result.get("error_message"));
        JSONObject item = result.getJSONObject("record_item");
        assertEquals("a", item.get("a_username"));
        assertEquals("photo-b", item.get("b_photo"));
        assertEquals("B胜", item.get("result"));  // loser=A -> B胜
        assertNotNull(item.get("record"));
    }

    @Test
    void getRecord_notFound() {
        when(recordMapper.selectById(999)).thenReturn(null);
        JSONObject result = service.getRecord(999);
        assertEquals("录像不存在", result.get("error_message"));
    }
}
