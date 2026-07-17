package com.kob.backend.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kob.backend.agent.mapper.AgentTaskMapper;
import com.kob.backend.agent.model.AgentErrorCode;
import com.kob.backend.agent.model.AgentTask;
import com.kob.backend.agent.model.AgentTaskStatus;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Agent 任务仓库：乐观锁 CAS 推进状态，查询只返回本人任务与非终态任务。
 */
@Repository
public class AgentTaskRepository {

    private static final int MAX_MESSAGE = 1000;
    private final AgentTaskMapper mapper;

    public AgentTaskRepository(AgentTaskMapper mapper) {
        this.mapper = mapper;
    }

    public boolean transition(AgentTask task, AgentTaskStatus target,
                              Integer currentIteration, Long bestVersionId,
                              AgentErrorCode errorCode, String errorMessage,
                              boolean clearActiveSlot) {
        String errorCodeStr = errorCode == null ? null : errorCode.name();
        String message = truncate(errorMessage);
        int affected = mapper.compareAndSetStatus(
                task.getId(), task.getVersion(), task.getStatus(), target.name(),
                currentIteration, bestVersionId, errorCodeStr, message, clearActiveSlot);
        if (affected == 0) {
            return false;
        }
        task.setStatus(target.name());
        task.setVersion(task.getVersion() + 1);
        if (currentIteration != null) task.setCurrentIteration(currentIteration);
        if (bestVersionId != null) task.setBestVersionId(bestVersionId);
        if (errorCodeStr != null) task.setErrorCode(errorCodeStr);
        if (message != null) task.setErrorMessage(message);
        if (clearActiveSlot) task.setActiveSlot(null);
        return true;
    }

    public AgentTask findById(Long id) {
        return mapper.selectById(id);
    }

    public AgentTask findOwnedTask(Long taskId, Integer userId) {
        QueryWrapper<AgentTask> w = new QueryWrapper<>();
        w.eq("id", taskId).eq("user_id", userId);
        return mapper.selectOne(w);
    }

    public List<AgentTask> findIncompleteTasks() {
        QueryWrapper<AgentTask> w = new QueryWrapper<>();
        w.notIn("status", "COMPLETED", "FAILED", "CANCELLED");
        return mapper.selectList(w);
    }

    public List<AgentTask> findByUser(Integer userId) {
        QueryWrapper<AgentTask> w = new QueryWrapper<>();
        w.eq("user_id", userId).orderByDesc("created_at");
        return mapper.selectList(w);
    }

    public void insert(AgentTask task) {
        mapper.insert(task);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= MAX_MESSAGE ? s : s.substring(0, MAX_MESSAGE);
    }
}
