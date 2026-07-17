package com.kob.backend.agent.llm;

import com.kob.backend.agent.model.AgentErrorCode;
import com.kob.backend.agent.model.AgentTaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleLlmClientTest {

    private static final String FINISH_BODY =
            "{\"action\":\"FINISH\",\"strategySummary\":\"公开集已稳定\",\"changeReason\":\"达到迭代上限\",\"sourceCode\":null}";

    private static final String FINISH_RESPONSE =
            "{\"choices\":[{\"message\":{\"content\":\"" + FINISH_BODY.replace("\"", "\\\"") + "\"}}],"
                    + "\"usage\":{\"prompt_tokens\":123,\"completion_tokens\":45}}";

    private LlmContext context() {
        return new LlmContext(1L, AgentTaskStatus.ANALYZING, "goal", 3, 3,
                "code", null, null, null, null);
    }

    private OpenAiCompatibleLlmClient client(RestTemplate restTemplate) {
        return new OpenAiCompatibleLlmClient(restTemplate, "https://api.openai.com",
                "/v1/chat/completions", "gpt-test", "test-key");
    }

    @Test
    void parsesFinishResponseWithBearerAndModel() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("gpt-test"))
                .andRespond(withSuccess(FINISH_RESPONSE, MediaType.APPLICATION_JSON));

        LlmDecision decision = client(restTemplate).decide(context());

        assertEquals(com.kob.backend.agent.model.AgentAction.FINISH, decision.getAction());
        assertEquals(Integer.valueOf(123), decision.getPromptTokens());
        assertEquals(Integer.valueOf(45), decision.getCompletionTokens());
        server.verify();
    }

    @Test
    void retriesOnceOnInvalidJsonThenSucceeds() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess(FINISH_RESPONSE, MediaType.APPLICATION_JSON));

        LlmDecision decision = client(restTemplate).decide(context());

        assertEquals(com.kob.backend.agent.model.AgentAction.FINISH, decision.getAction());
        server.verify();
    }

    @Test
    void mapsRepeatedNetworkFailureToLlmTimeout() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));

        AgentLlmException ex = assertThrows(AgentLlmException.class,
                () -> client(restTemplate).decide(context()));
        assertEquals(AgentErrorCode.LLM_TIMEOUT, ex.getCode());
        server.verify();
    }

    @Test
    void failsFastWhenApiKeyMissing() {
        RestTemplate restTemplate = new RestTemplate();
        assertThrows(IllegalStateException.class,
                () -> new OpenAiCompatibleLlmClient(restTemplate, "https://api.openai.com",
                        "/v1/chat/completions", "gpt-test", ""));
    }
}
