package com.kob.backend.agent.llm;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.kob.backend.agent.model.AgentAction;
import com.kob.backend.agent.model.AgentErrorCode;
import com.kob.backend.agent.model.AgentTaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AnthropicMessagesLlmClientTest {

    private static final String FINISH_BODY =
            "{\"action\":\"FINISH\",\"strategySummary\":\"公开集已稳定\","
                    + "\"changeReason\":\"达到迭代上限\",\"sourceCode\":null}";

    private LlmContext context() {
        return new LlmContext(1L, AgentTaskStatus.ANALYZING, "goal", 3, 3,
                "code", null, null, null, null);
    }

    private AnthropicMessagesLlmClient client(RestTemplate restTemplate) {
        return new AnthropicMessagesLlmClient(restTemplate, "https://gateway.example/",
                "/v1/messages", "model-test", "test-key", 4096, "2023-06-01");
    }

    @Test
    void sendsMessagesContractAndParsesTextAfterThinking() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo("https://gateway.example/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("x-api-key", "test-key"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andExpect(jsonPath("$.model").value("model-test"))
                .andExpect(jsonPath("$.system").value(containsString(
                        "GENERATING 必须返回 GENERATE_CODE")))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").isString())
                .andExpect(jsonPath("$.tools[0].name").value("submit_decision"))
                .andExpect(jsonPath("$.tools[0].input_schema.type").value("object"))
                .andExpect(jsonPath("$.tools[0].input_schema.properties.sourceCode.maxLength")
                        .value(10000))
                .andExpect(jsonPath("$.thinking.type").value("disabled"))
                .andExpect(jsonPath("$.tool_choice.type").value("tool"))
                .andExpect(jsonPath("$.tool_choice.name").value("submit_decision"))
                .andExpect(jsonPath("$.max_tokens").value(4096))
                .andRespond(withSuccess(textResponse(FINISH_BODY), MediaType.APPLICATION_JSON));

        LlmDecision decision = client(restTemplate).decide(context());

        assertEquals(AgentAction.FINISH, decision.getAction());
        assertEquals("公开集已稳定", decision.getStrategySummary());
        assertEquals(Integer.valueOf(123), decision.getPromptTokens());
        assertEquals(Integer.valueOf(45), decision.getCompletionTokens());
        server.verify();
    }

    @Test
    void concatenatesMultipleTextBlocks() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        int split = FINISH_BODY.length() / 2;
        server.expect(requestTo("https://gateway.example/v1/messages"))
                .andRespond(withSuccess(textResponse(
                        FINISH_BODY.substring(0, split), FINISH_BODY.substring(split)),
                        MediaType.APPLICATION_JSON));

        assertEquals(AgentAction.FINISH, client(restTemplate).decide(context()).getAction());
        server.verify();
    }

    @Test
    void parsesSubmitDecisionToolUse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo("https://gateway.example/v1/messages"))
                .andRespond(withSuccess(toolResponse("submit_decision"), MediaType.APPLICATION_JSON));

        assertEquals(AgentAction.FINISH, client(restTemplate).decide(context()).getAction());
        server.verify();
    }

    @Test
    void retriesOnceOnInvalidDecisionThenSucceeds() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo("https://gateway.example/v1/messages"))
                .andRespond(withSuccess(textResponse("not json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://gateway.example/v1/messages"))
                .andRespond(withSuccess(textResponse(FINISH_BODY), MediaType.APPLICATION_JSON));

        assertEquals(AgentAction.FINISH, client(restTemplate).decide(context()).getAction());
        server.verify();
    }

    @Test
    void mapsRepeatedNetworkFailureToLlmTimeout() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        for (int i = 0; i < 3; i++) {
            server.expect(requestTo("https://gateway.example/v1/messages"))
                    .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        }

        AgentLlmException error = assertThrows(AgentLlmException.class,
                () -> client(restTemplate).decide(context()));

        assertEquals(AgentErrorCode.LLM_TIMEOUT, error.getCode());
        server.verify();
    }

    @Test
    void mapsClientErrorToInvalidResponseWithoutRetry() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo("https://gateway.example/v1/messages"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        AgentLlmException error = assertThrows(AgentLlmException.class,
                () -> client(restTemplate).decide(context()));

        assertEquals(AgentErrorCode.LLM_INVALID_RESPONSE, error.getCode());
        server.verify();
    }

    @Test
    void doesNotRetainSensitiveErrorResponseInExceptionChain() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        String sensitiveMarker = "SENSITIVE_MODEL_RESPONSE_7f3a";
        server.expect(requestTo("https://gateway.example/v1/messages"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"" + sensitiveMarker + "\"}"));

        AgentLlmException error = assertThrows(AgentLlmException.class,
                () -> client(restTemplate).decide(context()));

        assertEquals(AgentErrorCode.LLM_INVALID_RESPONSE, error.getCode());
        assertEquals(null, error.getCause());
        assertEquals(false, stackTrace(error).contains(sensitiveMarker));
        server.verify();
    }

    @Test
    void retriesRateLimitThenSucceeds() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo("https://gateway.example/v1/messages"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo("https://gateway.example/v1/messages"))
                .andRespond(withSuccess(textResponse(FINISH_BODY), MediaType.APPLICATION_JSON));

        assertEquals(AgentAction.FINISH, client(restTemplate).decide(context()).getAction());
        server.verify();
    }

    @Test
    void stopsRetryingWhenInterruptedDuringBackoff() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo("https://gateway.example/v1/messages"))
                .andRespond(request -> {
                    Thread.currentThread().interrupt();
                    return withSuccess(textResponse("not json"), MediaType.APPLICATION_JSON)
                            .createResponse(request);
                });

        try {
            AgentLlmException error = assertThrows(AgentLlmException.class,
                    () -> client(restTemplate).decide(context()));
            assertEquals(AgentErrorCode.LLM_TIMEOUT, error.getCode());
            server.verify();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void rejectsResponseWithoutTextOrToolUse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo("https://gateway.example/v1/messages"))
                .andRespond(withSuccess(thinkingOnlyResponse(), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://gateway.example/v1/messages"))
                .andRespond(withSuccess(thinkingOnlyResponse(), MediaType.APPLICATION_JSON));

        AgentLlmException error = assertThrows(AgentLlmException.class,
                () -> client(restTemplate).decide(context()));

        assertEquals(AgentErrorCode.LLM_INVALID_RESPONSE, error.getCode());
        server.verify();
    }

    @Test
    void rejectsUnknownToolUse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo("https://gateway.example/v1/messages"))
                .andRespond(withSuccess(toolResponse("unknown_tool"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://gateway.example/v1/messages"))
                .andRespond(withSuccess(toolResponse("unknown_tool"), MediaType.APPLICATION_JSON));

        AgentLlmException error = assertThrows(AgentLlmException.class,
                () -> client(restTemplate).decide(context()));

        assertEquals(AgentErrorCode.LLM_INVALID_RESPONSE, error.getCode());
        server.verify();
    }

    @Test
    void rejectsUnknownAction() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        String unknownAction = FINISH_BODY.replace("FINISH", "UNKNOWN");
        server.expect(requestTo("https://gateway.example/v1/messages"))
                .andRespond(withSuccess(textResponse(unknownAction), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://gateway.example/v1/messages"))
                .andRespond(withSuccess(textResponse(unknownAction), MediaType.APPLICATION_JSON));

        AgentLlmException error = assertThrows(AgentLlmException.class,
                () -> client(restTemplate).decide(context()));

        assertEquals(AgentErrorCode.LLM_INVALID_RESPONSE, error.getCode());
        server.verify();
    }

    @Test
    void failsFastWhenRequiredConfigurationMissing() {
        RestTemplate restTemplate = new RestTemplate();
        assertThrows(IllegalStateException.class,
                () -> new AnthropicMessagesLlmClient(restTemplate, "https://gateway.example",
                        "/v1/messages", "model-test", "", 4096, "2023-06-01"));
        assertThrows(IllegalStateException.class,
                () -> new AnthropicMessagesLlmClient(restTemplate, "https://gateway.example",
                        "/v1/messages", "", "test-key", 4096, "2023-06-01"));
        assertThrows(IllegalStateException.class,
                () -> new AnthropicMessagesLlmClient(restTemplate, "https://gateway.example",
                        "/v1/messages", "model-test", "test-key", 0, "2023-06-01"));
    }

    private static String textResponse(String... texts) {
        JSONObject response = baseResponse();
        JSONArray content = new JSONArray();
        JSONObject thinking = new JSONObject();
        thinking.put("type", "thinking");
        thinking.put("thinking", "must-not-be-parsed");
        content.add(thinking);
        for (String text : texts) {
            JSONObject block = new JSONObject();
            block.put("type", "text");
            block.put("text", text);
            content.add(block);
        }
        response.put("content", content);
        return response.toJSONString();
    }

    private static String toolResponse(String toolName) {
        JSONObject response = baseResponse();
        JSONArray content = new JSONArray();
        JSONObject block = new JSONObject();
        block.put("type", "tool_use");
        block.put("name", toolName);
        block.put("input", JSONObject.parseObject(FINISH_BODY));
        content.add(block);
        response.put("content", content);
        return response.toJSONString();
    }

    private static String thinkingOnlyResponse() {
        JSONObject response = baseResponse();
        JSONArray content = new JSONArray();
        JSONObject thinking = new JSONObject();
        thinking.put("type", "thinking");
        thinking.put("thinking", "must-not-be-parsed");
        content.add(thinking);
        response.put("content", content);
        return response.toJSONString();
    }

    private static JSONObject baseResponse() {
        JSONObject response = new JSONObject();
        response.put("type", "message");
        JSONObject usage = new JSONObject();
        usage.put("input_tokens", 123);
        usage.put("output_tokens", 45);
        response.put("usage", usage);
        return response;
    }

    private static String stackTrace(Throwable error) {
        StringWriter output = new StringWriter();
        error.printStackTrace(new PrintWriter(output));
        return output.toString();
    }
}
