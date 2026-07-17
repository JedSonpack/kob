package com.kob.backend.agent.tool;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * botsys 评测接口客户端。requestId = taskId:versionId:mode，作为 botsys 幂等键。
 */
@Component
public class EvaluationClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public EvaluationClient(@Qualifier("agentEvaluationRestTemplate") RestTemplate restTemplate,
                            @Value("${kob.agent.evaluation.base-url:http://127.0.0.1:3002}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public EvaluationResponse evaluate(EvaluationRequest request) {
        try {
            return restTemplate.postForObject(baseUrl + "/bot/evaluate/", request, EvaluationResponse.class);
        } catch (RestClientException e) {
            throw new RuntimeException("评测服务调用失败: " + e.getMessage(), e);
        }
    }

    public void cancel(String requestId) {
        try {
            restTemplate.postForObject(baseUrl + "/bot/evaluate/" + requestId + "/cancel/", null, Object.class);
        } catch (RestClientException ignored) {
            // 取消失败不影响主流程
        }
    }
}
