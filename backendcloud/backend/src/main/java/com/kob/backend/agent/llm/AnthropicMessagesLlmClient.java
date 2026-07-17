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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Anthropic Messages API 客户端。
 *
 * <p>协议适配局限在 {@link LlmClient} 边界内，Agent 工作流不感知具体模型供应商。
 * 思考内容不会进入决策、Trace 或异常信息；只接受文本决策或 submit_decision 工具调用。
 */
@Component
@ConditionalOnProperty(name = "kob.agent.llm.provider", havingValue = "anthropic-messages")
public class AnthropicMessagesLlmClient implements LlmClient {

    private static final String DECISION_TOOL = "submit_decision";
    private static final String SYSTEM_PROMPT =
            "你是 KOB Agent Lab 的 Java Bot 生成器。优先调用 submit_decision 工具返回决策。" +
            "如果不能调用工具，只返回 JSON 对象。允许动作只有 GENERATE_CODE、REPAIR_CODE、" +
            "IMPROVE_CODE、FINISH。状态约束：GENERATING 必须返回 GENERATE_CODE；" +
            "REPAIRING 必须返回 REPAIR_CODE；IMPROVING 必须返回 IMPROVE_CODE；" +
            "ANALYZING 只能返回 IMPROVE_CODE 或 FINISH。源码必须是完整 com.kob.test.Bot，并提供 " +
            "public Integer nextMove(String input)。不得返回 Markdown、Shell 命令、网络请求或文件操作。";

    private final RestTemplate restTemplate;
    private final String url;
    private final String model;
    private final String apiKey;
    private final int maxTokens;
    private final String anthropicVersion;

    public AnthropicMessagesLlmClient(
            @Qualifier("agentLlmRestTemplate") RestTemplate restTemplate,
            @Value("${kob.agent.llm.base-url:https://api.anthropic.com}") String baseUrl,
            @Value("${kob.agent.llm.anthropic-path:/v1/messages}") String path,
            @Value("${kob.agent.llm.model:}") String model,
            @Value("${kob.agent.llm.api-key:}") String apiKey,
            @Value("${kob.agent.llm.max-tokens:4096}") int maxTokens,
            @Value("${kob.agent.llm.anthropic-version:2023-06-01}") String anthropicVersion) {
        if (isBlank(baseUrl) || isBlank(path) || isBlank(apiKey) || isBlank(model)
                || isBlank(anthropicVersion) || maxTokens <= 0) {
            throw new IllegalStateException(
                    "Anthropic Messages LLM 需配置 base-url、path、model、api-key、版本和正数 max-tokens");
        }
        this.restTemplate = restTemplate;
        this.url = joinUrl(baseUrl, path);
        this.model = model;
        this.apiKey = apiKey;
        this.maxTokens = maxTokens;
        this.anthropicVersion = anthropicVersion;
    }

    @Override
    public LlmDecision decide(LlmContext context) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", anthropicVersion);
        HttpEntity<String> entity = new HttpEntity<>(buildRequestBody(context), headers);

