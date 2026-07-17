package com.kob.backend.agent.llm;

import com.kob.backend.agent.model.AgentTaskStatus;
import com.kob.backend.agent.tool.EvaluationAggregate;

import java.util.List;

/**
 * 提供给模型的上下文。只含公开集信息，禁止出现隐藏集字段、种子、Authorization 或其他用户数据。
 */
public final class LlmContext {
    private final Long taskId;
    private final AgentTaskStatus status;
    private final String goal;
    private final int iteration;
    private final int maxIterations;
    private final String currentSourceCode;
    private final String compileError;
    private final EvaluationAggregate publicEvaluation;
    private final List<String> failureSummaries;
    private final String previousChangeSummary;

    public LlmContext(Long taskId, AgentTaskStatus status, String goal, int iteration, int maxIterations,
                      String currentSourceCode, String compileError, EvaluationAggregate publicEvaluation,
                      List<String> failureSummaries, String previousChangeSummary) {
        this.taskId = taskId;
        this.status = status;
        this.goal = goal;
        this.iteration = iteration;
        this.maxIterations = maxIterations;
        this.currentSourceCode = currentSourceCode;
        this.compileError = compileError;
        this.publicEvaluation = publicEvaluation;
        this.failureSummaries = failureSummaries;
        this.previousChangeSummary = previousChangeSummary;
    }

    public Long getTaskId() { return taskId; }
    public AgentTaskStatus getStatus() { return status; }
    public String getGoal() { return goal; }
    public int getIteration() { return iteration; }
    public int getMaxIterations() { return maxIterations; }
    public String getCurrentSourceCode() { return currentSourceCode; }
    public String getCompileError() { return compileError; }
    public EvaluationAggregate getPublicEvaluation() { return publicEvaluation; }
    public List<String> getFailureSummaries() { return failureSummaries; }
    public String getPreviousChangeSummary() { return previousChangeSummary; }
}
