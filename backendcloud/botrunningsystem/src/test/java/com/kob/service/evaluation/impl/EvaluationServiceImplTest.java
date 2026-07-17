package com.kob.service.evaluation.impl;

import com.kob.service.evaluation.EvaluationConflictException;
import com.kob.service.evaluation.EvaluationCoordinator;
import com.kob.service.evaluation.EvaluationJobRegistry;
import com.kob.service.evaluation.dto.EvaluationMatchResult;
import com.kob.service.evaluation.dto.EvaluationMode;
import com.kob.service.evaluation.dto.EvaluationRequest;
import com.kob.service.evaluation.dto.EvaluationResponse;
import com.kob.service.evaluation.dto.EvaluationSummary;
import com.kob.service.evaluation.sandbox.PersistentBotProcess;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvaluationServiceImplTest {

    private static final String SOURCE =
            "package com.kob.test;\npublic class Bot {\npublic Integer nextMove(String input) { return 0; }\n}";

    private EvaluationResponse sampleResponse() {
        EvaluationSummary summary = new EvaluationSummary(0, 0.0, 0.0, 0.0, 0L, 0, null);
        return new EvaluationResponse("req-1", true, null, summary, Collections.<EvaluationMatchResult>emptyList());
    }

    @Test
    void evaluateIsIdempotentAndCachesResponse() {
        EvaluationCoordinator coordinator = mock(EvaluationCoordinator.class);
        EvaluationResponse response = sampleResponse();
        when(coordinator.evaluate(any())).thenReturn(response);
        EvaluationJobRegistry registry = new EvaluationJobRegistry();
        EvaluationServiceImpl service = new EvaluationServiceImpl(coordinator, registry);

        EvaluationRequest request = new EvaluationRequest("req-1", SOURCE, EvaluationMode.PUBLIC);
        EvaluationResponse first = service.evaluate(request);
        EvaluationResponse second = service.evaluate(request);

        assertSame(first, second);
        verify(coordinator, times(1)).evaluate(request);
    }

    @Test
    void cancelRunningProcess() {
        EvaluationCoordinator coordinator = mock(EvaluationCoordinator.class);
        PersistentBotProcess process = mock(PersistentBotProcess.class);
        EvaluationJobRegistry registry = new EvaluationJobRegistry();
        EvaluationServiceImpl service = new EvaluationServiceImpl(coordinator, registry);
        registry.register("req-1", process);

        assertTrue(service.cancel("req-1"));
        verify(process).cancel();
        assertFalse(registry.contains("req-1"));
    }

    @Test
    void cancelUnknownReturnsFalse() {
        EvaluationCoordinator coordinator = mock(EvaluationCoordinator.class);
        EvaluationJobRegistry registry = new EvaluationJobRegistry();
        EvaluationServiceImpl service = new EvaluationServiceImpl(coordinator, registry);

        assertFalse(service.cancel("nope"));
        verify(coordinator, never()).evaluate(any());
    }

    @Test
    void runningRequestThrowsConflict() {
        EvaluationCoordinator coordinator = mock(EvaluationCoordinator.class);
        PersistentBotProcess process = mock(PersistentBotProcess.class);
        EvaluationJobRegistry registry = new EvaluationJobRegistry();
        EvaluationServiceImpl service = new EvaluationServiceImpl(coordinator, registry);
        registry.register("req-1", process);

        EvaluationRequest request = new EvaluationRequest("req-1", SOURCE, EvaluationMode.PUBLIC);
        assertThrows(EvaluationConflictException.class, () -> service.evaluate(request));
        verify(coordinator, never()).evaluate(any());
    }
}
