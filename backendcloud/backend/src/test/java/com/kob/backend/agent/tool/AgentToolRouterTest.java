package com.kob.backend.agent.tool;

import com.kob.backend.agent.model.AgentStep;
import com.kob.backend.agent.model.AgentTask;
import com.kob.backend.agent.model.BotVersion;
import com.kob.backend.agent.model.DatasetType;
import com.kob.backend.agent.repository.AgentStepRepository;
import com.kob.backend.agent.repository.BotVersionRepository;
import com.kob.backend.agent.repository.EvaluationRunRepository;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolRouterTest {

    private EvaluationResponse compileOkResponse() {
        return new EvaluationResponse("req", true, null, null, Collections.<EvaluationMatchResult>emptyList());
    }

    private AgentTask task(String status) {
        AgentTask t = new AgentTask();
        t.setId(1L);
        t.setStatus(status);
        return t;
    }

    private BotVersion version() {
        BotVersion v = new BotVersion();
        v.setId(10L);
        v.setSourceCode("package com.kob.test;");
        return v;
    }

    @Test
    void compileIsIdempotentAndReusesOnSecondCall() {
        EvaluationClient client = mock(EvaluationClient.class);
        when(client.evaluate(any())).thenReturn(compileOkResponse());
        AgentStepRepository stepRepo = mock(AgentStepRepository.class);
        AgentStep running = new AgentStep();
        running.setId(1L);
        when(stepRepo.insertRunning(anyLong(), anyString(), anyString(), anyString(), anyString())).thenReturn(running);
        AgentStep success = new AgentStep();
        success.setStatus("SUCCESS");
        when(stepRepo.findByIdempotencyKey(anyString())).thenReturn(null, success);
        BotVersionRepository versionRepo = mock(BotVersionRepository.class);
        EvaluationRunRepository runRepo = mock(EvaluationRunRepository.class);

        AgentToolRouter router = new AgentToolRouter(client, versionRepo, runRepo, stepRepo);
        BotVersion version = version();

        CompileToolResult first = router.compile(task("COMPILING"), version);
        CompileToolResult second = router.compile(task("COMPILING"), version);

        verify(client, times(1)).evaluate(any());
        assertEquals(first, second);
        assertEquals(true, first.isSucceeded());
    }

    @Test
    void publicEvaluationInCompilingThrows() {
        AgentToolRouter router = new AgentToolRouter(
                mock(EvaluationClient.class), mock(BotVersionRepository.class),
                mock(EvaluationRunRepository.class), mock(AgentStepRepository.class));
        assertThrows(IllegalStateException.class,
                () -> router.evaluate(task("COMPILING"), version(), DatasetType.PUBLIC));
    }

    @Test
    void hiddenEvaluationInEvaluatingThrows() {
        AgentToolRouter router = new AgentToolRouter(
                mock(EvaluationClient.class), mock(BotVersionRepository.class),
                mock(EvaluationRunRepository.class), mock(AgentStepRepository.class));
        assertThrows(IllegalStateException.class,
                () -> router.evaluate(task("EVALUATING"), version(), DatasetType.HIDDEN));
    }
}
