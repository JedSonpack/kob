package com.kob.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Agent 专用 HTTP 客户端配置：评测调用使用独立 RestTemplate（130s 读取超时），
 * 不复用通用 RestTemplate 的 3 秒超时。LLM 调用的 RestTemplate 由任务 7 追加。
 */
@Configuration
public class AgentHttpClientConfig {

    @Bean("agentEvaluationRestTemplate")
    public RestTemplate agentEvaluationRestTemplate(
            @Value("${kob.agent.evaluation.connect-timeout-ms:5000}") int connectTimeout,
            @Value("${kob.agent.evaluation.read-timeout-ms:130000}") int readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }
}
