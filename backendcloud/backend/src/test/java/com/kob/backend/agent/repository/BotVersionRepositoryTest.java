package com.kob.backend.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kob.backend.agent.mapper.BotVersionMapper;
import com.kob.backend.agent.mapper.EvaluationRunMapper;
import com.kob.backend.agent.model.BotVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotVersionRepositoryTest {

    private BotVersion version(int iteration, int attempt, Long parentId) {
        BotVersion v = new BotVersion();
        v.setTaskId(1L);
        v.setIteration(iteration);
        v.setAttempt(attempt);
        v.setParentVersionId(parentId);
        v.setSourceCode("package com.kob.test;");
        v.setStrategySummary("s");
        v.setCompileStatus("PENDING");
        return v;
    }

    @Test
    void rejectsInvalidIteration() {
        BotVersionRepository repo = new BotVersionRepository(
                mock(BotVersionMapper.class), mock(EvaluationRunMapper.class));
        assertThrows(IllegalArgumentException.class, () -> repo.save(version(0, 1, null)));
        assertThrows(IllegalArgumentException.class, () -> repo.save(version(4, 1, null)));
    }

    @Test
    void rejectsInvalidAttempt() {
        BotVersionRepository repo = new BotVersionRepository(
                mock(BotVersionMapper.class), mock(EvaluationRunMapper.class));
        assertThrows(IllegalArgumentException.class, () -> repo.save(version(1, 0, null)));
        assertThrows(IllegalArgumentException.class, () -> repo.save(version(1, 3, null)));
    }

    @Test
    void rejectsAttempt2WithoutAttempt1() {
        BotVersionMapper vm = mock(BotVersionMapper.class);
        when(vm.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        BotVersionRepository repo = new BotVersionRepository(vm, mock(EvaluationRunMapper.class));
        assertThrows(IllegalStateException.class, () -> repo.save(version(1, 2, null)));
    }

    @Test
    void rejectsIteration2WithoutCompletedPublicParent() {
        BotVersionMapper vm = mock(BotVersionMapper.class);
        EvaluationRunMapper em = mock(EvaluationRunMapper.class);
        when(em.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        BotVersionRepository repo = new BotVersionRepository(vm, em);
        assertThrows(IllegalStateException.class, () -> repo.save(version(2, 1, 99L)));
    }

    @Test
    void rejectsIteration2WithoutParent() {
        BotVersionRepository repo = new BotVersionRepository(
                mock(BotVersionMapper.class), mock(EvaluationRunMapper.class));
        assertThrows(IllegalStateException.class, () -> repo.save(version(2, 1, null)));
    }

    @Test
    void acceptsValidFirstVersion() {
        BotVersionMapper vm = mock(BotVersionMapper.class);
        BotVersionRepository repo = new BotVersionRepository(vm, mock(EvaluationRunMapper.class));
        BotVersion v = version(1, 1, null);
        assertDoesNotThrow(() -> repo.save(v));
        verify(vm).insert(v);
    }

    @Test
    void fillsMissingCreatedAtBeforeInsert() {
        BotVersionMapper vm = mock(BotVersionMapper.class);
        doAnswer(invocation -> {
            BotVersion inserted = invocation.getArgument(0);
            assertNotNull(inserted.getCreatedAt());
            return 1;
        }).when(vm).insert(any(BotVersion.class));
        BotVersionRepository repo = new BotVersionRepository(vm, mock(EvaluationRunMapper.class));

        repo.save(version(1, 1, null));
    }

    @Test
    void acceptsAttempt2WhenAttempt1Exists() {
        BotVersionMapper vm = mock(BotVersionMapper.class);
        when(vm.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        BotVersionRepository repo = new BotVersionRepository(vm, mock(EvaluationRunMapper.class));
        BotVersion v = version(1, 2, null);
        assertDoesNotThrow(() -> repo.save(v));
        verify(vm).insert(v);
    }
}
