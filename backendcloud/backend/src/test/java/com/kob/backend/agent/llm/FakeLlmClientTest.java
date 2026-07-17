package com.kob.backend.agent.llm;

import com.kob.backend.agent.model.AgentAction;
import com.kob.backend.agent.model.AgentTaskStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FakeLlmClientTest {

    private final FakeLlmClient client = new FakeLlmClient();

    private LlmContext ctx(AgentTaskStatus status, int iteration, int max) {
        return new LlmContext(1L, status, "goal", iteration, max, null, null, null, null, null);
    }

    @Test
    void generatingReturnsGenerateCode() {
        assertEquals(AgentAction.GENERATE_CODE, client.decide(ctx(AgentTaskStatus.GENERATING, 0, 3)).getAction());
    }

    @Test
    void repairingReturnsRepairCode() {
        assertEquals(AgentAction.REPAIR_CODE, client.decide(ctx(AgentTaskStatus.REPAIRING, 1, 3)).getAction());
    }

    @Test
    void analyzingBeforeMaxReturnsImprove() {
        assertEquals(AgentAction.IMPROVE_CODE, client.decide(ctx(AgentTaskStatus.ANALYZING, 1, 3)).getAction());
    }

    @Test
    void analyzingAtMaxReturnsFinish() {
        assertEquals(AgentAction.FINISH, client.decide(ctx(AgentTaskStatus.ANALYZING, 3, 3)).getAction());
    }

    @Test
    void isDeterministicAcrossCalls() {
        for (int i = 0; i < 20; i++) {
            assertEquals(AgentAction.GENERATE_CODE, client.decide(ctx(AgentTaskStatus.GENERATING, 0, 3)).getAction());
        }
    }
}
