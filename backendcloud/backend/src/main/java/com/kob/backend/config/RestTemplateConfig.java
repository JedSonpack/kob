package com.kob.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Value("${kob.http.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${kob.http.read-timeout-ms:3000}")
    private int readTimeoutMs;

    // @Primary：阶段 3 新增 agentEvaluation/agentLlm 两个具名 RestTemplate 后，按类型注入
    // （如 WebSocketServer.setRestTemplate）出现歧义，标主使既有通用 client 仍被选中。
    @Bean
    @Primary
    public RestTemplate getRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
}
