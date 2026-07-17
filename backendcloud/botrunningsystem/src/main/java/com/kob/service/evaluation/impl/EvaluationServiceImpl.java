package com.kob.service.evaluation.impl;

import com.kob.service.evaluation.EvaluationConflictException;
import com.kob.service.evaluation.EvaluationCoordinator;
import com.kob.service.evaluation.EvaluationJobRegistry;
import com.kob.service.evaluation.EvaluationService;
import com.kob.service.evaluation.dto.EvaluationRequest;
import com.kob.service.evaluation.dto.EvaluationResponse;
import com.kob.service.evaluation.sandbox.PersistentBotProcess;
import org.springframework.stereotype.Service;

/**
 * 评测服务实现：幂等缓存、防重与取消。
 *
 * <p>已完成 requestId 直接返回缓存；运行中 requestId 返回 409 冲突；
 * 协调器成功后保存响应；取消运行中进程并移除登记。
 */
@Service
public class EvaluationServiceImpl implements EvaluationService {

    private final EvaluationCoordinator coordinator;
    private final EvaluationJobRegistry registry;

    public EvaluationServiceImpl(EvaluationCoordinator coordinator, EvaluationJobRegistry registry) {
        this.coordinator = coordinator;
        this.registry = registry;
    }

    @Override
    public EvaluationResponse evaluate(EvaluationRequest request) {
        String requestId = request.getRequestId();
        EvaluationResponse cached = registry.getCompleted(requestId);
        if (cached != null) {
            return cached;
        }
        if (registry.isRunning(requestId)) {
            throw new EvaluationConflictException(requestId);
        }
        EvaluationResponse response = coordinator.evaluate(request);
        registry.markCompleted(requestId, response);
        return response;
    }

    @Override
    public boolean cancel(String requestId) {
        PersistentBotProcess process = registry.getRunning(requestId);
        if (process == null) {
            return false;
        }
        process.cancel();
        registry.removeRunning(requestId);
        return true;
    }
}
