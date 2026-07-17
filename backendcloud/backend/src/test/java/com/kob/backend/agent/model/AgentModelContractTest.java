package com.kob.backend.agent.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentModelContractTest {
    @Test
    void exposesTerminalAndRunningStates() {
        assertTrue(AgentTaskStatus.COMPLETED.isTerminal());
        assertTrue(AgentTaskStatus.FAILED.isTerminal());
        assertTrue(AgentTaskStatus.CANCELLED.isTerminal());
        assertFalse(AgentTaskStatus.EVALUATING.isTerminal());
    }

    @Test
    void limitsActionsByPhase() {
        assertTrue(AgentAction.GENERATE_CODE.isAllowedIn(AgentTaskStatus.GENERATING));
        assertTrue(AgentAction.REPAIR_CODE.isAllowedIn(AgentTaskStatus.REPAIRING));
        assertTrue(AgentAction.IMPROVE_CODE.isAllowedIn(AgentTaskStatus.ANALYZING));
        assertTrue(AgentAction.FINISH.isAllowedIn(AgentTaskStatus.ANALYZING));
        assertFalse(AgentAction.IMPROVE_CODE.isAllowedIn(AgentTaskStatus.GENERATING));
        assertFalse(AgentAction.IMPROVE_CODE.isAllowedIn(AgentTaskStatus.IMPROVING));
    }
}
