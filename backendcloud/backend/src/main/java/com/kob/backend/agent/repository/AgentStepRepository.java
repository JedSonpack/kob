package com.kob.backend.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kob.backend.agent.mapper.AgentStepMapper;
import com.kob.backend.agent.model.AgentStep;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * Agent Step 仓库：幂等键查询、sequence 递增与状态更新。
 */
@Repository
public class AgentStepRepository {

    private final AgentStepMapper mapper;

    public AgentStepRepository(AgentStepMapper mapper) {
        this.mapper = mapper;
    }

    public AgentStep findByIdempotencyKey(String key) {
        QueryWrapper<AgentStep> w = new QueryWrapper<>();
        w.eq("idempotency_key", key);
        return mapper.selectOne(w);
    }

    public int nextSequence(Long taskId) {
        QueryWrapper<AgentStep> w = new QueryWrapper<>();
        w.eq("task_id", taskId).orderByDesc("sequence").last("LIMIT 1");
        AgentStep last = mapper.selectOne(w);
        return last == null ? 1 : last.getSequence() + 1;
    }

    public AgentStep insertRunning(Long taskId, String phase, String toolName,
                                   String idempotencyKey, String inputSummary) {
        AgentStep step = new AgentStep();
        step.setTaskId(taskId);
        step.setSequence(nextSequence(taskId));
        step.setPhase(phase);
        step.setToolName(toolName);
        step.setIdempotencyKey(idempotencyKey);
        step.setInputSummary(inputSummary);
        step.setStatus("RUNNING");
        step.setCreatedAt(new Date());
        mapper.insert(step);
        return step;
    }

    public void markSuccess(Long stepId, long durationMs, String outputSummary,
                            Integer promptTokens, Integer completionTokens) {
        AgentStep s = mapper.selectById(stepId);
        if (s == null) return;
        s.setStatus("SUCCESS");
        s.setDurationMs(durationMs);
        s.setOutputSummary(outputSummary);
        s.setPromptTokens(promptTokens);
        s.setCompletionTokens(completionTokens);
        mapper.updateById(s);
    }

    public void markFailed(Long stepId, long durationMs, String errorCode, String outputSummary) {
        AgentStep s = mapper.selectById(stepId);
        if (s == null) return;
        s.setStatus("FAILED");
        s.setDurationMs(durationMs);
        s.setErrorCode(errorCode);
        s.setOutputSummary(outputSummary);
        mapper.updateById(s);
    }

    public List<AgentStep> findByTask(Long taskId) {
        QueryWrapper<AgentStep> w = new QueryWrapper<>();
        w.eq("task_id", taskId).orderByAsc("sequence");
        return mapper.selectList(w);
    }
}
