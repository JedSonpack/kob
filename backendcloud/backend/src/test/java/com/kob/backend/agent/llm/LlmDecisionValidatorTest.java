package com.kob.backend.agent.llm;

import com.kob.backend.agent.model.AgentAction;
import com.kob.backend.agent.model.AgentTaskStatus;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmDecisionValidatorTest {

    private final LlmDecisionValidator validator = new LlmDecisionValidator();
    private static final String SOURCE =
            "package com.kob.test; public class Bot { public Integer nextMove(String i){return 0;} }";

    @Test
    void rejectsActionNotAllowedInPhase() {
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(AgentTaskStatus.GENERATING,
                        new LlmDecision(AgentAction.IMPROVE_CODE, "s", "r", SOURCE, 1, 1)));
    }

    @Test
    void rejectsEmptySourceForNonFinish() {
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(AgentTaskStatus.GENERATING,
                        new LlmDecision(AgentAction.GENERATE_CODE, "s", "r", "", 1, 1)));
    }

    @Test
    void rejectsTooLongSource() {
        String tooLong = String.join("", Collections.nCopies(10001, "x"));
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(AgentTaskStatus.GENERATING,
                        new LlmDecision(AgentAction.GENERATE_CODE, "s", "r", tooLong, 1, 1)));
    }

    @Test
    void rejectsMissingStrategySummary() {
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(AgentTaskStatus.GENERATING,
                        new LlmDecision(AgentAction.GENERATE_CODE, "", "r", SOURCE, 1, 1)));
    }

    @Test
    void finishAllowsEmptySource() {
        assertDoesNotThrow(() ->
                validator.validate(AgentTaskStatus.ANALYZING,
                        new LlmDecision(AgentAction.FINISH, "s", "r", null, 1, 1)));
    }

    @Test
    void acceptsValidGenerateDecision() {
        assertDoesNotThrow(() ->
                validator.validate(AgentTaskStatus.GENERATING,
                        new LlmDecision(AgentAction.GENERATE_CODE, "s", "r", SOURCE, 1, 1)));
    }
}
