package com.kob.backend.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

// jwt工具类，用来创建、解析jwt token。
// 密钥与有效期通过配置注入（kob.jwt.secret / kob.jwt.ttl-millis），不再硬编码。
// TODO 后续可重构为实例方法 + 构造器注入，去掉静态 setter。


@Component
public class JwtUtil {

    // 通过 @Value 静态 setter 注入；调用方仍用静态方法，零改动。
    private static String jwtKey;
    private static long jwtTtlMillis;

    @Value("${kob.jwt.secret:}")
    public void setJwtKey(String jwtKey) {
        JwtUtil.jwtKey = jwtKey;
    }

    @Value("${kob.jwt.ttl-millis:1209600000}")  // 默认 14 天
    public void setJwtTtlMillis(long jwtTtlMillis) {
        JwtUtil.jwtTtlMillis = jwtTtlMillis;
    }

    @PostConstruct
    public void validate() {
        if (jwtKey == null || jwtKey.trim().isEmpty()) {
            throw new IllegalStateException(
                    "kob.jwt.secret 未配置：请在 application-local.properties 或环境变量 KOB_JWT_SECRET 中提供 JWT 密钥");
        }
    }

    public static String getUUID() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    public static String createJWT(String subject) {
        JwtBuilder builder = getJwtBuilder(subject, null, getUUID());
        return builder.compact();
    }

    private static JwtBuilder getJwtBuilder(String subject, Long ttlMillis, String uuid) {
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;
        SecretKey secretKey = generalKey();
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        if (ttlMillis == null) {
            ttlMillis = jwtTtlMillis;
        }

        long expMillis = nowMillis + ttlMillis;
        Date expDate = new Date(expMillis);
        return Jwts.builder()
                .setId(uuid)
                .setSubject(subject)
                .setIssuer("sg")
                .setIssuedAt(now)
                .signWith(signatureAlgorithm, secretKey)
                .setExpiration(expDate);
    }

    public static SecretKey generalKey() {
        byte[] encodeKey = Base64.getDecoder().decode(jwtKey);
        return new SecretKeySpec(encodeKey, 0, encodeKey.length, "HmacSHA256");
    }

    public static Claims parseJWT(String jwt) throws Exception {
        SecretKey secretKey = generalKey();
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(jwt)
                .getBody();
    }
}
