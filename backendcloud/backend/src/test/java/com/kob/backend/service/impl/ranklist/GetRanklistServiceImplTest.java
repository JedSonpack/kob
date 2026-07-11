package com.kob.backend.service.impl.ranklist;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kob.backend.dto.UserListItemDto;
import com.kob.backend.mapper.UserMapper;
import com.kob.backend.pojo.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * GetRanklistServiceImpl 测试（审计任务 5.1）。
 * 验证映射为响应 DTO、不修改实体密码、返回计数。
 */
@ExtendWith(MockitoExtension.class)
class GetRanklistServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private GetRanklistServiceImpl service;

    @Test
    @SuppressWarnings("unchecked")
    void getList_mapsToDtosWithoutMutatingPassword() {
        User u1 = new User(1, "a", "secret-a", "photo-a", 1500);
        User u2 = new User(2, "b", "secret-b", "photo-b", 1600);
        IPage<User> page = new Page<>();
        page.setRecords(Arrays.asList(u1, u2));
        when(userMapper.selectPage(any(IPage.class), any(QueryWrapper.class))).thenReturn(page);
        when(userMapper.selectCount(any())).thenReturn(2L);

        JSONObject result = service.getList(1);

        List<UserListItemDto> users = (List<UserListItemDto>) result.get("users");
        assertEquals(2, users.size());
        assertEquals(Integer.valueOf(1), users.get(0).getId());
        assertEquals("a", users.get(0).getUsername());
        assertEquals(Integer.valueOf(1500), users.get(0).getRating());
        assertEquals(Long.valueOf(2L), result.get("users_count"));

        // 实体密码未被修改（审计 5.1：不再用 setPassword("") 规避泄漏）
        assertEquals("secret-a", u1.getPassword());
        assertEquals("secret-b", u2.getPassword());
    }
}
