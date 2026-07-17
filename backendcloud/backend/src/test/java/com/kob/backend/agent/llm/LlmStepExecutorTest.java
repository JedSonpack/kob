package com.kob.backend.agent.llm;

import com.kob.backend.agent.model.AgentAction;
import com.kob.backend.agent.model.AgentStep;
import com.kob.backend.agent.model.AgentTask;
import com.kob.backend.agent.model.AgentTaskStatus;
import com.kob.backend.agent.model.BotVersion;
import com.kob.backend.agent.repository.AgentStepRepository;
import com.kob.backend.agent.repository.BotVersionRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmStepExecutorTest {

    private LlmContext ctx() {
        return new LlmContext(1L, AgentTaskStatus.GENERATING, "goal", 0, 3,
                null, null, null, null, null);
    }

    private AgentTask task() {
        AgentTask t = new AgentTask();
        t.setId(1L);
        return t;
    }

    @Test
    void executeCallsLlmAndPersistsVersion() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.decide(any())).thenReturn(new LlmDecision(AgentAction.GENERATE_CODE, "s", "r", "code", 10, 5));
        BotVersionRepository versionRepo = mock(BotVersionRepository.class);
        AgentStepRepository stepRepo = mock(AgentStepRepository.class);
        when(stepRepo.findByIdempotencyKey(anyString())).thenReturn(null);
        AgentStep step = new AgentStep();
        step.setId(7L);
        when(stepRepo.insertRunning(anyLong(), anyString(), anyString(), anyString(), anyString())).thenReturn(step);

        LlmStepExecutor executor = new LlmStepExecutor(llm, new LlmDecisionValidator(), versionRepo, stepRepo);
        LlmStepResult result = executor.execute(task(), ctx(), 1, 1, null);

        assertEquals(AgentAction.GENERATE_CODE, result.getDecision().getAction());
        verify(llm, times(1)).decide(any());
        verify(versionRepo).save(any());
    }

    @Test
    void executeRecoversFromSuccessStepWithoutCallingLlm() {
        LlmClient llm = mock(LlmClient.class);
        BotVersionRepository versionRepo = mock(BotVersionRepository.class);
        AgentStepRepository stepRepo = mock(AgentStepRepository.class);
        AgentStep success = new AgentStep();
        success.setStatus("SUCCESS");
        success.setOutputSummary("version:5:GENERATE_CODE");
        success.setPromptTokens(10);
        success.setCompletionTokens(5);
        when(stepRepo.findByIdempotencyKey(anyString())).thenReturn(success);
        BotVersion v = new BotVersion();
        v.setId(5L);
        v.setSourceCode("code");
        v.setStrategySummary("s");
        when(versionRepo.findById(5L)).thenReturn(v);

        LlmStepExecutor executor = new LlmStepExecutor(llm, new LlmDecisionValidator(), versionRepo, stepRepo);
        LlmStepResult result = executor.execute(task(), ctx(), 1, 1, null);

        assertEquals(AgentAction.GENERATE_CODE, result.getDecision().getAction());
        verify(llm, never()).decide(any());
    }
}
