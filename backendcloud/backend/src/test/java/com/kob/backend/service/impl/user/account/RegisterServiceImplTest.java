package com.kob.backend.service.impl.user.account;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kob.backend.mapper.UserMapper;
import com.kob.backend.pojo.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 注册服务回归测试骨架（审计任务 0.1）。
 *
 * <p>纯 Mockito 单元测试：不加载 Spring 上下文、不连数据库。
 * 仅锁定 {@link RegisterServiceImpl} 当前已正确的业务校验与成功注册契约，
 * 为后续修改（如空密码空指针修复）提供回归护栏。
 *
 * <p>只 mock 接口（{@link UserMapper}、{@link PasswordEncoder}），
 * 规避 Java 17 运行时下 Mockito 3.x 对具体类的字节码插桩风险。
 */
@ExtendWith(MockitoExtension.class)
class RegisterServiceImplTest {

    /** 成功注册时 passwordEncoder.encode 的桩返回值，仅用于断言透传，与真实 BCrypt 无关。 */
    private static final String ENCODED_PASSWORD = "encoded-password";

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private RegisterServiceImpl registerService;

    // -------------------- 校验失败：不触达数据库与加密 --------------------

    @Test
    void register_whenUsernameNull_returnsUsernameEmpty() {
        Map<String, String> res = registerService.register(null, "pass", "pass");
        assertEquals("用户名不能为空", res.get("error_message"));
        verifyNoInteractions(userMapper, passwordEncoder);
    }

    @Test
    void register_whenUsernameBlank_returnsUsernameEmpty() {
        Map<String, String> res = registerService.register("   ", "pass", "pass");
        assertEquals("用户名不能为空", res.get("error_message"));
        verifyNoInteractions(userMapper, passwordEncoder);
    }

    @Test
    void register_whenUsernameTooLong_returnsUsernameTooLong() {
        String longUsername = String.join("", Collections.nCopies(101, "a"));
        Map<String, String> res = registerService.register(longUsername, "pass", "pass");
        assertEquals("用户名长度不能大于100", res.get("error_message"));
        verifyNoInteractions(userMapper, passwordEncoder);
    }

    @Test
    void register_whenPasswordEmpty_returnsPasswordEmpty() {
        Map<String, String> res = registerService.register("user", "", "");
        assertEquals("密码不能为空", res.get("error_message"));
        verifyNoInteractions(userMapper, passwordEncoder);
    }

    @Test
    void register_whenPasswordTooLong_returnsPasswordTooLong() {
        String longPassword = String.join("", Collections.nCopies(101, "a"));
        Map<String, String> res = registerService.register("user", longPassword, longPassword);
        assertEquals("密码长度不能大于100", res.get("error_message"));
        verifyNoInteractions(userMapper, passwordEncoder);
    }

    @Test
    void register_whenPasswordMismatch_returnsMismatch() {
        Map<String, String> res = registerService.register("user", "pass1", "pass2");
        assertEquals("两次输入的密码不一致", res.get("error_message"));
        verifyNoInteractions(userMapper, passwordEncoder);
    }

    // -------------------- 触达数据库的分支 --------------------

    @Test
    void register_whenUsernameExists_returnsExists_andDoesNotInsert() {
        when(userMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(
                        new User(1, "user", "x", "photo", 1500)));

        Map<String, String> res = registerService.register("user", "pass", "pass");

        assertEquals("用户名已存在", res.get("error_message"));
        verify(passwordEncoder, never()).encode(anyString());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void register_whenValid_insertsUserAndReturnsSuccess() {
        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        when(passwordEncoder.encode("pass")).thenReturn(ENCODED_PASSWORD);

        Map<String, String> res = registerService.register("user", "pass", "pass");

        assertEquals("success", res.get("error_message"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper, times(1)).insert(captor.capture());
        User saved = captor.getValue();
        assertEquals("user", saved.getUsername());
        assertEquals(ENCODED_PASSWORD, saved.getPassword());
        assertEquals(Integer.valueOf(1500), saved.getRating());
        assertNotNull(saved.getPhoto());
    }
}
