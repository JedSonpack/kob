package com.kob.backend.agent.workflow;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * Agent 任务有界执行器：固定线程池承载 runTask；同一任务重复 submit 返回现有 Future；
 * cancel 中断对应 Future。
 */
@Component
public class AgentWorkflowExecutor {

    private final AgentWorkflowService workflowService;
    private final ThreadPoolTaskExecutor executor;
    private final Map<Long, Future<?>> tasks = new ConcurrentHashMap<>();

    @Autowired
    public AgentWorkflowExecutor(@Lazy AgentWorkflowService workflowService,
                                 @Qualifier("agentTaskExecutor") ThreadPoolTaskExecutor executor) {
        this.workflowService = workflowService;
        this.executor = executor;
    }

    public Future<?> submit(Long taskId) {
        return tasks.computeIfAbsent(taskId, id -> executor.submit(() -> {
            try {
                workflowService.runTask(id);
            } finally {
                tasks.remove(id);
            }
        }));
    }

    public void cancel(Long taskId) {
        Future<?> future = tasks.remove(taskId);
        if (future != null) {
            future.cancel(true);
        }
    }
}
