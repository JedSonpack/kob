package com.kob.service.evaluation;

import com.kob.service.evaluation.dto.EvaluationMode;
import com.kob.service.evaluation.dto.EvaluationRequest;
import com.kob.service.evaluation.dto.EvaluationResponse;
import com.kob.service.evaluation.sandbox.BotMove;
import com.kob.service.evaluation.sandbox.PersistentBotProcess;
import com.kob.service.evaluation.sandbox.PersistentBotProcessFactory;
import com.kob.service.evaluation.sandbox.SandboxErrorCode;
import com.kob.service.evaluation.sandbox.SandboxExecutionException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvaluationCoordinatorTest {

    private static final String BOT_SOURCE =
            "package com.kob.test;\n" +
            "public class Bot {\n" +
            "  public Integer nextMove(String input) { return 1; }\n" +
            "}";

    private EvaluationCoordinator newCoordinator(PersistentBotProcessFactory factory) {
        long[] pub = {101L, 211L, 307L, 401L, 503L, 601L, 709L, 809L};
        long[] hid = {907L, 1009L, 1103L, 1201L};
        return new EvaluationCoordinator(factory, pub, hid, 1000, 120000L, 60000L, 1048576L);
    }

    @Test
    void publicEvaluationRuns48MatchesWithOneProcess() {
        PersistentBotProcess process = mock(PersistentBotProcess.class);
        when(process.decide(anyString())).thenReturn(new BotMove(1, 1_000_000L));
        PersistentBotProcessFactory factory = mock(PersistentBotProcessFactory.class);
        when(factory.create()).thenReturn(process);
        EvaluationCoordinator coordinator = newCoordinator(factory);

        EvaluationResponse response = coordinator.evaluate(
                new EvaluationRequest("req-1", BOT_SOURCE, EvaluationMode.PUBLIC));

        assertTrue(response.isCompileSucceeded());
        assertEquals("req-1", response.getRequestId());
        assertEquals(48, response.getSummary().getGameCount());
        assertEquals(48, response.getMatches().size());
        assertEquals(0, response.getSummary().getInvalidMoveCount());
        verify(factory, times(1)).create();
        verify(process, times(1)).start(anyString());
        verify(process).close();
    }

    @Test
    void hiddenEvaluationRuns24Matches() {
        PersistentBotProcess process = mock(PersistentBotProcess.class);
        when(process.decide(anyString())).thenReturn(new BotMove(1, 1_000_000L));
        PersistentBotProcessFactory factory = mock(PersistentBotProcessFactory.class);
        when(factory.create()).thenReturn(process);
        EvaluationCoordinator coordinator = newCoordinator(factory);

        EvaluationResponse response = coordinator.evaluate(
                new EvaluationRequest("req-2", BOT_SOURCE, EvaluationMode.HIDDEN));

        assertTrue(response.isCompileSucceeded());
        assertEquals(24, response.getSummary().getGameCount());
        assertEquals(24, response.getMatches().size());
    }

    @Test
    void compileFailureReturnsCompileFailedResponse() {
        PersistentBotProcess process = mock(PersistentBotProcess.class);
        doThrow(new SandboxExecutionException(SandboxErrorCode.COMPILE_FAILED, "syntax error"))
                .when(process).start(anyString());
        PersistentBotProcessFactory factory = mock(PersistentBotProcessFactory.class);
        when(factory.create()).thenReturn(process);
        EvaluationCoordinator coordinator = newCoordinator(factory);

        EvaluationResponse response = coordinator.evaluate(
                new EvaluationRequest("req-3", "bad source", EvaluationMode.COMPILE_ONLY));

        assertFalse(response.isCompileSucceeded());
        assertEquals("req-3", response.getRequestId());
        assertTrue(response.getMatches().isEmpty());
        verify(process).close();
    }

    @Test
    void p95UsesCeilIndex() {
        List<Long> nanos = Arrays.asList(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L);
        // n=10, ceil(10*0.95)-1 = ceil(9.5)-1 = 9 -> sorted[9] = 100
        assertEquals(100L, EvaluationCoordinator.p95Nanos(nanos));
    }

    @Test
    void scoreCountsDrawAsHalf() {
        // 2 wins, 1 draw, gameCount=4 -> (2 + 0.5) / 4 = 0.625
        assertEquals(0.625, EvaluationCoordinator.score(2, 1, 4), 1e-9);
    }
}
