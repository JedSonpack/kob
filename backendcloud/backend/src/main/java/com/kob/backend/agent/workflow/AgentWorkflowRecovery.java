package com.kob.backend.agent.workflow;

import com.kob.backend.agent.model.AgentTask;
import com.kob.backend.agent.repository.AgentTaskRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 应用启动恢复：扫描非终态任务并重新提交执行（设计规格 12）。
 */
@Component
public class AgentWorkflowRecovery {

    private final AgentTaskRepository taskRepository;
    private final AgentWorkflowExecutor executor;

    public AgentWorkflowRecovery(AgentTaskRepository taskRepository, AgentWorkflowExecutor executor) {
        this.taskRepository = taskRepository;
        this.executor = executor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        for (AgentTask task : taskRepository.findIncompleteTasks()) {
            executor.submit(task.getId());
        }
    }
}
