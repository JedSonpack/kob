package com.kob.backend.utils;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JwtUtil 单元测试（审计任务 1.1）。
 *
 * <p>验证密钥外置后 createJWT/parseJWT 的往返一致性与签名校验。
 * 测试中手动调用 setter 注入测试密钥（运行时由 Spring 经 @Value 注入真实密钥）。
 */
class JwtUtilTest {

    private static final String TEST_SUBJECT = "12345";

    /** 生成合法 Base64 密钥（≥32 字节以满足 JJWT 对 HS256 的最小长度要求），保证 generalKey() 不抛异常。 */
    private static String testKey() {
        return Base64.getEncoder().encodeToString(
                "0123456789abcdef0123456789abcdef0123456789abcdef".getBytes());  // 48 字节
    }

    @BeforeEach
    void setUp() {
        JwtUtil util = new JwtUtil();
        util.setJwtKey(testKey());
        util.setJwtTtlMillis(60_000L);
    }

    @Test
    void createAndParseJwt_roundTrip_preservesSubject() throws Exception {
        String token = JwtUtil.createJWT(TEST_SUBJECT);
        Claims claims = JwtUtil.parseJWT(token);
        assertEquals(TEST_SUBJECT, claims.getSubject());
    }

    @Test
    void parseJwt_tamperedToken_throws() {
        String token = JwtUtil.createJWT(TEST_SUBJECT);
        // 篡改末尾若干字符，破坏签名
        String tampered = token.substring(0, token.length() - 4) + "XXXX";
        assertThrows(Exception.class, () -> JwtUtil.parseJWT(tampered));
    }
}
