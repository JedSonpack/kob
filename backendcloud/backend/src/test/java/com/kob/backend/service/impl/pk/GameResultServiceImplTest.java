package com.kob.backend.service.impl.pk;

import com.kob.backend.mapper.RecordMapper;
import com.kob.backend.mapper.UserMapper;
import com.kob.backend.pojo.Record;
import com.kob.backend.pojo.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GameResultServiceImpl 单元测试（审计任务 2.4）。
 * 验证积分计算（赢+5/输-2/平不变）与双方更新+记录写入的调用序列。
 * 事务回滚的集成测试需 DB schema，列为本任务未验证项。
 */
@ExtendWith(MockitoExtension.class)
class GameResultServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private RecordMapper recordMapper;

    @InjectMocks
    private GameResultServiceImpl gameResultService;

    private User user(int id, int rating) {
        return new User(id, "u" + id, "pw", "photo", rating);
    }

    @Test
    void saveResult_loserA_aLoses2bGains5_andInsertsRecord() {
        when(userMapper.selectById(1)).thenReturn(user(1, 1500));
        when(userMapper.selectById(2)).thenReturn(user(2, 1500));

        gameResultService.saveResult(1, 2, 11, 1, 1, 12, "0123", "3210",
                "mapstr", "A", new Date(1000));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper, times(2)).updateById(captor.capture());
        assertEquals(Integer.valueOf(1498), captor.getAllValues().get(0).getRating());  // A 扣 2
        assertEquals(Integer.valueOf(1505), captor.getAllValues().get(1).getRating());  // B 加 5

        ArgumentCaptor<Record> rec = ArgumentCaptor.forClass(Record.class);
        verify(recordMapper, times(1)).insert(rec.capture());
        assertEquals("A", rec.getValue().getLoser());
        assertEquals("mapstr", rec.getValue().getMap());
    }

    @Test
    void saveResult_loserB_aGains5bLoses2() {
        when(userMapper.selectById(1)).thenReturn(user(1, 1500));
        when(userMapper.selectById(2)).thenReturn(user(2, 1500));

        gameResultService.saveResult(1, 2, 11, 1, 1, 12, "0123", "3210",
                "mapstr", "B", new Date(1000));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper, times(2)).updateById(captor.capture());
        assertEquals(Integer.valueOf(1505), captor.getAllValues().get(0).getRating());  // A 加 5
        assertEquals(Integer.valueOf(1498), captor.getAllValues().get(1).getRating());  // B 扣 2
    }

    @Test
    void saveResult_draw_ratingsUnchangedButStillSaved() {
        when(userMapper.selectById(1)).thenReturn(user(1, 1500));
        when(userMapper.selectById(2)).thenReturn(user(2, 1500));

        gameResultService.saveResult(1, 2, 11, 1, 1, 12, "0123", "3210",
                "mapstr", "all", new Date(1000));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper, times(2)).updateById(captor.capture());
        assertEquals(Integer.valueOf(1500), captor.getAllValues().get(0).getRating());
        assertEquals(Integer.valueOf(1500), captor.getAllValues().get(1).getRating());
        verify(recordMapper, times(1)).insert(any(Record.class));
    }
}