        for (int attempt = 0; attempt < 3; attempt++) {
            ensureNotInterrupted();
            String response;
            try {
                response = restTemplate.postForObject(url, entity, String.class);
            } catch (HttpClientErrorException e) {
                if (isRetryableClientStatus(e.getStatusCode())) {
                    if (attempt < 2) {
                        sleep(backoffMs(attempt));
                        continue;
                    }
                    throw new AgentLlmException(AgentErrorCode.LLM_TIMEOUT,
                            "Anthropic 瞬态 HTTP 错误重试耗尽，状态 " + e.getRawStatusCode());
                }
                throw new AgentLlmException(AgentErrorCode.LLM_INVALID_RESPONSE,
                        "Anthropic 请求被拒绝，HTTP " + e.getRawStatusCode());
            } catch (RestClientException e) {
                if (attempt < 2) {
                    sleep(backoffMs(attempt));
                    continue;
                }
                throw new AgentLlmException(AgentErrorCode.LLM_TIMEOUT,
                        "Anthropic 请求失败: " + e.getClass().getSimpleName());
            }

            try {
                return parseResponse(response);
            } catch (RuntimeException parseError) {
                if (attempt == 0) {
                    sleep(200L);
                    continue;
                }
                throw new AgentLlmException(AgentErrorCode.LLM_INVALID_RESPONSE,
                        "Anthropic 响应非法: " + parseError.getClass().getSimpleName());
            }
        }
        throw new AgentLlmException(AgentErrorCode.LLM_TIMEOUT, "Anthropic 请求重试耗尽");
    }

    private String buildRequestBody(LlmContext context) {
        JSONObject request = new JSONObject();
        request.put("model", model);
        request.put("system", SYSTEM_PROMPT);
        request.put("max_tokens", maxTokens);

        JSONArray messages = new JSONArray();
        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", buildUserContent(context));
        messages.add(user);
        request.put("messages", messages);

        JSONArray tools = new JSONArray();
        tools.add(decisionTool());
        request.put("tools", tools);
        return request.toJSONString();
    }

    private JSONObject decisionTool() {
        JSONObject tool = new JSONObject();
        tool.put("name", DECISION_TOOL);
        tool.put("description", "提交下一步 Agent 决策和完整 Java Bot 源码");

        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        JSONObject properties = new JSONObject();
        properties.put("action", enumStringProperty(
                "GENERATE_CODE", "REPAIR_CODE", "IMPROVE_CODE", "FINISH"));
        properties.put("strategySummary", stringProperty("当前策略摘要"));
        properties.put("changeReason", stringProperty("本次决策或修改原因"));
        properties.put("sourceCode", stringProperty("完整 Java Bot 源码；FINISH 时可以省略"));
        schema.put("properties", properties);
        JSONArray required = new JSONArray();
        required.add("action");
        required.add("strategySummary");
        required.add("changeReason");
        schema.put("required", required);
        tool.put("input_schema", schema);
        return tool;
    }

    private JSONObject enumStringProperty(String... values) {
        JSONObject property = new JSONObject();
        property.put("type", "string");
        JSONArray allowed = new JSONArray();
        for (String value : values) {
            allowed.add(value);
        }
        property.put("enum", allowed);
        return property;
    }

    private JSONObject stringProperty(String description) {
        JSONObject property = new JSONObject();
        property.put("type", "string");
        property.put("description", description);
        return property;
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
        JSONObject evaluation = new JSONObject();
        evaluation.put("datasetType", context.getPublicEvaluation().getDatasetType().name());
        evaluation.put("gameCount", context.getPublicEvaluation().getGameCount());
        evaluation.put("score", context.getPublicEvaluation().getScore());
        evaluation.put("winRate", context.getPublicEvaluation().getWinRate());
        evaluation.put("decisionP95Ms", context.getPublicEvaluation().getDecisionP95Ms());
        evaluation.put("invalidMoveCount", context.getPublicEvaluation().getInvalidMoveCount());
        return evaluation;
    }

    private LlmDecision parseResponse(String responseJson) {
        JSONObject response = JSON.parseObject(responseJson);
        if (response == null) {
            throw new IllegalArgumentException("响应不是 JSON 对象");
        }
        JSONArray blocks = response.getJSONArray("content");
        if (blocks == null || blocks.isEmpty()) {
            throw new IllegalArgumentException("缺少 content");
        }

        StringBuilder text = new StringBuilder();
        JSONObject toolDecision = null;
        for (int i = 0; i < blocks.size(); i++) {
            JSONObject block = blocks.getJSONObject(i);
            if (block == null) {
                continue;
            }
            String type = block.getString("type");
            if ("text".equals(type)) {
                String value = block.getString("text");
                if (value != null) {
                    text.append(value);
                }
            } else if ("tool_use".equals(type)) {
                if (!DECISION_TOOL.equals(block.getString("name"))) {
                    throw new IllegalArgumentException("未知工具");
                }
                if (toolDecision != null) {
                    throw new IllegalArgumentException("重复决策工具调用");
                }
                toolDecision = block.getJSONObject("input");
                if (toolDecision == null) {
                    throw new IllegalArgumentException("工具输入为空");
                }
            }
        }

        JSONObject decision = toolDecision;
        if (decision == null) {
            if (text.length() == 0) {
                throw new IllegalArgumentException("没有可解析的决策内容");
            }
            decision = JSON.parseObject(text.toString());
        }
        return toDecision(decision, response.getJSONObject("usage"));
    }

    private LlmDecision toDecision(JSONObject decision, JSONObject usage) {
        if (decision == null || isBlank(decision.getString("action"))) {
            throw new IllegalArgumentException("决策缺少 action");
        }
        AgentAction action = AgentAction.valueOf(decision.getString("action"));
        Integer promptTokens = usage == null ? null : usage.getInteger("input_tokens");
        Integer completionTokens = usage == null ? null : usage.getInteger("output_tokens");
        return new LlmDecision(action,
                decision.getString("strategySummary"),
                decision.getString("changeReason"),
                decision.getString("sourceCode"),
                promptTokens, completionTokens);
    }

    private static String joinUrl(String baseUrl, String path) {
        boolean baseSlash = baseUrl.endsWith("/");
        boolean pathSlash = path.startsWith("/");
        if (baseSlash && pathSlash) {
            return baseUrl.substring(0, baseUrl.length() - 1) + path;
        }
        if (!baseSlash && !pathSlash) {
            return baseUrl + "/" + path;
        }
        return baseUrl + path;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static long backoffMs(int attempt) {
        return attempt == 0 ? 200L : 400L;
    }

    private static boolean isRetryableClientStatus(HttpStatus status) {
        return status == HttpStatus.REQUEST_TIMEOUT || status == HttpStatus.TOO_MANY_REQUESTS;
    }

    private static void ensureNotInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new AgentLlmException(AgentErrorCode.LLM_TIMEOUT, "Anthropic 请求已被中断");
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgentLlmException(AgentErrorCode.LLM_TIMEOUT, "Anthropic 请求已被中断");
        }
    }
}
