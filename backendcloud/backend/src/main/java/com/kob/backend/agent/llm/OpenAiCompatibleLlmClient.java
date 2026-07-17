package com.kob.backend.agent.llm;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.kob.backend.agent.model.AgentAction;
import com.kob.backend.agent.model.AgentErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * OpenAI 兼容 LLM 客户端（设计规格 8.2、13）。
 *
 * <p>System Prompt 限定只返回 JSON、允许动作、com.kob.test.Bot 契约，禁止 Markdown/Shell/网络/文件。
 * 请求带 Authorization: Bearer key；网络失败 200/400ms 退避最多重试 2 次，非法结构重试 1 次，
 * 最终映射 LLM_TIMEOUT / LLM_INVALID_RESPONSE。Trace 不保存 key 与原始响应。
 */
@Component
@ConditionalOnProperty(name = "kob.agent.llm.provider", havingValue = "openai-compatible")
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final String SYSTEM_PROMPT =
            "只返回 JSON 对象。允许动作只有 GENERATE_CODE、REPAIR_CODE、IMPROVE_CODE、FINISH。" +
            "源码必须是完整 com.kob.test.Bot，并提供 public Integer nextMove(String input)。" +
            "不得返回 Markdown、Shell 命令、网络请求或文件操作。";

    private final RestTemplate restTemplate;
    private final String url;
    private final String model;
    private final String apiKey;

    public OpenAiCompatibleLlmClient(
            @Qualifier("agentLlmRestTemplate") RestTemplate restTemplate,
            @Value("${kob.agent.llm.base-url:https://api.openai.com}") String baseUrl,
            @Value("${kob.agent.llm.path:/v1/chat/completions}") String path,
            @Value("${kob.agent.llm.model:}") String model,
            @Value("${kob.agent.llm.api-key:}") String apiKey) {
        if (apiKey == null || apiKey.isEmpty() || model == null || model.isEmpty()) {
            throw new IllegalStateException("OpenAI-compatible LLM 需配置 model 与 api-key");
        }
        this.restTemplate = restTemplate;
        this.url = baseUrl + path;
        this.model = model;
        this.apiKey = apiKey;
    }

    @Override
    public LlmDecision decide(LlmContext context) {
        String body = buildRequestBody(context);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        for (int attempt = 0; attempt < 3; attempt++) {
            String response;
            try {
                response = restTemplate.postForObject(url, entity, String.class);
            } catch (RestClientException e) {
                if (attempt < 2) {
                    sleep(backoffMs(attempt));
                    continue;
                }
                throw new AgentLlmException(AgentErrorCode.LLM_TIMEOUT, "LLM 请求失败: " + e.getMessage(), e);
            }
            try {
                return parseResponse(response);
            } catch (RuntimeException parseError) {
                if (attempt == 0) {
                    sleep(200L);
                    continue;
                }
                throw new AgentLlmException(AgentErrorCode.LLM_INVALID_RESPONSE,
                        "LLM 响应非法: " + parseError.getMessage(), parseError);
            }
        }
        throw new AgentLlmException(AgentErrorCode.LLM_TIMEOUT, "LLM 请求重试耗尽");
    }

    private String buildRequestBody(LlmContext context) {
        JSONObject request = new JSONObject();
        request.put("model", model);
        JSONArray messages = new JSONArray();
        JSONObject system = new JSONObject();
        system.put("role", "system");
        system.put("content", SYSTEM_PROMPT);
        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", buildUserContent(context));
        messages.add(system);
        messages.add(user);
        request.put("messages", messages);
        return request.toJSONString();
    }

    private String buildUserContent(LlmContext context) {
        JSONObject content = new JSONObject();
        content.put("status", context.getStatus().name());
        content.put("goal", context.getGoal());
        content.put("iteration", context.getIteration());
        content.put("maxIterations", context.getMaxIterations());
        content.put("currentSourceCode", context.getCurrentSourceCode());
        content.put("compileError", context.getCompileError());
        content.put("publicEvaluation", publicEvaluationJson(context));
        content.put("failureSummaries", context.getFailureSummaries());
        content.put("previousChangeSummary", context.getPreviousChangeSummary());
        return content.toJSONString();
    }

    private JSONObject publicEvaluationJson(LlmContext context) {
        if (context.getPublicEvaluation() == null) {
            return null;
        }
        JSONObject eval = new JSONObject();
        eval.put("datasetType", context.getPublicEvaluation().getDatasetType().name());
        eval.put("gameCount", context.getPublicEvaluation().getGameCount());
        eval.put("score", context.getPublicEvaluation().getScore());
        eval.put("winRate", context.getPublicEvaluation().getWinRate());
        eval.put("decisionP95Ms", context.getPublicEvaluation().getDecisionP95Ms());
        eval.put("invalidMoveCount", context.getPublicEvaluation().getInvalidMoveCount());
        return eval;
    }

    private LlmDecision parseResponse(String responseJson) {
        JSONObject response = JSON.parseObject(responseJson);
        JSONArray choices = response.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalArgumentException("缺少 choices");
        }
        String content = choices.getJSONObject(0).getJSONObject("message").getString("content");
        JSONObject decision = JSON.parseObject(content);
        AgentAction action = AgentAction.valueOf(decision.getString("action"));
        String strategySummary = decision.getString("strategySummary");
        String changeReason = decision.getString("changeReason");
        String sourceCode = decision.getString("sourceCode");
        Integer promptTokens = null;
        Integer completionTokens = null;
        JSONObject usage = response.getJSONObject("usage");
        if (usage != null) {
            promptTokens = usage.getInteger("prompt_tokens");
            completionTokens = usage.getInteger("completion_tokens");
        }
        return new LlmDecision(action, strategySummary, changeReason, sourceCode, promptTokens, completionTokens);
    }

    private static long backoffMs(int attempt) {
        return attempt == 0 ? 200L : 400L;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
